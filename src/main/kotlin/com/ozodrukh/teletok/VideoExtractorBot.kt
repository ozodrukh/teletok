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
import com.ozodrukh.teletok.extractor.*
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.tinylog.kotlin.Logger
import java.util.concurrent.Executors

class VideoExtractorBot(
    private val botToken: String,
    extractorWorkers: Int) {
    private val scope = CoroutineScope(
        Dispatchers.IO +
                CoroutineName("bot-worker") +
                SupervisorJob()
    )

    private val extractorDispatcher = Executors
        .newFixedThreadPool(extractorWorkers)
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

        if (videoUrl != null) {
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
                bot.sendMessage(chatId, "Please send valid Tiktok or Instagram url")

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
        bot.sendMessage(chatId, "👾 downloading video  ·  $videoUrl", disableWebPagePreview = true).fold(
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

        val extractor = YtDlpVideoExtractor(videoUrl, SimpleCaptionConstructor())
        val result = withContext(extractorDispatcher) {
            extractor.extract()
        }

        result.onFailure {
            if (stateMessageId >= 0) {
                bot.editMessageText(
                    chatId, stateMessageId, null,
                    "I'm sorry, unsupported [media]($videoUrl) 🥲", ParseMode.MARKDOWN_V2
                )
            }

            Logger.debug { "Video extraction failed for $videoUrl" }
        }

        result.onSuccess {
            sendExtractedVideo(chatId, it) { sentSuccessfully ->
                if (stateMessageId >= 0) {
                    bot.deleteMessage(chatId, stateMessageId)
                }

                if (userMessageId >= 0 && sentSuccessfully) {
                    bot.deleteMessage(chatId, userMessageId)
                }

                if (sentSuccessfully) {
                    // todo clear cache && memorize extracted video to telegram remote file
                    // so we don't need to download video again
                    it.videoFile.delete()
                }
            }
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
        extractedVideoMetadata: ExtractedMetaVideo,
        messageSentCallback: (Boolean) -> Unit
    ) {
        bot.sendVideo(
            chatId,
            TelegramFile.ByFile(extractedVideoMetadata.videoFile),
            parseMode = ParseMode.MARKDOWN_V2,
            caption = extractedVideoMetadata.caption,
            duration = extractedVideoMetadata.metadata.duration,
            width = extractedVideoMetadata.metadata.width,
            height = extractedVideoMetadata.metadata.height,
        ).fold(response = {
            messageSentCallback(it?.ok ?: false)
        }, error = {
            messageSentCallback(false)

            it.logEvent(
                "Sending extracted message failed" +
                        " chat=${chatId.id()}," +
                        " url=${extractedVideoMetadata.sourceUrl}," +
                        " service=${extractedVideoMetadata.sourceService}"
            )
        })
    }
}