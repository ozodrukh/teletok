package com.ozodrukh.teletok

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.channel
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.network.fold
import com.github.kotlintelegrambot.types.TelegramBotResult
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.tinylog.kotlin.Logger
import java.io.File
import java.util.concurrent.Executors

class VideoExtractorBot(private val botToken: String) {
    private val scope = CoroutineScope(
        Dispatchers.IO +
                CoroutineName("bot-worker") +
                SupervisorJob()
    )

    private val extractorDispatcher = Executors
        .newFixedThreadPool(2)
        .asCoroutineDispatcher()

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
                onMessage(channelPost, channelPost.text ?: return@channel)
            }
            text {
                onMessage(message, text)
            }
        }
    }

    private fun onMessage(message: Message, text: String) {
        val private = message.chat.type == "private"
        val chatId: ChatId = message.chatId
        val messageId: Long = if (private) -1 else message.messageId

        val videoUrl = text.toHttpUrlOrNull()

        if (videoUrl != null && videoUrl.host.contains("tiktok")) {
            if (message.from == null) {
                Logger.warn {
                    "proceeding request from cid=${message.chat.id} - (${message.chat.title}) -" +
                            " url=$videoUrl"
                }
            } else {
                message.from?.let {
                    Logger.warn {
                        "proceeding request from uid=${it.id} - (${it.firstName} ${it.lastName}) -" +
                                " url=$videoUrl"
                    }
                }
            }

            scope.launch {
                extractVideo(chatId, messageId, videoUrl)
            }
        } else {
            if (private) {
                bot.sendMessage(chatId, "Please send valid Tiktok url")

                if (message.from == null) {
                    Logger.warn {
                        "bad request from cid=${message.chat.id} - (${message.chat.title}) -" +
                                " url=$videoUrl"
                    }
                } else {
                    message.from?.let {
                        Logger.warn {
                            "bad request from uid=${it.id} - (${it.firstName} ${it.lastName}) -" +
                                    " url=$videoUrl"
                        }
                    }
                }
            }
        }
    }

    private suspend fun extractVideo(chatId: ChatId, userMessageId: Long, videoUrl: HttpUrl) {
        var stateMessageId: Long = -1
        bot.sendMessage(chatId, "👾 downloading video · $videoUrl", disableWebPagePreview = true).fold(
            ifSuccess = {
                stateMessageId = it.messageId
                Logger.debug { "Link accepted, pending request - ${it.messageId}" }
            },
            ifError = {
                Logger.error {
                    "Failed to send pending message" +
                            " chatId = ${chatId.id()}" +
                            " url = $videoUrl"
                }
            }
        )

        val extractor = VideoExtractor(videoUrl)
        val videoInfo = withContext(extractorDispatcher) {
            extractor.extract()
        }

        if (videoInfo != null) {
            val videoFile = File("tt_videos/${videoInfo.id}.${videoInfo.ext}")
            sendExtractedVideo(chatId, videoFile, videoInfo) { sentSuccesfully ->
                if (stateMessageId >= 0) {
                    bot.deleteMessage(chatId, stateMessageId)
                }

                if (userMessageId >= 0 && sentSuccesfully) {
                    bot.deleteMessage(chatId, userMessageId)
                }

                if (sentSuccesfully) {
                    // todo clear cache && memorize extracted video to telegram remote file
                    // so we don't need to download video again
                    videoFile.delete()
                }
            }
        } else {
            if (stateMessageId >= 0) {
                bot.editMessageText(
                    chatId, stateMessageId, null,
                    "I'm sorry, unsupported [media]($videoUrl) 🥲", ParseMode.MARKDOWN_V2
                )
            }

            Logger.debug { "Video extraction failed for $videoUrl" }
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
        videoFile: File,
        videoInfo: ExtractedInfo,
        messageSentCallaback: (Boolean) -> Unit
    ) {
        bot.sendVideo(
            chatId,
            TelegramFile.ByFile(videoFile),
            parseMode = ParseMode.MARKDOWN_V2,
            caption = videoInfo.asCaption(),
            duration = videoInfo.duration.toInt(),
            width = videoInfo.width,
            height = videoInfo.height,
        ).fold(response = {
            messageSentCallaback(it?.ok ?: false)
        }, error = {
            messageSentCallaback(false)

            it.logEvent(
                "Sending extracted message failed" +
                        " chat=${chatId.id()}," +
                        " url=${videoInfo.originalUrl}"
            )
        })
    }
}