package com.ozodrukh.teletok

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode

class ChannelLogger(
    private val bot: Bot,
    private val channelId: ChatId = ChatId.fromId(2007582141),
) {
    companion object {
        private lateinit var instance: ChannelLogger

        fun setLogger(logger: ChannelLogger) {
            synchronized(this) {
                instance = logger
            }
        }

        fun getLogger(): ChannelLogger {
            return instance
        }
    }

    fun getMessageOwner(message: Message): String {
        message.from?.let {
            return markdown2()
                .appendLink(
                    "${it.firstName} ${it.lastName}",
                    "tg://user?id=${it.id})"
                )
                .toString()
        }

        return markdown2()
            .appendLink(
                "${message.chat.title} - ${message.chat.type}",
                "tg://chat?id=${message.chat.id})"
            )
            .toString()
    }

    fun logMessage(text: String, parseMode: ParseMode? = null) {
        bot.sendMessage(channelId, text, parseMode, disableNotification = true)
    }
}