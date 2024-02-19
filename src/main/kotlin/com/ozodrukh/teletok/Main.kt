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
//        testGenerator()

        val botName = AppProperties.getProperty("bot.name", "Release Extractor Bot")
        Logger.info { "$botName Launched" }

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Logger.error(e)
        }

        val bot = VideoExtractorBot(AppProperties.getProperty("bot.token"))
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
        audioFile = File("/Users/ozodrukh/Documents/Projects/TeleTokChannel/tt_videos/#writing #poetry #fyp  [7331461145640979744].mp3 20-00-23-359.mp3"),
        cacheDir = File("/Users/ozodrukh/Documents/Projects/TeleTokChannel/tt_videos"),
        outputName = "$extractor-$id",
        outputExt = "mp4"
    )

    generator.generateVideo()
}