package io.github.animeliodas.bayun.data

import org.json.JSONArray
import org.json.JSONObject

data class Book(
    val path: String,
    val title: String,
    val originalTitle: String,
    val author: String,
    val sourceLang: String,
    val targetLang: String,
    val chaptersTotal: Int,
    val chaptersTranslated: Int,
    val cover: String?,
    val jobState: String?,
    val metaTranslated: Boolean = true,  // название и автор уже на языке читателя
) {
    val needsTranslation: Boolean get() = sourceLang != targetLang

    companion object {
        fun from(o: JSONObject) = Book(
            path = o.optString("path"),
            title = o.optString("title"),
            originalTitle = o.optString("original_title"),
            author = o.optString("author"),
            sourceLang = o.optString("source_lang"),
            targetLang = o.optString("target_lang"),
            chaptersTotal = o.optInt("chapters_total"),
            chaptersTranslated = o.optInt("chapters_translated"),
            cover = o.optString("cover").takeIf { it.isNotBlank() && it != "null" },
            jobState = o.optJSONObject("job")?.optString("state"),
            metaTranslated = o.optBoolean("meta_translated", true),
        )
    }
}

data class ChapterInfo(val n: Int, val title: String, val translated: Boolean)

/** Текст главы: абзацы оригинала и перевода (перевод может отсутствовать или не совпадать по числу абзацев). */
data class ChapterText(val n: Int, val source: List<String>, val translation: List<String>?, val aligned: Boolean)

enum class TextMode { TRANSLATION, ORIGINAL }

data class Language(val code: String, val name: String, val native: String)

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }
