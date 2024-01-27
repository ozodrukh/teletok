package com.ozodrukh.teletok.utils

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import com.ozodrukh.teletok.AppProperties
import com.ozodrukh.teletok.markdown2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.AbstractFormatPatternWriter
import org.tinylog.writers.Writer
import java.util.Map

class TelegramChannelWriter(properties: Map<String, String>) : Writer {
    private val layoutRenderer = LayoutRenderer(properties as kotlin.collections.Map<String, String>)

    private val writer = ITelegramChannelLogger()
    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> {
        val logEntryValues = layoutRenderer.requiredLogEntryValues
        logEntryValues.add(LogEntryValue.LEVEL)
        return logEntryValues
    }

    override fun write(logEntry: LogEntry) {
        writer.logMessage(
            text = markdown2()
                .appendFixedBlock(layoutRenderer.renderText(logEntry))
                .toString(),
            parseMode = ParseMode.MARKDOWN_V2
        )
    }

    override fun flush() = Unit
    override fun close() = Unit
}

internal class LayoutRenderer(properties: kotlin.collections.Map<String, String>):
    AbstractFormatPatternWriter(properties) {
    override fun write(logEntry: LogEntry) = Unit
    override fun flush() = Unit
    override fun close() = Unit

    fun renderText(logEntry: LogEntry): String {
        return render(logEntry)
    }
}

internal class ITelegramChannelLogger(
    private val telegramBot: Bot = Bot.Builder().also {
        it.token = AppProperties.getProperty("bot.token")
    }.build(),

    private val channelId: ChatId = ChatId.fromId(-1002007582141),
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun logMessage(text: String, parseMode: ParseMode? = null) {
        scope.launch {
            if (text.length > 4096) {
                var offset = 0
                val length = text.length

                while (length > offset) {
                    val chunk = text.substring(offset, offset + 4096)

                    telegramBot.sendMessage(
                        channelId,
                        chunk,
                        parseMode,
                        disableNotification = true,
                        disableWebPagePreview = true
                    )

                    offset += 4096
                }
            } else {
                telegramBot.sendMessage(
                    channelId,
                    text,
                    parseMode,
                    disableNotification = true,
                    disableWebPagePreview = true
                )
            }
        }
    }
}