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

        val logMessageOwner = ChannelLogger.getLogger().getMessageOwner(message)

        if (videoUrl != null && videoUrl.host.contains("tiktok")) {

            ChannelLogger.getLogger()
                .logMessage(
                    logMessageOwner + markdown2()
                    .appendEscaped(" - extract request - ${message.text}")
                    .toString(),
                    ParseMode.MARKDOWN_V2
                )

            scope.launch {
                extractVideo(chatId, messageId, videoUrl)
            }
        } else {
            ChannelLogger.getLogger()
                .logMessage(
                    logMessageOwner + markdown2()
                        .appendEscaped(" - bad url")
                        .toString(),
                    ParseMode.MARKDOWN_V2
                )

            if (private) {
                bot.sendMessage(chatId, "Please send valid Tiktok url")
            }
        }
    }

    private suspend fun extractVideo(chatId: ChatId, userMessageId: Long, videoUrl: HttpUrl) {
        var stateMessageId: Long = -1
        bot.sendMessage(chatId, "📦 extracting video --> $videoUrl").fold(
            ifSuccess = {
                stateMessageId = it.messageId
                println("pending request - ${it.messageId}")
            },
            ifError = {
                ChannelLogger.getLogger()
                    .logMessage(markdown2()
                        .appendEscaped("Error sending message\n\n")
                        .appendFixedBlock(it.describeError())
                        .toString(), ParseMode.MARKDOWN_V2)

                println("Error: " + it.describeError())
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
            ChannelLogger.getLogger()
                .logMessage("Couldn't handle link - $videoUrl")

            println("Error couldn't extract $videoUrl")
        }
    }

    fun start() {
        when (val me = bot.getMe()) {
            is TelegramBotResult.Success -> {
                ChannelLogger.setLogger(ChannelLogger(bot))

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

            if (it.errorBody != null) {
                ChannelLogger.getLogger().logMessage(
                    markdown2()
                        .appendEscaped("Couldn't send message - ${videoInfo.originalUrl}")
                        .appendFixedBlock(it.errorBody?.string() ?: "unknown")
                        .toString()
                )

                println("Error: " + it.errorBody?.string())
            } else if(it.exception != null) {
                ChannelLogger.getLogger().logMessage(
                    markdown2()
                        .appendEscaped("Couldn't send message - ${videoInfo.originalUrl}")
                        .appendFixedBlock(it.exception?.message ?: "unknown")
                        .toString()
                )

                it.exception?.printStackTrace()
            }



        })
    }
}