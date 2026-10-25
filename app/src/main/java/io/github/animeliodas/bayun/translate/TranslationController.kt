package io.github.animeliodas.bayun.translate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.animeliodas.bayun.MainActivity
import io.github.animeliodas.bayun.R
import io.github.animeliodas.bayun.data.Book
import io.github.animeliodas.bayun.data.Library
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.engine.PolytomeEngine
import io.github.animeliodas.bayun.tts.ReadAloud
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Прогресс переводов по книгам — для карточек библиотеки, читалки и уведомления. */
object Translations {
    data class Progress(val done: Int = 0, val total: Int = 0, val running: Boolean = false,
                        val preparing: Boolean = false, val error: String? = null,
                        val pausedBySystem: Boolean = false)

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val progress: StateFlow<Map<String, Progress>> = _progress

    fun update(book: String, f: (Progress) -> Progress) {
        _progress.value = _progress.value + (book to f(_progress.value[book] ?: Progress()))
    }

    fun isRunning(book: String) = _progress.value[book]?.running == true
    val anyRunning: Boolean get() = _progress.value.values.any { it.running }

    /** Книги, у которых сейчас переводятся название и оглавление. */
    private val _meta = MutableStateFlow<Set<String>>(emptySet())
    val meta: StateFlow<Set<String>> = _meta

    fun setMeta(book: String, on: Boolean) {
        _meta.value = if (on) _meta.value + book else _meta.value - book
    }
}

/**
 * Перевод книг на уровне приложения: запуск, события Polytome, прогресс, опережение чтения, продолжение после
 * перерыва. Сам перевод идёт в Python-потоке Polytome; TranslationService только держит приложение живым
 * (foreground-сервис, уведомление, wake lock). Если система не дала поднять сервис (запуск из фона), перевод всё
 * равно идёт — процесс в этот момент держит чтение вслух.
 */
object TranslationController {
    const val CHANNEL = "translation"
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val titles = HashMap<String, String>()

