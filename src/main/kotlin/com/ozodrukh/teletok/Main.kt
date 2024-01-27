package com.ozodrukh.teletok

import kotlinx.coroutines.runBlocking
import org.tinylog.kotlin.Logger
import java.io.FileReader
import java.util.*

val AppProperties = Properties().also {
    it.load(FileReader("privacy/app.properties"))
}

fun main(args: Array<String>) {
    runBlocking {
        Logger.info { "Extractor Bot Launched" }

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Logger.error(e)
        }

        val bot = VideoExtractorBot(AppProperties.getProperty("bot.token"))
        bot.start()
    }
}