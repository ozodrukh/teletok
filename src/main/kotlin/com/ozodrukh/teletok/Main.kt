package com.ozodrukh.teletok

import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.source
import java.io.File
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

        val bot = VideoExtractorBot(props.getProperty("bot.token"))
        bot.start()

//        val toks = arrayOf(
//            "https://vt.tiktok.com/ZSFJDAGGL",
//            "https://vt.tiktok.com/ZSNcwY9rQ",
//            "https://vt.tiktok.com/ZSNopUEB4",
//            "https://vt.tiktok.com/ZSNEcfPos",
//            "https://vt.tiktok.com/ZSNEn7WXu"
//        )
//        File("a").source()
//
//        val job = SupervisorJob()
//        val scope = CoroutineScope(Dispatchers.IO + job)
//        toks.forEachIndexed { x, url ->
//            scope.launch {
//                println("Extracting $x")
//                val e: ExtractedInfo?
//                val t = measureTime {
//                    e = VideoExtractor(url.toHttpUrl()).extract()
//                }
//                if(e != null) {
//                    println("finished $x, ${t.inWholeSeconds} in secs, d=${e.duration}s, ${e.width}x${e.height}")
//                } else {
//                    println("finished $x, ${t.inWholeSeconds} in secs")
//                }
//            }
//        }
//
//        job.join()
    }
}