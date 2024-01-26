package com.ozodrukh.teletok

import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.FileReader
import java.util.*
import kotlin.time.measureTime

fun main(args: Array<String>) {
    runBlocking {
//    with(VideoExtractor(url)) {
//        println(extract())
//        download()
//    }

        val props = Properties()
        props.load(FileReader("privacy/app.properties"))

//        val bot = VideoExtractorBot(props.getProperty("bot.token"))
//        bot.start()

        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        for (x in 0..5) {
            scope.launch {
                print("Extracting $x")
                val t = measureTime {
                    VideoExtractor("https://vt.tiktok.com/ZSFJDAGGL".toHttpUrl()).extract()
                }
                print("finished $x, ${t.inWholeSeconds} in secs")
            }
        }

        job.join()
    }
}