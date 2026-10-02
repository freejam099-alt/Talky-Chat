package com.deepwiki.a2uichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deepwiki.a2uichat.components.A2UIReorderList
import com.deepwiki.a2uichat.components.ReorderPerson
import com.deepwiki.a2uichat.components.IconBar
import com.deepwiki.a2uichat.components.IconBarItem
import java.nio.charset.StandardCharsets

private val AppLight = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    background = Color(0xFFFAFAFC),
    surface = Color.White,
    onSurface = Color(0xFF17171B),
    surfaceVariant = Color(0xFFF0F0F2),
)

private val AppDark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF10233F),
    background = Color(0xFF101114),
    surface = Color(0xFF17181C),
    onSurface = Color(0xFFE9E9ED),
    surfaceVariant = Color(0xFF25262B),
)


private fun resolveRootValue(value: Any?, model: org.json.JSONObject): Any? {
    val obj = value as? org.json.JSONObject ?: return value
    val path = obj.optString("path", "")
    if (path.isEmpty()) return obj.opt("literal")
    var cursor: Any = model
    path.removePrefix("/").split('/').filter(String::isNotEmpty).forEach { token ->
        cursor = when (cursor) {
            is org.json.JSONObject -> cursor.opt(token.replace("~1", "/").replace("~0", "~")) ?: return null
            is org.json.JSONArray -> cursor.opt(token.toIntOrNull() ?: return null)
            else -> return null
        }
    }
    return cursor
}

private fun iconResource(name: String): Int = when (name.lowercase()) {
    "home", "home-01" -> R.drawable.ic_huge_home
    "search", "search-01" -> R.drawable.ic_huge_search
    "files", "folder", "folder-01" -> R.drawable.ic_huge_folder
    "saved", "bookmark", "bookmark-01" -> R.drawable.ic_huge_bookmark
    "you", "user", "user-02" -> R.drawable.ic_huge_user
    else -> R.drawable.ic_huge_home
}

private fun parseIconBarItems(value: Any?): List<IconBarItem> {
    val array = value as? org.json.JSONArray ?: return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            add(
                IconBarItem(
                    key = o.optString("key", "item-$i"),
                    label = o.optString("label", "Navigation"),
                    icon = iconResource(o.optString("icon", "home")),
                )
            )
        }
    }
}

private val nullPeople = emptyList<ReorderPerson>()

private fun parseReorderPeople(value: Any?): List<ReorderPerson> {
    val array = value as? org.json.JSONArray ?: return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            add(
                ReorderPerson(
                    id = o.optString("id", "person-$i"),
                    name = o.optString("name", "Unknown"),
                    here = o.optBoolean("here", false),
                    tint = runCatching { Color(android.graphics.Color.parseColor(o.optString("tint", "#7EA6D8"))) }.getOrDefault(Color(0xFF7EA6D8)),
                    avatarType = o.optString("avatarType", "circle"),
                    avatarFace = o.optString("avatarFace", "eyes"),
                    avatarState = o.optString("avatarState", "default"),
                    avatarSeed = o.optDouble("avatarSeed", 0.5).toFloat().coerceIn(0f, 1f),
                )
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val renderer = remember {
                A2UIRenderer().also { r ->
                    val jsonl = context.assets.open("chat_thread.a2ui.jsonl").use { it.readBytes().toString(StandardCharsets.UTF_8) }
                    r.processJsonl(jsonl)
                }
            }
            val surface = renderer.state("chat-thread-demo")
            MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) AppDark else AppLight) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    surface?.let { host ->
                        val root = host.components["root"]
                        when (root?.type) {
                            "ChatThread" -> {
                                A2UIChatThread(root, host.dataModel)
                                val nav = host.components["floating-nav"]
                                if (nav != null) {
                                    val items = parseIconBarItems(resolveRootValue(nav.props.opt("items"), host.dataModel))
                                    val axis = (resolveRootValue(nav.props.opt("axis"), host.dataModel) as? String) ?: "row"
                                    val glyph = (resolveRootValue(nav.props.opt("glyph"), host.dataModel) as? Number)?.toFloat()?.dp ?: 17.dp
                                    val dilate = (resolveRootValue(nav.props.opt("dilate"), host.dataModel) as? Number)?.toInt() ?: 100
                                    val bounce = (resolveRootValue(nav.props.opt("bounce"), host.dataModel) as? Number)?.toInt() ?: 50
                                    val speed = (resolveRootValue(nav.props.opt("speed"), host.dataModel) as? Number)?.toInt() ?: 50
                                    val hug = (resolveRootValue(nav.props.opt("hug"), host.dataModel) as? Number)?.toFloat()?.dp ?: 7.dp
                                    val corner = (resolveRootValue(nav.props.opt("corner"), host.dataModel) as? Number)?.toFloat()?.dp ?: 26.dp
                                    val slot = (resolveRootValue(nav.props.opt("slot"), host.dataModel) as? Number)?.toFloat()?.dp ?: 38.dp
                                    val gap = (resolveRootValue(nav.props.opt("gap"), host.dataModel) as? Number)?.toFloat()?.dp ?: 4.dp
                                    if (items.isNotEmpty()) {
                                        IconBar(
                                            items = items,
                                            glyph = glyph,
                                            axis = axis,
                                            dilate = dilate,
                                            bounce = bounce,
                                            speed = speed,
                                            hug = hug,
                                            corner = corner,
                                            slot = slot,
                                            gap = gap,
                                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 82.dp),
                                        )
                                    }
                                }
                            }
                            "ReorderList" -> {
                                val people = parseReorderPeople(resolveRootValue(root.props.opt("people"), host.dataModel))
                                val give = (resolveRootValue(root.props.opt("give"), host.dataModel) as? Number)?.toInt() ?: 50
                                val lean = (resolveRootValue(root.props.opt("lean"), host.dataModel) as? Number)?.toInt() ?: 18
                                val step = (resolveRootValue(root.props.opt("step"), host.dataModel) as? Number)?.toFloat()?.dp ?: 50.dp
                                val corner = (resolveRootValue(root.props.opt("corner"), host.dataModel) as? Number)?.toFloat()?.dp ?: 22.dp
                                A2UIReorderList(people = people.ifEmpty { nullPeople }, give = give, lean = lean, step = step, corner = corner)
                            }
                            "IconBar" -> {
                                val items = parseIconBarItems(resolveRootValue(root.props.opt("items"), host.dataModel))
                                val glyph = (resolveRootValue(root.props.opt("glyph"), host.dataModel) as? Number)?.toFloat()?.dp ?: 17.dp
                                IconBar(items = items, glyph = glyph, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
