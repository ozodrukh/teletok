package com.ozodrukh.teletok.extractor

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tinylog.kotlin.Logger
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO
import kotlin.coroutines.resume

class GalleryVideoGenerator(
    private val images: List<Image>,
    private val audioFile: File,
    private val cacheDir: File,
    private val outputName: String,
    private val outputExt: String,

    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) {

    private val imagesCacheDir = File(cacheDir, outputName)

    fun downloadResources() {
        if (imagesCacheDir.exists() && !imagesCacheDir.deleteRecursively()) {
            throw IllegalStateException("Couldn't delete $imagesCacheDir directory")
        }

        if (!imagesCacheDir.mkdirs()) {
            throw IllegalStateException("Couldn't create $imagesCacheDir directory")
        }

        Logger.debug { "Downloading slides(${images.size}) for $outputName" }

        images.mapIndexed { index, image ->
            val call = client.newCall(
                Request.Builder()
                    .get()
                    .url(image.url.toHttpUrl())
                    .build()
            )

            val response = call.execute()
            val outputFile = File(imagesCacheDir, "${outputName}_${index}.jpg")
            outputFile.createNewFile()
            // ffmpeg -framerate 0.5 -i "/Users/ozodrukh/Documents/Projects/TeleTokChannel/tt_videos/TikTok+TTGalery-7331461145640979744/TikTok+TTGalery-7331461145640979744_%d.jpg" -i "/Users/ozodrukh/Downloads/apk-patchable-by-downloadprovider/output/kinopoisk/resources/res/raw/calls_busy.mp3" -filter "scale=720:1280:force_original_aspect_ratio=decrease,pad=720:1280:(ow-iw)/2:(oh-ih)/2,setsar=1" -shortest output.mp4
            if (!response.isSuccessful) {
                val tmpImg = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
                tmpImg.graphics.apply {
                    color = Color.BLACK
                    fillRect(0, 0, image.width, image.height)
                }
                ImageIO.write(tmpImg, "jpg", outputFile)
            } else {
                response.body?.byteStream()?.copyTo(FileOutputStream(outputFile))
            }
        }
    }

    fun generateVideo() {
        val commands = arrayListOf<String>()
        commands.add("ffmpeg")

        val applyFilterSegments = StringBuilder()
        val concatSegments = StringBuilder()

        repeat(images.size) { id ->
            commands.add("-loop")
            commands.add("1")
            commands.add("-t")
            commands.add("3")
            commands.add("-i")
            commands.add("${imagesCacheDir.absolutePath}${File.separator}${outputName}_${id}.jpg")

            applyFilterSegments
                .append("[$id:v]scale=720:1280:force_original_aspect_ratio=decrease,pad=720:1280:(ow-iw)/2:(oh-ih)/2,setsar=1[v$id];")
            concatSegments
                .append("[v$id]")
        }

        commands.add("-stream_loop")
        commands.add("-1")
        commands.add("-i")
        commands.add(audioFile.absolutePath)
        commands.add("-filter_complex")
        commands.add("${applyFilterSegments}${concatSegments}concat=n=${images.size}:v=1:a=0,format=yuv420p[v]")
        commands.add("-map")
        commands.add("[v]")
        commands.add("-map")
        commands.add("${images.size}:a")
        commands.add("-shortest")
        commands.add(File(cacheDir,"$outputName.$outputExt").absolutePath)


        Logger.debug {
            commands.joinToString("\t")
        }

        runCommand(commands, verbose = true)
    }

    private fun runCommand(
        command: List<String>,
        verbose: Boolean = false,
    ): Process {
        val pb = ProcessBuilder(command).directory(null)

        if (verbose) {
            pb.redirectErrorStream(true);
            pb.redirectInput(ProcessBuilder.Redirect.PIPE); //optional, default behavior
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        }

        val p = pb.start()
        p.waitFor()
        return p
    }
}


data class Image(val url: String, val width: Int, val height: Int)