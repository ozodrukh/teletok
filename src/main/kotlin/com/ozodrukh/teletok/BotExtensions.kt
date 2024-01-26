package com.ozodrukh.teletok

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.ReplyMarkup
import com.github.kotlintelegrambot.types.TelegramBotResult


fun Bot.replyTo(
    message: Message,
    text: String,
    parseMode: ParseMode? = null,
    disableWebPagePreview: Boolean? = null,
    disableNotification: Boolean? = null,
    replyToMessageId: Long? = null,
    allowSendingWithoutReply: Boolean? = null,
    replyMarkup: ReplyMarkup? = null
): TelegramBotResult<Message> {
    return sendMessage(
        message.chatId,
        text,
        parseMode,
        disableWebPagePreview,
        disableNotification,
        replyToMessageId,
        allowSendingWithoutReply,
        replyMarkup
    )
}

val Message.chatId: ChatId
    get() = ChatId.fromId(chat.id)

fun TelegramBotResult.Error.describeError(): String {
    return when (this) {
        is TelegramBotResult.Error.HttpError ->
            "httpError($httpCode) - $description"

        is TelegramBotResult.Error.TelegramApi ->
            "apiError($errorCode) - $description"

        is TelegramBotResult.Error.InvalidResponse ->
            "invalid($httpCode) - ${this.body}"

        is TelegramBotResult.Error.Unknown -> "unknown(${exception.message})"
    }
}

private val markdownV2LinkEscapes = setOf(')', '\\')
private val markdownV2PreAndCodeEscapes = setOf('`', '\\')
private val markdownV2CommonEscapes = setOf(
    '_',
    '*',
    '[', ']',
    '(', ')',
    '~',
    '`',
    '>',
    '#',
    '+', '-', '=',
    '|',
    '{', '}',
    '.', '!'
)

private fun String.escapeMarkdownV2(escapeCharacters: Iterable<Char>): String = map {
    if (it in escapeCharacters) {
        "\\$it"
    } else {
        "$it"
    }
}.joinToString("")

fun String.escapeMarkdownV2Link() = escapeMarkdownV2(markdownV2LinkEscapes)
fun String.escapeMarkdownV2PreAndCode() = escapeMarkdownV2(markdownV2PreAndCodeEscapes)
fun String.escapeMarkdownV2Common() = escapeMarkdownV2(markdownV2CommonEscapes)

fun markdown2(): Markdown2Builder {
    return Markdown2Builder()
}

class Markdown2Builder() {
    private val builder = StringBuilder()

    fun appendEscaped(text: String): Markdown2Builder {
        builder.append(text.escapeMarkdownV2Common())
        return this
    }

    private fun appendWrapAndEscapeContent(tag: String, text: String): Markdown2Builder {
        builder.append(tag)
        appendEscaped(text)
        builder.append(tag)
        return this
    }

    fun appendBold(text: String): Markdown2Builder {
        return appendWrapAndEscapeContent("*", text)
    }

    fun appendItalic(text: String): Markdown2Builder {
        return appendWrapAndEscapeContent("_", text)
    }

    fun appendSpoiler(text: String): Markdown2Builder {
        return appendWrapAndEscapeContent("_", text)
    }

    override fun toString(): String {
        return builder.toString()
    }
}