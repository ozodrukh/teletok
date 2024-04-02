package com.ozodrukh.teletok

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.ozodrukh.teletok.extractor.GalleryVideoGenerator
import com.ozodrukh.teletok.extractor.Image
import kotlinx.coroutines.runBlocking
import org.tinylog.kotlin.Logger
import java.io.File
import java.io.FileReader
import java.util.*

val AppProperties = Properties().also {
    it.load(FileReader("privacy/app.properties"))
}

fun main(args: Array<String>) {
    runBlocking {
        val botName = AppProperties.getProperty("bot.name", "Release Extractor Bot")
        Logger.info { "$botName Launched - v02.04.2024" }

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Logger.error(e)
        }

        val bot = VideoExtractorBot(
            botToken = AppProperties.getProperty("bot.token"),
            extractorWorkers = AppProperties.getProperty("extractor.workers")
                .toIntOrNull() ?: 2
        )
        bot.start()
    }
}

private suspend fun testGenerator() {
    val jsonParser = Gson()
    val output = File("/Users/ozodrukh/Documents/Projects/TeleTokChannel/tt_videos/TikTok+TTGalery-7331461145640979744.info.json")
        .readText()
    val rawMetadata = jsonParser.fromJson(output, JsonObject::class.java)

    val listOfImagesToken = object: TypeToken<List<Image>>() {}
    val images: List<Image> = jsonParser.fromJson(
        rawMetadata.getAsJsonArray("gallery_post"),
        listOfImagesToken
    )

    val id = rawMetadata.get("id").asString
    val fileExt = rawMetadata.get("ext").asString
    val extractor = rawMetadata.get("extractor").asString

    val generator = GalleryVideoGenerator(
        images = images,
        audioFile = File("/Users/ozodrukh/Downloads/test_video_gen/422976840_376897618372993_6830905500507794348_n.mp3"),
        cacheDir = File("/Users/ozodrukh/Documents/Projects/TeleTokChannel/tt_videos"),
        outputName = "$extractor-$id",
        outputExt = "mp4"
    )

    generator.generateVideo()
}