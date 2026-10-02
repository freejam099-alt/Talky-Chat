package com.deepwiki.a2uichat

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter


data class ChatParticipant(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val avatarType: String? = null,
    val avatarFace: String = "eyes",
    val avatarState: String = "default",
    val avatarSeed: Float = 0.5f,
    val avatarShading: String = "fabric",
    val avatarInteractive: Boolean = true,
    val avatarColor: String? = null,
)

data class ChatAttachment(
    val id: String,
    val name: String,
    val kind: String,
    val uri: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val alt: String? = null,
)

data class ChatReaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean = false,
)

enum class ChatMessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

data class ChatMessage(
    val id: String,
    val authorId: String,
    val text: String? = null,
    val createdAt: Instant,
    val attachments: List<ChatAttachment> = emptyList(),
    val reactions: List<ChatReaction> = emptyList(),
    val status: ChatMessageStatus? = null,
)

fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null

fun parseParticipants(value: Any?): List<ChatParticipant> {
    val array = value as? JSONArray ?: return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                ChatParticipant(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    avatar = o.stringOrNull("avatar"),
                    avatarType = o.stringOrNull("avatarType"),
                    avatarFace = o.optString("avatarFace", "eyes"),
                    avatarState = o.optString("avatarState", "default"),
                    avatarSeed = o.optDouble("avatarSeed", stableAvatarSeed(o.getString("id").hashCode())).toFloat().coerceIn(0f, 1f),
                    avatarShading = o.optString("avatarShading", "fabric"),
                    avatarInteractive = o.optBoolean("avatarInteractive", true),
                    avatarColor = o.stringOrNull("avatarColor"),
                )
            )
        }
    }
}

private fun stableAvatarSeed(value: Int): Double {
    val mixed = (value.toUInt() * 0x9E3779B9u + 0x85EBCA6Bu).toUInt()
    return mixed.toLong().and(0xFFFF_FFFFL).toDouble() / 4294967295.0
}

fun parseMessages(value: Any?): List<ChatMessage> {
    val array = value as? JSONArray ?: return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val reactions = mutableListOf<ChatReaction>()
            val reactionArray = o.optJSONArray("reactions")
            if (reactionArray != null) for (r in 0 until reactionArray.length()) {
                val item = reactionArray.getJSONObject(r)
                reactions += ChatReaction(item.getString("emoji"), item.optInt("count", 0), item.optBoolean("mine", false))
            }
            val attachments = mutableListOf<ChatAttachment>()
            val attachmentArray = o.optJSONArray("attachments")
            if (attachmentArray != null) for (a in 0 until attachmentArray.length()) {
                val item = attachmentArray.getJSONObject(a)
                attachments += ChatAttachment(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    kind = item.optString("kind", "file"),
                    uri = item.stringOrNull("url"),
                    size = if (item.has("size")) item.optLong("size") else null,
                    width = if (item.has("width")) item.optInt("width") else null,
                    height = if (item.has("height")) item.optInt("height") else null,
                    alt = item.stringOrNull("alt")
                )
            }
            val status = when (o.stringOrNull("status")) {
                "sending" -> ChatMessageStatus.SENDING
                "sent" -> ChatMessageStatus.SENT
                "delivered" -> ChatMessageStatus.DELIVERED
                "read" -> ChatMessageStatus.READ
                "failed" -> ChatMessageStatus.FAILED
                else -> null
            }
            add(ChatMessage(
                id = o.getString("id"),
                authorId = o.getString("authorId"),
                text = o.stringOrNull("text"),
                createdAt = parseInstant(o.get("createdAt")),
                attachments = attachments,
                reactions = reactions,
                status = status
            ))
        }
    }
}

fun parseInstant(value: Any?): Instant = when (value) {
    is Number -> Instant.ofEpochMilli(value.toLong())
    else -> {
        val raw = value?.toString() ?: error("createdAt is required")
        runCatching { Instant.parse(raw) }.getOrElse {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(raw, Instant::from)
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${kotlin.math.round(bytes / 1024.0).toInt()} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}
