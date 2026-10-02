package com.deepwiki.a2uichat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val IncomingBubble = Color(0xFFF0F0F2)
private val SurfaceLight = Color.White
private val Border = Color(0xFFE1E1E6)
private val Muted = Color(0xFF777780)
private val Secondary = Color(0xFF505058)
private val Accent = Color(0xFF1565C0)
private val AccentText = Color.White
private val Danger = Color(0xFFC62828)

private data class AttachmentDraft(val id: String, val uri: Uri, val name: String, val kind: String)

private sealed interface ChatRow {
    data class Day(val key: String, val label: String) : ChatRow
    data class Message(val message: ChatMessage, val author: ChatParticipant, val mine: Boolean, val first: Boolean, val last: Boolean, val index: Int) : ChatRow
    data class Typing(val author: ChatParticipant) : ChatRow
}

private fun buildRows(messages: List<ChatMessage>, people: Map<String, ChatParticipant>, currentUserId: String, groupWindow: Long, locale: Locale): List<ChatRow> {
    val result = mutableListOf<ChatRow>()
    var lastDay = ""
    messages.forEachIndexed { index, message ->
        val date = Date(message.createdAt.toEpochMilli())
        val dayKey = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        if (dayKey != lastDay) {
            result += ChatRow.Day("day-$dayKey", dayLabelAndroid(date, locale))
            lastDay = dayKey
        }
        fun joins(other: ChatMessage?): Boolean {
            if (other == null || other.authorId != message.authorId) return false
            val otherDate = Date(other.createdAt.toEpochMilli())
            val otherDay = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(otherDate)
            return otherDay == dayKey && abs(other.createdAt.toEpochMilli() - message.createdAt.toEpochMilli()) <= groupWindow
        }
        result += ChatRow.Message(
            message = message,
            author = people[message.authorId] ?: ChatParticipant(message.authorId, "Unknown"),
            mine = message.authorId == currentUserId,
            first = !joins(messages.getOrNull(index - 1)),
            last = !joins(messages.getOrNull(index + 1)),
            index = index
        )
    }
    return result
}

private fun dayLabelAndroid(date: Date, locale: Locale): String {
    val cal = java.util.Calendar.getInstance(locale)
    val today = cal.clone() as java.util.Calendar
    today.set(java.util.Calendar.HOUR_OF_DAY, 0); today.set(java.util.Calendar.MINUTE, 0); today.set(java.util.Calendar.SECOND, 0); today.set(java.util.Calendar.MILLISECOND, 0)
    cal.time = date
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
    val days = ((today.timeInMillis - cal.timeInMillis) / 86_400_000L).toInt()
    return when {
        days == 0 -> "Today"
        days == 1 -> "Yesterday"
        days in 2..6 -> java.text.SimpleDateFormat("EEEE", locale).format(date)
        else -> java.text.SimpleDateFormat(if (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)) "EEE, MMM d" else "EEE, MMM d, yyyy", locale).format(date)
    }
}