    fun init(context: Context) {
        app = context.applicationContext
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.channel_translation), NotificationManager.IMPORTANCE_LOW))
        scope.launch { PolytomeEngine.events.collect { onEvent(it) } }
        // сеть вернулась (или Android снова её разрешил) — повторить сорвавшееся опережение
        app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { main.post(retryNow) }
                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) { if (!blocked) main.post(retryNow) }
            })
    }

    fun title(book: String) = titles[book] ?: File(book).name

    // ---------- запуск и остановка ----------
    /** chapters: null — все непереведённые; иначе список номеров в нужном порядке (например, с текущей главы). */
    fun start(book: String, chapters: List<Int>? = null, resume: Boolean = false) {
        if (Translations.isRunning(book)) return
        Translations.update(book) { Translations.Progress(running = true) }
        Prefs.setAutoResume(book, true)
        ensureService()
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    PolytomeEngine.configure(Prefs.polytomeConfig())
                    titles[book] = Library.book(book).title
                    val check = PolytomeEngine.callObject("check")
                    if (!check.optBoolean("ready")) return@runCatching app.getString(R.string.translation_not_configured)
                    // название и оглавление пишут те же файлы книги — дождаться, не переводить параллельно
                    while (book in Translations.meta.value) Thread.sleep(300)
                    if (Prefs.settings.value.autoMeta && !Library.book(book).metaTranslated) metaNow(book)
                    val p = JSONObject().put("book", book)
                    if (resume) {
                        val r = PolytomeEngine.callObject("resume", p)
                        if (r.optBoolean("nothing_to_resume")) return@runCatching ""  // всё уже переведено
                    } else {
                        if (chapters != null) p.put("chapters", JSONArray(chapters))
                        PolytomeEngine.call("translate", p)  // через call() — в фоне, прогресс приходит событиями
                    }
                    null
                }.getOrElse { it.message ?: it.toString() }
            }
            if (err != null) finish(book, err.ifBlank { null }, notify = err.isNotBlank())
        }
    }

    fun stop(book: String) {
        Prefs.setAutoResume(book, false)  // пользователь остановил сам — не продолжать без него
        retries.remove(book)
        scope.launch(Dispatchers.IO) { runCatching { PolytomeEngine.call("cancel", JSONObject().put("book", book)) } }
    }

    /** Android исчерпал лимит фоновой работы (6 ч в сутки на Android 15+): мягко остановить, продолжить позже. */
    fun pauseAllBySystem() {
        Translations.progress.value.filterValues { it.running }.keys.forEach { book ->
            Translations.update(book) { it.copy(pausedBySystem = true) }
            scope.launch(Dispatchers.IO) { runCatching { PolytomeEngine.call("cancel", JSONObject().put("book", book)) } }
        }
    }

    /**
     * Приложение открыто: продолжить переводы, прерванные не пользователем (сбой, перезагрузка, лимит Android,
     * процесс выгружен системой). Остановленные кнопкой «Остановить» не трогаются.
     */
    fun autoResume() {
        scope.launch {
            val books = withContext(Dispatchers.IO) { runCatching { Library.refresh(); Library.books.value }.getOrDefault(emptyList()) }
            books.filter { b -> Prefs.autoResume(b.path) && !Translations.isRunning(b.path) &&
                b.jobState in setOf("interrupted", "cancelled", "failed") }
                .forEach { start(it.path, resume = true) }
        }
    }

    // ---------- название, автор, оглавление ----------
    /** Перевести название, автора и заголовки глав (в фоне; без настроенного провайдера — тихо пропустить). */
    fun translateMeta(book: String, onDone: (String?) -> Unit = {}) {
        if (book in Translations.meta.value || Translations.isRunning(book)) return
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    PolytomeEngine.configure(Prefs.polytomeConfig())
                    if (!PolytomeEngine.callObject("check").optBoolean("ready")) {
                        return@runCatching app.getString(R.string.translation_not_configured)
                    }
                    metaNow(book)
                    null
                }.getOrElse { it.message ?: it.toString() }
            }
            onDone(err)
        }
    }

    /** В IO-потоке: название и оглавление сейчас, потом обновить библиотеку (новые названия на экране). */
    private fun metaNow(book: String) {
        Translations.setMeta(book, true)
        try {
            PolytomeEngine.callObject("translate_meta", JSONObject().put("book", book))
            titles.remove(book)
        } finally {
            Translations.setMeta(book, false)
        }
        scope.launch { runCatching { Library.refresh() } }
    }

    /** Новая книга: сразу название и оглавление и/или первые главы — как задано в настройках. */
    fun afterImport(b: Book) {
        if (!b.needsTranslation) return
        val s = Prefs.settings.value
        Prefs.setReadAhead(b.path, s.defaultAhead)  // режим перевода новой книги — из настроек
        if (s.autoChapters > 0) {
            scope.launch {
                val first = withContext(Dispatchers.IO) {
                    runCatching { Library.chapters(b.path).take(s.autoChapters).map { it.n } }.getOrDefault(emptyList())
                }
                if (first.isNotEmpty()) start(b.path, first)
            }
        } else if (s.autoMeta) translateMeta(b.path)
    }

    // ---------- вся книга, ночной перевод ----------
    /** Главы для перевода «всей книги» в порядке чтения: с текущей до конца, потом начало книги. */
    suspend fun chaptersFromCurrent(book: String): List<Int> = withContext(Dispatchers.IO) {
        val toc = Library.chapters(book).map { it.n }
        val current = Prefs.position(book)?.chapter ?: toc.firstOrNull() ?: 1
        val i = toc.indexOf(current).coerceAtLeast(0)
        toc.drop(i) + toc.take(i)
    }

    /** Будильник ночного перевода: продолжить прерванное или начать оставшееся; книга готова — снять расписание. */
    fun startNight(book: String) {
        if (Translations.isRunning(book)) return
        ensureService()  // пока процесс разбужен будильником — сразу удержать его foreground-сервисом
        scope.launch {
            val b = withContext(Dispatchers.IO) { runCatching { Library.refresh(); Library.book(book) }.getOrNull() }
                ?: return@launch
            when {
                b.chaptersTranslated >= b.chaptersTotal -> NightTranslation.cancel(book)
                b.jobState in setOf("interrupted", "cancelled", "failed") -> start(book, resume = true)
                else -> start(book, chaptersFromCurrent(book))
            }
        }
    }

    // ---------- опережение чтения ----------
    /** Держать впереди читателя N переведённых глав (настройка книги): дочитал главу — в очередь следующая. */
    fun readAhead(book: String, chapter: Int, onNothing: () -> Unit = {}) {
        val n = Prefs.readAhead(book)
        if (n <= 0 || Translations.isRunning(book)) return
        scope.launch {
            val todo = withContext(Dispatchers.IO) {
                runCatching {
                    val toc = Library.chapters(book)
                    val from = toc.indexOfFirst { it.n == chapter }.coerceAtLeast(0)
                    toc.drop(from).take(n).filter { !it.translated }.map { it.n }
                }.getOrDefault(emptyList())
            }
            if (todo.isNotEmpty()) {
                Log.d("Translation", "опережение: $book главы $todo")
                start(book, todo)
            } else onNothing()
        }
    }

    /** Где сейчас читатель: глава чтения вслух, если оно в этой книге, иначе сохранённая позиция. */
    private fun currentChapter(book: String): Int? =
        ReadAloud.state.value.takeIf { it.book == book && it.chapter > 0 }?.chapter ?: Prefs.position(book)?.chapter

    // ---------- повтор после сбоя ----------
    /**
     * Книги, у которых главы опережения не перевелись (обычно — пропала связь): номер попытки. Повтор — как только
     * сеть вернётся, и по таймеру: 1, 2, 4, 8, 16 минут; после шести неудач — до следующего действия читателя.
     */
    private val retries = HashMap<String, Int>()
    private val main = Handler(Looper.getMainLooper())
    private val retryNow = Runnable { retryFailed() }
    private const val RETRY_MAX = 6
    private const val RETRY_BASE_MS = 60_000L

    private fun scheduleRetry(book: String) {
        val n = (retries[book] ?: 0) + 1
        if (n > RETRY_MAX) {
            retries.remove(book)
            return
        }
        retries[book] = n
        main.removeCallbacks(retryNow)
        main.postDelayed(retryNow, RETRY_BASE_MS shl (n - 1).coerceAtMost(4))
    }

    private fun retryFailed() {
        for (book in retries.keys.toList()) {
            if (Translations.isRunning(book)) continue
            val chapter = currentChapter(book) ?: continue
            Log.d("Translation", "повтор опережения: $book с главы $chapter, попытка ${retries[book]}")
            readAhead(book, chapter, onNothing = { retries.remove(book) })  // всё уже переведено — повторять нечего
        }
    }

    // ---------- события Polytome ----------
    private fun onEvent(ev: JSONObject) {
        val book = ev.optString("book")
        // шаги движка — в logcat (adb logcat -s Polytome): по ним видно, где перевод застрял
        val kind = ev.optString("ev")
        if (kind != "batch_done") Log.d("Polytome", "$kind ${ev.optString("msg").ifBlank { ev.optString("error") }}".trim())
        if (book.isBlank() || !Translations.isRunning(book)) return
        when (kind) {
            "prepare_start" -> Translations.update(book) { it.copy(preparing = true) }
            "job_start" -> Translations.update(book) { it.copy(preparing = false, total = ev.optInt("total"), done = 0) }
            "chapter_done" -> {
                val n = ev.optInt("n")
                Library.invalidate(book, n)
                Translations.update(book) { it.copy(done = it.done + 1) }
                ReadAloud.onChapterTranslated(book, n)
            }
            "job_done" -> {
                val r = ev.optJSONObject("report") ?: JSONObject()
                val halted = r.optString("HALTED").takeIf { it.isNotBlank() }
                val cancelled = r.optString("cancelled").isNotBlank()
                val failed = r.optJSONArray("failed")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
                when {
                    // остановка пользователем или системой — не «готово»
                    cancelled && Translations.progress.value[book]?.pausedBySystem != true && !Prefs.autoResume(book) ->
                        finish(book, null, notify = false)
                    // главы не перевелись (провайдер не ответил, пропала связь) — не «готово», а ошибка и повтор
                    halted == null && !cancelled && failed.isNotEmpty() -> finish(book, app.getString(
                        R.string.translation_failed_retry, failed.joinToString(), r.optString("failed_error").take(200)),
                        notify = true, retry = true)
                    // остановлено на ошибках провайдера (сеть, перегрузка) — тоже повторить позже
                    else -> finish(book, halted?.let { app.getString(R.string.translation_error_retry, it.take(200).trimEnd('.', ' ')) },
                        notify = !cancelled || halted != null, retry = halted != null)
                }
            }
            "job_error" -> finish(book, app.getString(R.string.translation_error_retry, ev.optString("error").take(200).trimEnd('.', ' ')),
                notify = true, retry = true)
        }
    }

    private fun finish(book: String, error: String?, notify: Boolean, retry: Boolean = false) {
        val paused = Translations.progress.value[book]?.pausedBySystem == true
        Translations.update(book) { it.copy(running = false, preparing = false, error = error) }
        if (error == null && !paused) Prefs.setAutoResume(book, false)  // доделано
        if (retry) scheduleRetry(book) else if (error == null) retries.remove(book)
        // чтение вслух ждёт главу, которой не было в этом переводе (режим «открытая глава»/«с опережением»): сразу
        // перевести её, а не переключаться на оригинал
        val ra = ReadAloud.state.value
        val waitingHere = ra.book == book && ra.waitingTranslation && error == null && !paused && Prefs.readAhead(book) > 0
        if (!waitingHere) ReadAloud.onTranslationStopped(book)
        scope.launch {
            runCatching { Library.refresh() }
            Library.books.value.firstOrNull { it.path == book }?.let { b ->
                if (b.chaptersTranslated >= b.chaptersTotal && Prefs.nightTime(book) != null) NightTranslation.cancel(book)
            }
            if (notify || paused) post(book, when {
                paused -> app.getString(R.string.translation_paused_by_system)
                error != null -> error
                else -> app.getString(R.string.translation_done)
            })
            // читатель ушёл вперёд, пока шёл перевод (или чтение вслух ждёт главу) — догнать
            if (waitingHere) readAhead(book, ra.chapter, onNothing = { ReadAloud.onTranslationStopped(book) })
            else if (error == null && !paused) currentChapter(book)?.let { readAhead(book, it) }
        }
    }

    private fun post(book: String, text: String) {
        val open = PendingIntent.getActivity(app, 1, Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        app.getSystemService(NotificationManager::class.java).notify(book.hashCode(), NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_translate)
            .setContentTitle(title(book))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    private fun ensureService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, TranslationService::class.java))
        } catch (e: Exception) {
            // Android 12+: из фона foreground-сервис не запустить. Перевод всё равно идёт в процессе приложения.
            Log.w("Translation", "сервис перевода не запущен: $e")
        }
    }
}
