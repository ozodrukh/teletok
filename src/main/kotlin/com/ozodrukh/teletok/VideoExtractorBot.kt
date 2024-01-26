package com.ozodrukh.teletok

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.channel
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.network.fold
import com.github.kotlintelegrambot.types.TelegramBotResult
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

class VideoExtractorBot(private val botToken: String) {
    private val scope = CoroutineScope(
        Dispatchers.IO +
                CoroutineName("extractor-worker") +
                SupervisorJob()
    )

    private var username: String? = null
    private val bot = bot {
        token = botToken
        dispatch {
            channel {
                channelPost.from?.let {
                    if (it.isBot && it.username == username) {
                        return@channel
                    }
                }

                onMessage(channelPost.chatId, channelPost.text ?: return@channel)
            }
            text {
                onMessage(message.chatId, text)
            }
        }
    }

    private fun onMessage(chatId: ChatId, text: String) {
        val videoUrl = text.toHttpUrlOrNull()

        println(videoUrl?.host)
        if (videoUrl != null && videoUrl.host.contains("tiktok")) {
            scope.launch {
                extractVideo(chatId, videoUrl)
            }
        } else {
            bot.sendMessage(chatId, "Please send valid url")
        }
    }

    private suspend fun extractVideo(chatId: ChatId, videoUrl: HttpUrl) {
        var messageId: Long = -1
        bot.sendMessage(chatId, "📦 extracting video --> $videoUrl").fold(
            ifSuccess = {
                messageId = it.messageId
                println("Sent: ${it.messageId}")
            },
            ifError = {
                println("Error: " + it.describeError())
            }
        )

        val extractor = VideoExtractor(videoUrl)
        val videoInfo = extractor.extract()

        if (videoInfo != null) {
            sendExtractedVideo(chatId, videoInfo) { sentSuccesfully ->
                if (messageId >= 0) {
                    bot.deleteMessage(chatId, messageId)
                }
            }
        } else {
            if (messageId >= 0) {
                bot.editMessageText(chatId, messageId, null,
                    "I'm sorry, unsupported [media]($videoUrl) 🥲", ParseMode.MARKDOWN_V2)
            }
            println("Error couldn't extract $videoUrl")
        }
    }

    fun start() {
        when (val me = bot.getMe()) {
            is TelegramBotResult.Success -> {
                username = me.value.username
                bot.startPolling()
            }

            is TelegramBotResult.Error ->
                throw RuntimeException("Error getMe() -> ${me.describeError()}")

            else -> Unit
        }
    }

    private fun sendExtractedVideo(
        chatId: ChatId,
        videoInfo: ExtractedInfo,
        messageSentCallaback: (Boolean) -> Unit
    ) {
        bot.sendVideo(
            chatId,
            TelegramFile.ByFile(
                File("tt_videos/${videoInfo.id}.${videoInfo.ext}")
            ),
            parseMode = ParseMode.MARKDOWN_V2,
            caption = videoInfo.asCaption(),
            duration = videoInfo.duration.toInt(),
            width = videoInfo.width,
            height = videoInfo.height,
        ).fold(response = {
            messageSentCallaback(it?.ok ?: false)
        }, error = {
            messageSentCallaback(false)
            println("Error: " + it.errorBody?.string())
        })
    }
}