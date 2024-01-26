package com.ozodrukh.teletok

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import kotlin.coroutines.resume

suspend fun runCommand(command: Array<String>): Process {
    return suspendCancellableCoroutine {
        val p = Runtime.getRuntime().exec(command)

        it.invokeOnCancellation {
            p.destroy()
        }

        p.waitFor()
        it.resume(p)
    }
}

fun ExtractedInfo.asCaption(): String {
    return markdown2()
        .appendBold("$creator - $title\n\n")
        .appendEscaped("$artist - $track🎧\n\n")
        .appendEscaped("${humanReadableCounter(viewsCount)}👀 - ${humanReadableCounter(likesCount)}❤️")
        .toString()

}

private fun humanReadableCounter(count: Long): String {
    var top = count
    var d = 0
    val t = arrayOf("", "K", "M", "B")
    while (top / 1000 > 0) {
        top /= 1000
        d++
    }
    d = d.coerceAtMost(t.size)
    return "$top${t[d]}"
}

class VideoExtractor(val url: HttpUrl) {
    private val jsonParser = Gson()

    suspend fun extract(): ExtractedInfo? {
        val p = runCommand(arrayOf("yt-dlp", url.toString(), "-j", "--no-simulate", "-o", "tt_videos/%(id)s.%(ext)s"))
        val exitCode = p.exitValue()
        if (exitCode == 0) {
            val output = p.inputStream.bufferedReader().readText()
            return jsonParser.fromJson(output, ExtractedInfo::class.java)
        }

        return null
    }

    suspend fun download() {
        val p = runCommand(arrayOf("yt-dlp", url.toString(), "-o", "tt_videos/%(id)s.%(ext)s"))

        if (p.exitValue() != 0) {
            val error = p.errorStream.bufferedReader().readText()
            throw RuntimeException(error)
        }
    }
}

data class ExtractedInfo(
    val id: Long,
    val title: String,
    val creator: String,
    @SerializedName("uploader")
    val creatorHandle: String,
    @SerializedName("uploader_url")
    val ownerLink: String,

    val artist: String,
    val track: String,

    @SerializedName("view_count")
    val viewsCount: Long,
    @SerializedName("like_count")
    val likesCount: Long,

    val width: Int,
    val height: Int,
    val ext: String,
    val duration: Long, // in seconds
)