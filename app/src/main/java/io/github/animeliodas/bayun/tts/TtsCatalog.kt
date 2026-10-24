package io.github.animeliodas.bayun.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Движки синтеза речи и их голоса — для экрана настроек. */
object TtsCatalog {
    data class Engine(val name: String, val label: String)
    data class Voice(val name: String, val lang: String, val local: Boolean, val quality: Int)
    data class Catalog(val engines: List<Engine>, val defaultEngine: String, val voices: List<Voice>)

    /** Подключиться к движку (engine; пусто — системный по умолчанию), прочитать список и отключиться. */
    suspend fun load(context: Context, engine: String): Catalog? = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine { cont ->
            lateinit var tts: TextToSpeech
            tts = TextToSpeech(context.applicationContext, { status ->
                val result = if (status == TextToSpeech.SUCCESS) runCatching {
                    Catalog(
                        engines = tts.engines.map { Engine(it.name, it.label) },
                        defaultEngine = tts.defaultEngine,
                        voices = runCatching { tts.voices }.getOrNull().orEmpty()
                            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
                            .map { Voice(it.name, it.locale.language, !it.isNetworkConnectionRequired, it.quality) }
                            .sortedWith(compareBy({ it.lang }, { !it.local }, { -it.quality }, { it.name })),
                    )
                }.getOrNull() else null
                tts.shutdown()
                if (cont.isActive) cont.resume(result)
            }, engine.ifBlank { null })
            cont.invokeOnCancellation { runCatching { tts.shutdown() } }
        }
    }

    private val SAMPLES = mapOf(
        "ru" to "Это пример голоса для чтения книг.",
        "en" to "This is how the voice sounds when reading a book.",
        "zh" to "这是朗读书籍时的声音示例。",
        "ja" to "これは本を読み上げるときの声のサンプルです。",
        "ko" to "책을 읽어 줄 때의 목소리 예시입니다.",
        "uk" to "Це приклад голосу для читання книжок.",
        "es" to "Así suena la voz al leer un libro.",
        "fr" to "Voici la voix qui lira vos livres.",
        "de" to "So klingt die Stimme beim Vorlesen eines Buches.",
        "it" to "Ecco la voce che leggerà i tuoi libri.",
        "pt" to "Esta é a voz que vai ler os seus livros.",
    )

    private val GOOGLE_STYLE = Regex("""^[a-z]{2,3}-[a-z]{2,4}-x-([a-z0-9]+)-(local|network)$""", RegexOption.IGNORE_CASE)

    /** «ru-ru-x-dfc-local» → «DFC», «ru-RU-language» → основной голос; остальные имена — как есть. */
    fun shortName(name: String, mainLabel: String): String = when {
        name.endsWith("-language", ignoreCase = true) -> mainLabel
        else -> GOOGLE_STYLE.find(name)?.groupValues?.get(1)?.uppercase() ?: name
    }

    fun sample(lang: String): String = SAMPLES[lang] ?: SAMPLES.getValue("en")

    fun languageName(code: String): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }.ifBlank { code }
}
