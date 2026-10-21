package com.eventroute.app.data.mock

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Utility for reading and deserializing bundled mock JSON files from assets.
 */
object AssetReader {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun readAsset(context: Context, path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    inline fun <reified T> readJsonAsset(context: Context, path: String): T {
        val content = readAsset(context, path)
        return json.decodeFromString(content)
    }
}