private fun resolveValue(value: Any?, model: JSONObject): Any? {
    val o = value as? JSONObject ?: return value
    val path = o.optString("path", "")
    if (path.isEmpty()) return o.opt("literal")
    val tokens = path.removePrefix("/").split('/').filter(String::isNotEmpty).map { it.replace("~1", "/").replace("~0", "~") }
    var cursor: Any = model
    for (token in tokens) {
        cursor = when (cursor) {
            is JSONObject -> cursor.opt(token) ?: return null
            is JSONArray -> cursor.opt(token.toIntOrNull() ?: return null)
            else -> return null
        }
    }
    return cursor
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun A2UIChatThread(
    component: A2UIComponent,
    model: JSONObject,
    onAction: (name: String, context: Map<String, String>) -> Unit = { _, _ -> },
) {
    val props = component.props
    val participants = parseParticipants(resolveValue(props.opt("participants"), model))
    val currentUserId = (resolveValue(props.opt("currentUserId"), model) as? String) ?: "me"
    var messages by remember { mutableStateOf(parseMessages(resolveValue(props.opt("messages"), model))) }
    val groupWindow = (resolveValue(props.opt("groupWindow"), model) as? Number)?.toLong() ?: 300_000L
    val placeholder = (resolveValue(props.opt("placeholder"), model) as? String) ?: "Message"
    val localeTag = (resolveValue(props.opt("locale"), model) as? String) ?: "en-US"
    val locale = remember(localeTag) { Locale.forLanguageTag(localeTag) }
    val typing = parseStringList(resolveValue(props.opt("typing"), model))
    val reactions = parseStringList(resolveValue(props.opt("reactions"), model)).ifEmpty { listOf("👍", "❤️", "😂", "🎉", "👀", "🙏") }
    val readBy = parseStringMap(resolveValue(props.opt("readBy"), model))
    val allowAttachments = resolveValue(props.opt("allowAttachments"), model) as? Boolean ?: true
    val composer = resolveValue(props.opt("composer"), model) as? Boolean ?: true
    val voiceNote = resolveValue(props.opt("voiceNote"), model) as? Boolean ?: true
    val people = remember(participants) { participants.associateBy { it.id } }
    val rows = remember(messages, participants, currentUserId, groupWindow, locale) { buildRows(messages, people, currentUserId, groupWindow, locale) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rows.lastIndex.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    var seenCount by remember { mutableIntStateOf(messages.size) }
    var showJump by remember { mutableStateOf(false) }
    var pickerMessage by remember { mutableStateOf<String?>(null) }
    var composerText by remember { mutableStateOf("") }
    val pendingFiles = remember { mutableStateListOf<AttachmentDraft>() }
    var sentCounter by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.take(10).forEachIndexed { index, uri ->
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            pendingFiles += AttachmentDraft("pending-${System.currentTimeMillis()}-$index", uri, if (mime.startsWith("image/")) "Image" else "File", if (mime.startsWith("image/")) "image" else "file")
        }
    }

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 1 && (info.viewportEndOffset - (info.visibleItemsInfo.lastOrNull()?.offset ?: 0)) >= 0
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.layoutInfo.totalItemsCount }
            .collect { (first, _) ->
                val distance = listState.layoutInfo.totalItemsCount - (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first) - 1
                val near = distance <= 1
                if (near) {
                    showJump = false
                    seenCount = messages.size
                }
            }
    }

    LaunchedEffect(messages.size) {
        val ownLatest = messages.lastOrNull()?.authorId == currentUserId
        if (ownLatest) {
            listState.animateScrollToItem(rows.lastIndex.coerceAtLeast(0))
            seenCount = messages.size
            showJump = false
        } else if (!atBottom && messages.size > seenCount) {
            showJump = true
        } else if (atBottom) {
            listState.animateScrollToItem(rows.lastIndex.coerceAtLeast(0))
            seenCount = messages.size
        }
    }

    val addReaction: (String, String) -> Unit = { messageId, emoji ->
        messages = messages.map { message ->
            if (message.id != messageId) return@map message
            val current = message.reactions.toMutableList()
            val at = current.indexOfFirst { it.emoji == emoji }
            if (at == -1) current += ChatReaction(emoji, 1, true)
            else if (current[at].mine) current[at] = current[at].copy(count = current[at].count - 1, mine = false)
            else current[at] = current[at].copy(count = current[at].count + 1, mine = true)
            message.copy(reactions = current.filter { it.count > 0 })
        }
        pickerMessage = null
        onAction("react", mapOf("messageId" to messageId, "emoji" to emoji))
    }

    fun sendMessage() {
        val trimmed = composerText.trim()
        if (trimmed.isBlank() && pendingFiles.isEmpty()) return
        val attachments = pendingFiles.map {
            ChatAttachment(it.id, it.name, it.kind, it.uri.toString())
        }
        val id = "local-${System.currentTimeMillis()}"
        messages = messages + ChatMessage(id, currentUserId, trimmed.ifBlank { null }, java.time.Instant.now(), attachments, emptyList(), ChatMessageStatus.SENT)
        composerText = ""
        pendingFiles.clear()
        sentCounter++
        onAction("send", mapOf("messageId" to id, "text" to trimmed))
    }

    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f, fill = true)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(rows, key = { row -> when (row) { is ChatRow.Day -> row.key; is ChatRow.Message -> row.message.id; is ChatRow.Typing -> "typing" } }) { row ->
                        when (row) {
                            is ChatRow.Day -> DaySeparator(row.label)
                            is ChatRow.Message -> MessageRow(
                                row = row,
                                reactions = reactions,
                                pickerOpen = pickerMessage == row.message.id,
                                onPickerToggle = { pickerMessage = if (pickerMessage == row.message.id) null else row.message.id },
                                onPickReaction = { emoji -> addReaction(row.message.id, emoji) },
                                locale = locale,
                                readBy = readBy,
                                people = people,
                                currentUserId = currentUserId,
                                allMessages = messages,
                                onRetry = { onAction("retry", mapOf("messageId" to row.message.id)) },
                            )
                            is ChatRow.Typing -> TypingRow(row.author)
                        }
                    }
                    if (typing.any { it != currentUserId }) {
                        val author = people[typing.firstOrNull { it != currentUserId }] ?: ChatParticipant("typing", "Someone")
                        item(key = "typing") { TypingRow(author) }
                    }
                }

                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                AnimatedVisibility(
                    visible = showJump,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = .94f),
                    exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = .94f),
                ) {
                    val unread = (messages.size - seenCount).coerceAtLeast(0)
                    Surface(
                        onClick = {
                            scope.launch { listState.animateScrollToItem(rows.lastIndex.coerceAtLeast(0)); seenCount = messages.size; showJump = false }
                        },
                        shape = CircleShape,
                        tonalElevation = 3.dp,
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(modifier = Modifier.height(32.dp).padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(painterResource(R.drawable.ic_super_down), contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(if (unread > 0) "$unread new ${if (unread == 1) "message" else "messages"}" else "Jump to latest", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                } // close Box(weight)
            }

            if (composer) {
                Composer(
                    text = composerText,
                    onTextChange = { composerText = it },
                    placeholder = placeholder,
                    pendingFiles = pendingFiles,
                    onAttach = { if (allowAttachments) picker.launch("*/*") },
                    onRemove = { pendingFiles.removeAll { item -> item.id == it } },
                    onSend = ::sendMessage,
                    sentCounter = sentCounter,
                    allowAttachments = allowAttachments,
                    showVoiceNote = voiceNote,
                )
            }
        }
    }
}

