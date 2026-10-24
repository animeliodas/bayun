package io.github.animeliodas.bayun.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Settings(
    val readingLang: String = "",          // пусто — язык телефона
    val theme: String = "system",          // system | light | sepia | dark
    val accent: String = "amber",          // цвет интерфейса: amber | purple | blue | pink | green | dynamic (из обоев)
    val fontSize: Float = 18f,
    val ttsSpeed: Float = 1f,
    val ttsPitch: Float = 1f,
    val ttsEngine: String = "",            // пусто — движок по умолчанию в системе
    val provider: String = "openrouter",
    val strongModel: String = "",
    val fastModel: String = "",
    val profile: String = "economy",
    val baseUrl: String = "",              // свой OpenAI-совместимый сервер (Ollama на ПК и т.п.)
    val autoMeta: Boolean = true,          // новая книга: сразу перевести название, автора и оглавление
    val autoChapters: Int = 0,             // новая книга: сразу перевести первые N глав (0 — нет)
    val defaultAhead: Int = 0,             // режим новых книг: 0 — вручную, 1 — открытая глава, N — на N глав вперёд
) {
    val effectiveReadingLang: String
        get() = readingLang.ifBlank { Locale.getDefault().language.ifBlank { "en" } }
}

data class Position(val chapter: Int, val paragraph: Int, val mode: TextMode?)

/** Закладка: место (глава, абзац) и начало текста — чтобы в списке было видно, что там. */
data class Bookmark(val chapter: Int, val paragraph: Int, val text: String, val chapterTitle: String, val time: Long)

/** Настройки, позиции чтения и ключи провайдеров (ключи шифруются ключом из Android Keystore). */
object Prefs {
    private lateinit var sp: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    fun init(context: Context) {
        sp = context.getSharedPreferences("bayun", Context.MODE_PRIVATE)
        _settings.value = Settings(
            readingLang = sp.getString("readingLang", "")!!,
            theme = sp.getString("theme", "system")!!,
            accent = sp.getString("accent", "amber")!!,
            fontSize = sp.getFloat("fontSize", 18f),
            ttsSpeed = sp.getFloat("ttsSpeed", 1f),
            ttsPitch = sp.getFloat("ttsPitch", 1f),
            ttsEngine = sp.getString("ttsEngine", "")!!,
            provider = sp.getString("provider", "openrouter")!!,
            strongModel = sp.getString("strongModel", "")!!,
            fastModel = sp.getString("fastModel", "")!!,
            profile = sp.getString("profile", "economy")!!,
            baseUrl = sp.getString("baseUrl", "")!!,
            autoMeta = sp.getBoolean("autoMeta", true),
            autoChapters = sp.getInt("autoChapters", 0),
            defaultAhead = sp.getInt("defaultAhead", 0),
        )
    }

    fun update(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        _settings.value = s
        sp.edit()
            .putString("readingLang", s.readingLang).putString("theme", s.theme).putString("accent", s.accent).putFloat("fontSize", s.fontSize)
            .putFloat("ttsSpeed", s.ttsSpeed).putFloat("ttsPitch", s.ttsPitch).putString("ttsEngine", s.ttsEngine).putString("provider", s.provider)
            .putString("strongModel", s.strongModel).putString("fastModel", s.fastModel)
            .putString("profile", s.profile).putString("baseUrl", s.baseUrl)
            .putBoolean("autoMeta", s.autoMeta).putInt("autoChapters", s.autoChapters).putInt("defaultAhead", s.defaultAhead)
            .apply()
    }

    // ---------- позиции чтения ----------
    fun position(book: String): Position? {
        val raw = sp.getString("pos:$book", null) ?: return null
        val p = raw.split("|")
        return Position(p.getOrNull(0)?.toIntOrNull() ?: 1, p.getOrNull(1)?.toIntOrNull() ?: 0,
            p.getOrNull(2)?.let { runCatching { TextMode.valueOf(it) }.getOrNull() })
    }

    fun savePosition(book: String, pos: Position) {
        sp.edit().putString("pos:$book", "${pos.chapter}|${pos.paragraph}|${pos.mode?.name ?: ""}").apply()
    }

    fun forget(book: String) {
        sp.edit().remove("pos:$book").remove("bm:$book").remove("ahead:$book").remove("resume:$book")
            .remove("night:$book").remove("resumecfg:$book").apply()
    }

    // ---------- разовые подсказки ----------
    fun flag(name: String): Boolean = sp.getBoolean("flag:$name", false)

    fun setFlag(name: String) {
        sp.edit().putBoolean("flag:$name", true).apply()
    }

