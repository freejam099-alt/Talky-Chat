package com.deepwiki.a2uichat

import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small native A2UI v1.0 message processor for this app.
 * It accepts JSONL envelopes and turns the flat component list into a surface state.
 * The catalog is intentionally local/trusted: the agent can choose component data,
 * but cannot ship arbitrary Android code.
 */
class A2UIRenderer {
    private val surfaces = mutableStateMapOf<String, SurfaceState>()

    fun processJsonl(jsonl: String) {
        jsonl.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { process(JSONObject(it)) }
    }

    fun process(message: JSONObject) {
        when {
            message.has("createSurface") -> createSurface(message.getJSONObject("createSurface"))
            message.has("updateComponents") -> updateComponents(message.getJSONObject("updateComponents"))
            message.has("updateDataModel") -> updateDataModel(message.getJSONObject("updateDataModel"))
            message.has("deleteSurface") -> deleteSurface(message.getJSONObject("deleteSurface"))
        }
    }

    fun state(surfaceId: String): SurfaceState? = surfaces[surfaceId]

    private fun createSurface(payload: JSONObject) {
        val id = payload.getString("surfaceId")
        val components = if (payload.has("components")) parseComponents(payload.getJSONArray("components")) else emptyMap()
        val model = payload.optJSONObject("dataModel") ?: JSONObject()
        surfaces[id] = SurfaceState(
            id = id,
            catalogId = payload.optString("catalogId", "https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json"),
            components = components,
            dataModel = model
        )
    }

    private fun updateComponents(payload: JSONObject) {
        val id = payload.getString("surfaceId")
        val old = surfaces[id] ?: return
        val merged = old.components.toMutableMap()
        parseComponents(payload.getJSONArray("components")).forEach { (key, value) -> merged[key] = value }
        surfaces[id] = old.copy(components = merged)
    }

    private fun updateDataModel(payload: JSONObject) {
        val id = payload.getString("surfaceId")
        val old = surfaces[id] ?: return
        val path = payload.optString("path", "/")
        val next = JSONObject(old.dataModel.toString())
        val value = payload.opt("value")
        if (path == "/" || path.isBlank()) {
            surfaces[id] = old.copy(dataModel = value as? JSONObject ?: JSONObject())
            return
        }
        setJsonPointer(next, path, value)
        surfaces[id] = old.copy(dataModel = next)
    }

    private fun deleteSurface(payload: JSONObject) {
        surfaces.remove(payload.getString("surfaceId"))
    }

    private fun parseComponents(array: JSONArray): Map<String, A2UIComponent> {
        val result = linkedMapOf<String, A2UIComponent>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.getString("id")
            val type = item.getString("component")
            val props = JSONObject(item.toString()).apply {
                remove("id")
                remove("component")
            }
            result[id] = A2UIComponent(id, type, props)
        }
        return result
    }

    private fun setJsonPointer(root: JSONObject, rawPath: String, value: Any?) {
        val tokens = rawPath.removePrefix("/").split('/').filter { it.isNotEmpty() }.map { it.replace("~1", "/").replace("~0", "~") }
        if (tokens.isEmpty()) return
        var cursor = root
        tokens.dropLast(1).forEach { key ->
            val child = cursor.optJSONObject(key) ?: JSONObject().also { cursor.put(key, it) }
            cursor = child
        }
        cursor.put(tokens.last(), value)
    }
}

data class A2UIComponent(
    val id: String,
    val type: String,
    val props: JSONObject
)

data class SurfaceState(
    val id: String,
    val catalogId: String,
    val components: Map<String, A2UIComponent>,
    val dataModel: JSONObject
)
