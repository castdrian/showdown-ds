package com.showdown.ds

import org.json.JSONArray

sealed interface ShowdownSocketFrame {
    data object Open : ShowdownSocketFrame
    data class Messages(val values: List<String>) : ShowdownSocketFrame
    data class Closed(val code: Int?, val reason: String) : ShowdownSocketFrame
    data class Raw(val value: String) : ShowdownSocketFrame
}

object ShowdownSocketFrames {
    fun encode(message: String, sockJs: Boolean) = if (sockJs) JSONArray().put(message).toString() else message

    fun decode(frame: String): ShowdownSocketFrame = when {
        frame == "o" -> ShowdownSocketFrame.Open
        frame.startsWith("a") -> decodeMessages(frame)
        frame.startsWith("c") -> decodeClose(frame)
        else -> ShowdownSocketFrame.Raw(frame)
    }

    private fun decodeMessages(frame: String): ShowdownSocketFrame {
        val values = runCatching {
            val messages = JSONArray(frame.drop(1))
            buildList {
                for (index in 0 until messages.length()) add(messages.getString(index))
            }
        }.getOrNull() ?: return ShowdownSocketFrame.Raw(frame)
        return ShowdownSocketFrame.Messages(values)
    }

    private fun decodeClose(frame: String): ShowdownSocketFrame {
        val values = runCatching { JSONArray(frame.drop(1)) }.getOrNull() ?: return ShowdownSocketFrame.Raw(frame)
        return ShowdownSocketFrame.Closed(values.optInt(0).takeIf { it != 0 }, values.optString(1))
    }
}
