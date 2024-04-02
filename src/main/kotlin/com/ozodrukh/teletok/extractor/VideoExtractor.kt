package com.ozodrukh.teletok.extractor

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ozodrukh.teletok.AppProperties
import com.ozodrukh.teletok.markdown2
import com.ozodrukh.teletok.utils.humanReadableCounter
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import org.tinylog.kotlin.Logger
import java.io.File
import kotlin.coroutines.resume

abstract class VideoExtractor {
    abstract suspend fun extract(): Result<ExtractedMetaVideo>
}

class YtDlpVideoExtractor(
    protected val url: HttpUrl,
    protected val captionConstructor: CaptionConstructor,
    protected val cacheDir: File = File("tt_videos"),
) : VideoExtractor() {
    private val allowedExtractors = listOf(
        "TikTok", "vm.tiktok", "TikTokPhoto"
    )
    private val jsonParser = Gson()

    protected val filenameTemplate: String = "%(extractor)s-%(id)s.%(ext)s"
    private val verboseExtractorProgram = AppProperties.getProperty("extractor.verbose", "false")
        .toBoolean()

    override suspend fun extract(): Result<ExtractedMetaVideo> {
        //yt-dlp https://vt.tiktok.com/ZSFJDAGGL -j --no-simulate -o "tt_videos/%(id)s.%(ext)s"

        val destination = File(cacheDir, filenameTemplate)
        val executablePath = (AppProperties.get("extractor.yt_dlp_executable") as String)
            .split(" ")
            .toList()

        val command = arrayListOf<String>()
        command.addAll(executablePath)
        command.add(url.toString())
        command.add("-o")
        command.add(destination.path)
        // "-f", "mp4",
//            "--use-extractors", allowedExtractors.joinToString(","),
        command.add("--write-info-json")

        val verboseArgs = listOf(
            "-v",
            "--progress",
            "--no-quiet",
        )

        if (verboseExtractorProgram) {
            command.addAll(verboseArgs)
        }

        Logger.debug { "Executing yt-dlp program:\n${command.joinToString(" ")}" }

        val p = runCommand(command.toTypedArray(), verbose = true)

        return if (p.exitValue() == 0) {
            val retrieveFilenameCommand = arrayListOf<String>()
            retrieveFilenameCommand.addAll(executablePath)
            retrieveFilenameCommand.add(url.toString())
            retrieveFilenameCommand.add("-o")
            retrieveFilenameCommand.add(destination.path)
            retrieveFilenameCommand.add("--print")
            retrieveFilenameCommand.add("filename")

            val filename = runCommand(
                retrieveFilenameCommand.toTypedArray()
            )
                .inputStream
                .bufferedReader()
                .readText()


            val output = File(cacheDir, File(filename).nameWithoutExtension + ".info.json")
                .readText()
            val rawMetadata = jsonParser.fromJson(output, JsonObject::class.java)

            rawMetadata.addProperty("original_url", url.toString())

            val id = rawMetadata.get("id").asString
            var fileExt = rawMetadata.get("ext").asString
            val extractor = rawMetadata.get("extractor").asString
            val postMetadata = jsonParser.fromJson(rawMetadata, PostMetadata::class.java)

            val isGalleryPost = rawMetadata.has("gallery_post")
            // download images & build video from gallery

            if (isGalleryPost) {
                val listOfImagesToken = object : TypeToken<List<Image>>() {}
                val images: List<Image> = jsonParser.fromJson(
                    rawMetadata.getAsJsonArray("gallery_post"),
                    listOfImagesToken
                )
                val audioFile = File(cacheDir, "$extractor-$id.$fileExt")
                val outputFile = File(cacheDir, "$extractor-$id.mp4")

                if (!outputFile.exists()) {
                    val generator = GalleryVideoGenerator(
                        images = images,
                        audioFile = audioFile,
                        cacheDir = outputFile.parentFile,
                        outputName = outputFile.nameWithoutExtension,
                        outputExt = outputFile.extension
                    )

                    generator.downloadResources()
                    generator.generateVideo()
                }

                fileExt = outputFile.extension
                audioFile.delete()
            }

            Result.success(
                ExtractedMetaVideo(
                    videoFile = File(cacheDir, "$extractor-$id.$fileExt"),
                    caption = captionConstructor.buildCaption(jsonParser, rawMetadata),
                    metadata = postMetadata,
                    sourceUrl = url.toString(),
                    sourceService = extractor
                )
            )
        } else {
            val processFailReason: String = p.errorStream
                .bufferedReader()
                .readText()

            Logger.error { "Extraction failed due to:\n$processFailReason" }

            Result.failure(RuntimeException(processFailReason))
        }
    }

    private suspend fun runCommand(
        command: Array<String>,
        verbose: Boolean = false,
    ): Process {
        return suspendCancellableCoroutine {
            val pb = ProcessBuilder(*command).directory(null)

            if (verbose) {
                pb.inheritIO()
            }

            val p = pb.start()

            it.invokeOnCancellation {
                p.destroy()
            }

            p.waitFor()
            it.resume(p)
        }
    }
}


/**
 * Parser to extract required metadata to build caption
 * for message from raw json output yt-dlp
 */
abstract class CaptionConstructor {
    abstract fun buildCaption(gson: Gson, rawMetadata: JsonObject): String
}

class SimpleCaptionConstructor : CaptionConstructor() {

    override fun buildCaption(gson: Gson, rawMetadata: JsonObject): String {
        val postMetadata = gson.fromJson(rawMetadata, VideoPostMetadata::class.java)
        val originalUrl = rawMetadata.get("original_url").asString
        val extractor = rawMetadata.get("extractor").asString

        return postMetadata.run {
            val builder = markdown2()
            if (description.length > 120) {
                val description = if (description.length >= 1024)
                    description.substring(0, 500) + "…"
                else
                    description

                builder.appendBold(creator ?: uploader)
                builder.appendEscaped(" - $description\n\n")
            } else {
                builder.appendBold("$creator - $description\n\n")
            }

            if (!artist.isNullOrEmpty() && !track.isNullOrEmpty()) {
                builder.appendEscaped("🎧 $artist - $track\n\n")
            }

            if (viewsCount > 0) {
                builder.appendEscaped("${humanReadableCounter(viewsCount)}👀")
            }

            if (likesCount > 0) {
                if (viewsCount > 0) {
                    builder.appendEscaped(" - ")
                }
                builder.appendEscaped("${humanReadableCounter(likesCount)}❤\uFE0F")
            }

            if (commentsCount > 0) {
                if (likesCount > 0 || viewsCount > 0) {
                    builder.appendEscaped(" - ")
                }
                builder.appendEscaped("${humanReadableCounter(commentsCount)}💬")
            }

            if (likesCount > 0 || viewsCount > 0 || commentsCount > 0) {
                builder.appendEscaped("\n\n")
            }

            builder.appendLink(extractor, originalUrl)
            builder.toString()
        }
    }

}