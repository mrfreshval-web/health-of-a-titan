package com.healthofatitan.app.domain.training

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Content arrives as a plain tree of maps, lists and scalars: in the APK from the JSON that
 * `contentCompile` writes (content/…/x.yaml → assets/content/…/x.json), in unit tests straight
 * from the YAML sources. One reader serves both, so the tests check exactly what ships.
 */
internal object Tree {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseJson(text: String): Map<String, Any?> {
        val root = toTree(json.parseToJsonElement(text))
        @Suppress("UNCHECKED_CAST")
        return root as? Map<String, Any?> ?: error("content root must be an object")
    }

    private fun toTree(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonObject -> e.mapValues { (_, v) -> toTree(v) }
        is JsonArray -> e.map(::toTree)
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            else -> e.doubleOrNull ?: e.content
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

internal fun Map<String, Any?>.list(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.objects(key: String): List<Map<String, Any?>> = list(key).mapNotNull { it as? Map<String, Any?> }

internal fun Map<String, Any?>.str(key: String): String? = this[key]?.let { if (it is String) it else it.toString() }

internal fun Map<String, Any?>.strings(key: String): List<String> = when (val v = this[key]) {
    is List<*> -> v.mapNotNull { it?.toString() }
    is String -> listOf(v)
    else -> emptyList()
}

internal fun Map<String, Any?>.int(key: String): Int? = when (val v = this[key]) {
    is Number -> v.toInt()
    is String -> v.trim().toIntOrNull()
    else -> null
}

internal fun Map<String, Any?>.bool(key: String): Boolean = this[key] == true

internal fun Map<String, Any?>.doubles(key: String): List<Double> = list(key).mapNotNull { (it as? Number)?.toDouble() }
