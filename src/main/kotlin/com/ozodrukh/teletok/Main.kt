package com.ozodrukh.teletok

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.channel
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.network.fold
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileReader
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue

fun main(args: Array<String>) {
    runBlocking {
        val url = "https://vt.tiktok.com/ZSFJDAGGL"
        println("extracting $url")
//    with(VideoExtractor(url)) {
//        println(extract())
//        download()
//    }

        val props = Properties()
        props.load(FileReader("privacy/app.properties"))

        val bot = VideoExtractorBot(props.getProperty("bot.token"))
        bot.start()
    }
}