private fun parseStringList(value: Any?): List<String> {
    val array = value as? JSONArray ?: return emptyList()
    return buildList { for (i in 0 until array.length()) add(array.optString(i)) }
}

private fun parseStringMap(value: Any?): Map<String, String> {
    val obj = value as? JSONObject ?: return emptyMap()
    return buildMap { obj.keys().forEach { put(it, obj.optString(it)) } }
}

@Composable
private fun DaySeparator(label: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.weight(1f).height(1.dp).background(Border))
        Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f).height(1.dp).background(Border))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    row: ChatRow.Message,
    reactions: List<String>,
    pickerOpen: Boolean,
    onPickerToggle: () -> Unit,
    onPickReaction: (String) -> Unit,
    locale: Locale,
    readBy: Map<String, String>,
    people: Map<String, ChatParticipant>,
    currentUserId: String,
    allMessages: List<ChatMessage>,
    onRetry: () -> Unit,
) {
    val message = row.message
    val dark = isSystemInDarkTheme()
    val incoming = if (dark) Color(0xFF25262B) else IncomingBubble
    val borderColor = if (dark) Color(0xFF34353B) else Border
    val composerSurface = if (dark) Color(0xFF17181C) else SurfaceLight
    val time = remember(locale, message.createdAt) { DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(message.createdAt.toEpochMilli())) }
    val readers = readBy.entries.mapNotNull { (personId, msgId) ->
        val who = people[personId] ?: return@mapNotNull null
        val read = allMessages.find { it.id == msgId } ?: return@mapNotNull null
        if (personId == currentUserId || read.authorId == personId || msgId != message.id) null else who
    }
    val readIndex = readBy.values.maxOfOrNull { id -> allMessages.indexOfFirst { it.id == id } } ?: -1
    val status = when {
        message.status == ChatMessageStatus.FAILED -> ChatMessageStatus.FAILED
        message.status == ChatMessageStatus.SENDING -> ChatMessageStatus.SENDING
        readIndex >= row.index -> ChatMessageStatus.READ
        else -> message.status ?: ChatMessageStatus.SENT
    }
    val bubbleShape = RoundedCornerShape(
        topStart = if (!row.mine && !row.first) 6.dp else 18.dp,
        topEnd = if (row.mine && !row.first) 6.dp else 18.dp,
        bottomStart = if (!row.mine && !row.last) 6.dp else 18.dp,
        bottomEnd = if (row.mine && !row.last) 6.dp else 18.dp,
    )

    Row(modifier = Modifier.fillMaxWidth().padding(top = if (row.first) 12.dp else 0.dp), horizontalArrangement = if (row.mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!row.mine) Spacer(Modifier.width(28.dp))
        Column(modifier = Modifier.widthIn(max = 360.dp), horizontalAlignment = if (row.mine) Alignment.End else Alignment.Start) {
            if (row.first) {
                Row(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    if (!row.mine) Text(row.author.name, color = Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(time, color = Muted, fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!row.mine && row.last) Avatar(row.author, 28.dp)
                Column(horizontalAlignment = if (row.mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                    val images = message.attachments.filter { it.kind == "image" && it.uri != null }
                    val files = message.attachments.filter { it.kind != "image" || it.uri == null }
                    if (images.isNotEmpty()) AttachmentGallery(images)
                    files.forEach { AttachmentFile(it) }
                    message.text?.let {
                        Text(
                            text = it,
                            modifier = Modifier.background(if (row.mine) MaterialTheme.colorScheme.primary else incoming, bubbleShape).padding(horizontal = 13.dp, vertical = 9.dp),
                            color = if (row.mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
                ReactionPicker(
                    open = pickerOpen,
                    mine = row.mine,
                    reactions = reactions,
                    onToggle = onPickerToggle,
                    onPick = onPickReaction,
                )
            }

            if (message.reactions.isNotEmpty()) {
                Row(modifier = Modifier.padding(horizontal = if (row.mine) 4.dp else 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    message.reactions.forEach { reaction ->
                        Surface(
                            onClick = { onPickReaction(reaction.emoji) },
                            shape = CircleShape,
                            color = if (reaction.mine) Accent.copy(alpha = .08f) else composerSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (reaction.mine) MaterialTheme.colorScheme.primary.copy(alpha = .28f) else borderColor),
                        ) {
                            Row(modifier = Modifier.height(26.dp).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(reaction.emoji, fontSize = 14.sp)
                                Text(reaction.count.toString(), fontSize = 12.sp, color = Secondary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            if ((row.mine && row.index == allMessages.indexOfLast { it.authorId == currentUserId }) || readers.isNotEmpty()) {
                Row(modifier = Modifier.padding(horizontal = 6.dp).padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (row.mine) {
                        when (status) {
                            ChatMessageStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Not delivered", color = Danger, fontSize = 12.sp)
                                Row(modifier = Modifier.clip(CircleShape).clickable(onClick = onRetry).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_super_retry), contentDescription = null, tint = Danger, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)); Text("Retry", color = Danger, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                            }
                            ChatMessageStatus.SENDING -> Text("Sending", color = Muted, fontSize = 12.sp)
                            ChatMessageStatus.READ -> Text("Read", color = Muted, fontSize = 12.sp)
                            ChatMessageStatus.DELIVERED -> Text("Delivered", color = Muted, fontSize = 12.sp)
                            else -> Text("Sent", color = Muted, fontSize = 12.sp)
                        }
                    }
                    if (readers.isNotEmpty()) Row { readers.forEachIndexed { i, person -> Box(Modifier.padding(start = if (i == 0) 0.dp else (-4).dp)) { Box(Modifier.border(2.dp, MaterialTheme.colorScheme.background, CircleShape)) { Avatar(person, 16.dp) } } } }
                }
            }
        }
    }
}

@Composable
private fun Avatar(person: ChatParticipant, size: androidx.compose.ui.unit.Dp) {
    if (!person.avatar.isNullOrBlank()) {
        val incoming = if (isSystemInDarkTheme()) Color(0xFF25262B) else IncomingBubble
        Box(modifier = Modifier.size(size).clip(CircleShape).background(incoming), contentAlignment = Alignment.Center) {
            RemoteImage(url = person.avatar, contentDescription = "${person.name} profile photo", modifier = Modifier.size(size), contentScale = ContentScale.Crop)
        }
    } else {
        val type = person.avatarType ?: defaultBotType(person.id)
        BotAvatar(
            type = type,
            size = size,
            face = person.avatarFace,
            state = person.avatarState,
            seed = person.avatarSeed,
            shading = person.avatarShading,
            interactive = person.avatarInteractive,
            color = person.avatarColor?.let { parseAvatarColor(it) },
            label = "${person.name} bot avatar",
        )
    }
}

private fun defaultBotType(id: String): String {
    val types = listOf("clover", "flower", "triangle", "square", "blob", "ghost", "circle", "drop", "star", "droid", "mech", "alien", "hexagon", "cat", "cloud", "pill", "pebble", "puddle")
    return types[(id.hashCode().ushr(1) % types.size)]
}

private fun parseAvatarColor(raw: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(raw))
}.getOrNull()

@Composable
private fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = url) {
        value = url?.let { loadBitmap(context, it) }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier = modifier.background(if (isSystemInDarkTheme()) Color(0xFF25262B) else IncomingBubble))
    }
}

private suspend fun loadBitmap(context: Context, rawUrl: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val source = when {
            rawUrl.startsWith("content://") -> context.contentResolver.openInputStream(android.net.Uri.parse(rawUrl))
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> java.net.URL(rawUrl).openStream()
            else -> null
        } ?: return@withContext null
        source.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

@Composable
private fun AttachmentGallery(images: List<ChatAttachment>) {
    val count = images.size.coerceAtMost(4)
    val width = 240.dp
    Column(modifier = Modifier.width(width).clip(RoundedCornerShape(16.dp))) {
        if (count == 1) {
            RemoteImage(url = images.first().uri, contentDescription = images.first().alt ?: images.first().name, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp), contentScale = ContentScale.Crop)
        } else {
            val rows = if (count <= 2) 1 else 2
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val start = if (count == 3 && r == 0) 0 else r * 2
                    val end = minOf(start + 2, count)
                    for (i in start until end) {
                        Box(modifier = Modifier.weight(1f).height(if (count == 3 && r == 0) 120.dp else 116.dp)) {
                            RemoteImage(url = images[i].uri, contentDescription = images[i].alt ?: images[i].name, modifier = Modifier.fillMaxWidth().height(116.dp), contentScale = ContentScale.Crop)
                            if (i == 3 && images.size > 4) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) { Text("+${images.size - 4}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentFile(file: ChatAttachment) {
    val dark = isSystemInDarkTheme()
    val composerSurface = if (dark) Color(0xFF17181C) else SurfaceLight
    val borderColor = if (dark) Color(0xFF34353B) else Border
    Row(modifier = Modifier.widthIn(max = 280.dp).border(1.dp, borderColor, RoundedCornerShape(16.dp)).background(composerSurface, RoundedCornerShape(16.dp)).padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(R.drawable.ic_super_document), contentDescription = null, modifier = Modifier.size(18.dp), tint = Secondary)
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            file.size?.let { Text(formatBytes(it), fontSize = 12.sp, color = Muted) }
        }
    }
}

@Composable
private fun TypingRow(author: ChatParticipant) {
    val incoming = if (isSystemInDarkTheme()) Color(0xFF25262B) else IncomingBubble
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.width(28.dp))
        Avatar(author, 28.dp)
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(incoming).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    Text("•", color = Muted.copy(alpha = if (index == 1) .8f else .5f), fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun ReactionPicker(open: Boolean, mine: Boolean, reactions: List<String>, onToggle: () -> Unit, onPick: (String) -> Unit) {
    Box {
        IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
            Icon(painterResource(R.drawable.ic_super_add), contentDescription = null, tint = if (open) MaterialTheme.colorScheme.onSurface else Muted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = onToggle, shape = CircleShape) {
            Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                reactions.forEach { emoji ->
                    DropdownMenuItem(text = { Text(emoji, fontSize = 19.sp) }, onClick = { onPick(emoji) }, modifier = Modifier.size(38.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp))
                }
            }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    pendingFiles: List<AttachmentDraft>,
    onAttach: () -> Unit,
    onRemove: (String) -> Unit,
    onSend: () -> Unit,
    sentCounter: Int,
    allowAttachments: Boolean,
    showVoiceNote: Boolean,
) {
    val dark = isSystemInDarkTheme()
    val composerSurface = if (dark) Color(0xFF17181C) else SurfaceLight
    val borderColor = if (dark) Color(0xFF34353B) else Border
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, borderColor.copy(alpha = .55f), RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        AnimatedVisibility(visible = pendingFiles.isNotEmpty(), enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll).padding(start = 4.dp, end = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pendingFiles.forEach { item ->
                    Box(modifier = Modifier.size(56.dp)) {
                        if (item.kind == "image") RemoteImage(url = item.uri.toString(), contentDescription = null, modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        else Row(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).border(1.dp, borderColor).background(composerSurface), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Icon(painterResource(R.drawable.ic_super_document), contentDescription = null, modifier = Modifier.size(18.dp), tint = Secondary) }
                        IconButton(onClick = { onRemove(item.id) }, modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape)) { Icon(painterResource(R.drawable.ic_super_close), contentDescription = "Remove ${item.name}", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(12.dp)) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            if (allowAttachments) IconButton(onClick = onAttach, modifier = Modifier.size(40.dp)) { Icon(painterResource(R.drawable.ic_super_attachment), contentDescription = "Attach files", tint = Secondary) }
            Row(modifier = Modifier.weight(1f).border(1.dp, borderColor, CircleShape).background(composerSurface, CircleShape).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.weight(1f).heightIn(min = 40.dp, max = 150.dp).padding(vertical = 8.dp)) {
                    if (text.isEmpty()) Text(placeholder, color = Muted, fontSize = 16.sp)
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp, max = 134.dp).onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) { onSend(); true } else false
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        minLines = 1,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        decorationBox = { inner -> Box { inner() } },
                    )
                }
                val ready = text.trim().isNotEmpty() || pendingFiles.isNotEmpty()
                Surface(onClick = onSend, enabled = ready, shape = CircleShape, color = if (ready) MaterialTheme.colorScheme.primary else Color.Transparent) {
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        AnimatedContent(targetState = sentCounter, label = "send-arrow", transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }) { _ ->
                            Icon(painterResource(R.drawable.ic_super_send), contentDescription = "Send", tint = if (ready) MaterialTheme.colorScheme.onPrimary else Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showVoiceNote && text.isBlank() && pendingFiles.isEmpty(),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            VoiceNote(
                bars = 28,
                gain = 60,
                bounce = 40,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