    // ---------- закладки ----------
    fun bookmarks(book: String): List<Bookmark> {
        val arr = runCatching { JSONArray(sp.getString("bm:$book", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
            Bookmark(it.optInt("ch"), it.optInt("p"), it.optString("text"), it.optString("title"), it.optLong("t"))
        }.sortedWith(compareBy({ it.chapter }, { it.paragraph }))
    }

    fun addBookmark(book: String, b: Bookmark) = saveBookmarks(book,
        bookmarks(book).filterNot { it.chapter == b.chapter && it.paragraph == b.paragraph } + b)

    fun removeBookmark(book: String, b: Bookmark) = saveBookmarks(book,
        bookmarks(book).filterNot { it.chapter == b.chapter && it.paragraph == b.paragraph })

    private fun saveBookmarks(book: String, list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("ch", it.chapter).put("p", it.paragraph).put("text", it.text)
                .put("title", it.chapterTitle).put("t", it.time))
        }
        sp.edit().putString("bm:$book", arr.toString()).apply()
    }

    // ---------- перевод: опережение чтения, продолжение после перерыва ----------
    /** Режим перевода книги: 0 — вручную, 1 — открытая глава, N — держать N глав впереди. */
    fun readAhead(book: String): Int = sp.getInt("ahead:$book", 0)

    fun setReadAhead(book: String, chapters: Int) {
        sp.edit().putInt("ahead:$book", chapters).apply()
    }

    /** Ночной перевод: минута суток, с которой каждую ночь продолжать перевод книги (null — выключен). */
    fun nightTime(book: String): Int? = sp.getInt("night:$book", -1).takeIf { it >= 0 }

    fun setNightTime(book: String, minute: Int?) {
        if (minute == null) sp.edit().remove("night:$book").apply() else sp.edit().putInt("night:$book", minute).apply()
    }

    fun nightBooks(): Map<String, Int> = sp.all.filterKeys { it.startsWith("night:") }
        .mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix("night:") to it } }.toMap()

    /**
     * Продолжать ли прерванный перевод книги без пользователя. Только если провайдер, модели и адрес сервера те же,
     * что при запуске: сменили провайдера или ключ — тратить чужой баланс молча нельзя, пусть продолжат сами.
     */
    fun autoResume(book: String): Boolean =
        sp.getBoolean("resume:$book", false) && sp.getString("resumecfg:$book", null) == translationSignature()

    fun setAutoResume(book: String, on: Boolean) {
        sp.edit().putBoolean("resume:$book", on).putString("resumecfg:$book", translationSignature()).apply()
    }

    private fun translationSignature(): String =
        _settings.value.let { "${it.provider}|${it.strongModel}|${it.fastModel}|${it.baseUrl}" }

    // ---------- голоса: свой для каждого языка и движка ----------
    fun voice(engine: String, lang: String): String? = sp.getString("voice:$engine:$lang", null)

    fun setVoice(engine: String, lang: String, name: String?) {
        if (name.isNullOrBlank()) sp.edit().remove("voice:$engine:$lang").apply()
        else sp.edit().putString("voice:$engine:$lang", name).apply()
    }

    // ---------- ключи провайдеров ----------
    private const val KEY_ALIAS = "bayun_secrets"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return gen.generateKey()
    }

    fun setApiKey(provider: String, key: String) {
        if (key.isBlank()) {
            sp.edit().remove("key:$provider").apply()
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        val blob = c.iv + c.doFinal(key.toByteArray())
        sp.edit().putString("key:$provider", Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun apiKey(provider: String): String {
        val raw = sp.getString("key:$provider", null) ?: return ""
        return runCatching {
            val blob = Base64.decode(raw, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, blob, 0, 12))
            String(c.doFinal(blob, 12, blob.size - 12))
        }.getOrDefault("")
    }

    /** Конфиг Polytome из настроек: ключ передаётся только в память движка. */
    fun polytomeConfig(): String {
        val s = _settings.value
        val prov = JSONObject().put("api_key", apiKey(s.provider))
        if (s.baseUrl.isNotBlank()) prov.put("base_url", s.baseUrl.trim())
        val cfg = JSONObject()
            .put("profile", s.profile)
            .put("target_lang", s.effectiveReadingLang)
            .put("parallel", 4)
            .put("providers", JSONObject().put(s.provider, prov))
        val strong = s.strongModel.trim()
        val fast = s.fastModel.trim().ifBlank { strong }
        if (strong.isNotBlank()) {
            fun spec(m: String) = "${s.provider}:$m"
            cfg.put("slots", JSONObject()
                .put("strong", spec(strong)).put("fast", spec(fast)).put("reviewer", spec(strong))
                .put("extractor", spec(fast)).put("agent", spec(strong)).put("local", spec(strong)))
        }
        return cfg.toString()
    }
}
