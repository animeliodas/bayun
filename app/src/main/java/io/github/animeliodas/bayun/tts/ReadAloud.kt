package io.github.animeliodas.bayun.tts

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.animeliodas.bayun.R
import io.github.animeliodas.bayun.data.Book
import io.github.animeliodas.bayun.data.ChapterInfo
import io.github.animeliodas.bayun.data.Library
import io.github.animeliodas.bayun.data.Position
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.data.TextMode
import io.github.animeliodas.bayun.translate.TranslationController
import io.github.animeliodas.bayun.translate.Translations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import android.media.AudioAttributes as SystemAudioAttributes
import androidx.media3.common.AudioAttributes as MediaAudioAttributes

/**
 * Чтение вслух через системный синтез речи (любой установленный движок: Google, RHVoice, Samsung…).
 *
 * Фразы синтезируются заранее в файлы (на ~[AHEAD] фраз вперёд) и играются собственным плеером ExoPlayer, а не
 * голосом движка напрямую. Так:
 *  - звук идёт из процесса Баюна — Android отдаёт ему кнопки гарнитуры и часов (иначе они уходили в музыку);
 *  - короткий провал интернета не слышен: онлайн-голос успевает синтезировать с запасом и не подменяется офлайн;
 *  - пауза и продолжение — с того же места фразы, скорость и тон меняются сразу, без пересинтеза.
 * Работает с выключенным экраном: PlaybackService держит процесс (foreground-сервис медиа), здесь — wake lock.
 */
object ReadAloud {
    data class State(
        val book: String? = null,
        val bookTitle: String = "",
        val cover: String? = null,
        val chapter: Int = 0,
        val chapterTitle: String = "",
        val mode: TextMode = TextMode.TRANSLATION,
        val paragraph: Int = -1,
        val start: Int = 0,
        val end: Int = 0,
        val playing: Boolean = false,
        val loading: Boolean = false,
        val error: String? = null,
        val sleepUntil: Long = 0L,
        val waitingTranslation: Boolean = false,  // глава ещё переводится — чтение продолжится, когда она будет готова
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val waitingForTts = ArrayList<() -> Unit>()
    private var currentBook: Book? = null
    private var chapters: List<ChapterInfo> = emptyList()
    private var chaptersStale = false  // глава переведена — у неё новый заголовок
    private var sentences: List<Sentence> = emptyList()
    private var index = 0
    private var lang = ""
    private var engineInUse = ""
    private var wakeLock: PowerManager.WakeLock? = null
    private val sleepRunnable = Runnable { pause() }

    // ---------- звук: синтез в файлы и плеер ----------
    private const val AHEAD = 6           // столько фраз синтезируется впрок (≈20–40 секунд звука)
    private var generation = 0            // меняется при каждом новом месте чтения: старые файлы и события — в мусор
    private var player: ExoPlayer? = null
    private val ready = HashMap<Int, File?>()  // фраза → готовый звук (null — синтез не удался, пропустить)
    private var synthRequested = -1       // до какой фразы отправлено в синтез
    private var playerTail = -1           // до какой фразы добавлено в плеер
    private var resumable = false         // в плеере звук текущего места — «играть» продолжит с той же точки
    private var failuresInRow = 0
    private val audioDir by lazy { File(app.cacheDir, "tts").apply { mkdirs() } }

    // Сторож: движок принял фразы, но ничего не отдаёт — завис или молча не работает (бывает у облачных и
    // сторонних движков). Лучше сказать об этом, чем молчать.
    private const val STALL_MS = 60_000L
    private val stallRunnable = Runnable {
        if (_state.value.playing && !_state.value.waitingTranslation) {
            val name = engineLabel()
            pause()
            _state.value = _state.value.copy(error = app.getString(R.string.tts_engine_stalled, name))
        }
    }

    private fun armStall() {
        main.removeCallbacks(stallRunnable)
        if (_state.value.playing) main.postDelayed(stallRunnable, STALL_MS)
    }

    private fun engineLabel(): String {
        val t = tts ?: return engineInUse
        val name = engineInUse.ifBlank { t.defaultEngine }
        return t.engines.firstOrNull { it.name == name }?.label ?: name
    }

    fun init(context: Context) {
        app = context.applicationContext
        audioDir.listFiles()?.forEach { it.delete() }  // остатки прошлого запуска
    }

    // ---------- открыть место в книге ----------
    /** Подготовить чтение с абзаца главы (offset — с фразы, где этот символ абзаца). mode — что читать: перевод
     *  (если есть) или оригинал. */
    fun open(book: Book, chapter: Int, paragraph: Int, mode: TextMode, autoplay: Boolean, offset: Int = 0,
             waitTranslation: Boolean = true) {
        val wasPlaying = _state.value.playing
        resetAudio()
        _state.value = _state.value.copy(book = book.path, bookTitle = book.title, cover = book.cover, chapter = chapter,
            mode = mode, loading = true, error = null, waitingTranslation = false)
        scope.launch {
            runCatching {
                if (currentBook?.path != book.path || chapters.isEmpty() || chaptersStale) {
                    chapters = Library.chapters(book.path)
                    chaptersStale = false
                }
                currentBook = book
                val text = Library.chapter(book.path, chapter)
                if (waitTranslation && mode == TextMode.TRANSLATION && text.translation == null &&
                    (Translations.isRunning(book.path) || Prefs.readAhead(book.path) > 0)) {
                    // перевод идёт (или вот-вот начнётся — режим «открытая глава»/«с опережением») — подождать главу
                    val title = chapters.firstOrNull { it.n == chapter }?.title ?: text.source.firstOrNull().orEmpty()
                    sentences = emptyList()
                    waitParagraph = paragraph
                    main.removeCallbacks(stallRunnable)  // молчание не от сбоя — глава ещё переводится
                    _state.value = _state.value.copy(chapterTitle = title, loading = false, waitingTranslation = true,
                        playing = autoplay || wasPlaying, paragraph = -1)
                    if (autoplay || wasPlaying) acquireWake()
                    return@runCatching
                }
                val useTr = mode == TextMode.TRANSLATION && text.translation != null
                val paras = if (useTr) text.translation else text.source
                lang = if (useTr) book.targetLang else book.sourceLang
                sentences = splitSentences(paras)
                index = sentences.indexOfFirst { it.paragraph > paragraph || (it.paragraph == paragraph && it.end > offset) }
                    .coerceAtLeast(0)
                val title = chapters.firstOrNull { it.n == chapter }?.title ?: paras.firstOrNull().orEmpty()
                _state.value = _state.value.copy(chapterTitle = title, loading = false,
                    mode = if (useTr) TextMode.TRANSLATION else TextMode.ORIGINAL)
                publish()
                if (autoplay || wasPlaying) play()
            }.onFailure {
                _state.value = _state.value.copy(loading = false, playing = false, error = it.message)
            }
        }
    }

    private var waitParagraph = 0

    /** Глава переведена (событие Polytome): если чтение ждало её — продолжить. */
    fun onChapterTranslated(book: String, n: Int) {
        val s = _state.value
        val b = currentBook ?: return
        if (b.path == book) chaptersStale = true
        if (s.book != book || s.chapter != n) return
        if (s.waitingTranslation) open(b, n, waitParagraph, TextMode.TRANSLATION, autoplay = s.playing)
        else if (!s.playing) open(b, n, maxOf(0, s.paragraph), s.mode, autoplay = false)
    }

    /** Перевод книги остановился: если ждали главу, которую так и не перевели, — читать оригинал. */
    fun onTranslationStopped(book: String) {
        val s = _state.value
        val b = currentBook ?: return
        if (s.book == book && s.waitingTranslation) {
            open(b, s.chapter, waitParagraph, TextMode.TRANSLATION, autoplay = s.playing, waitTranslation = false)
        }
    }

    // ---------- управление ----------
    fun play() {
        if (_state.value.waitingTranslation) {  // нажали «играть», пока глава переводится — начать, когда будет готова
            _state.value = _state.value.copy(playing = true)
            acquireWake()
            return
        }
        if (_state.value.book == null || sentences.isEmpty()) return
        ensureTts {
            if (!applyLanguage()) return@ensureTts
            _state.value = _state.value.copy(playing = true, error = null)
            acquireWake()
            val p = ensurePlayer()
            applyParams()
            if (resumable) {  // пауза → продолжение с того же места фразы
                p.play()
                requestSynthesis()
                armStall()
            } else startFrom(index)
        }
    }

    fun pause() {
        if (_state.value.waitingTranslation && _state.value.playing) {
            _state.value = _state.value.copy(playing = false, sleepUntil = 0L)
            main.removeCallbacks(sleepRunnable)
            releaseWake()
            return
        }
        if (!_state.value.playing) return
        _state.value = _state.value.copy(playing = false, sleepUntil = 0L)
        main.removeCallbacks(sleepRunnable)
        main.removeCallbacks(stallRunnable)
        player?.pause()
        releaseWake()
        savePosition()
    }

    fun toggle() = if (_state.value.playing) pause() else play()

    fun nextChapter() = jumpChapter(+1)

    fun prevChapter() = jumpChapter(-1)

    private fun jumpChapter(delta: Int) {
        val book = currentBook ?: return
        val i = chapters.indexOfFirst { it.n == _state.value.chapter }
        val target = chapters.getOrNull(i + delta) ?: return
        open(book, target.n, 0, _state.value.mode, autoplay = _state.value.playing)
    }

    fun setSleepTimer(minutes: Int) {
        main.removeCallbacks(sleepRunnable)
        if (minutes <= 0) {
            _state.value = _state.value.copy(sleepUntil = 0L)
            return
        }
        main.postDelayed(sleepRunnable, minutes * 60_000L)
        _state.value = _state.value.copy(sleepUntil = System.currentTimeMillis() + minutes * 60_000L)
    }

    /** Скорость/тон изменились в настройках: плеер применяет их сразу, синтезировать заново не нужно. */
    fun onParamsChanged() = applyParams()

    // ---------- синтезатор ----------
    private fun ensureTts(then: () -> Unit) {
        if (ttsReady && tts != null) {
            then()
            return
        }
        waitingForTts += then
        if (tts != null) return
        engineInUse = Prefs.settings.value.ttsEngine
        tts = TextToSpeech(app, { status ->
            main.post {
                ttsReady = status == TextToSpeech.SUCCESS
                if (!ttsReady) {
                    _state.value = _state.value.copy(playing = false, error = app.getString(R.string.tts_unavailable))
                    waitingForTts.clear()
                    tts = null
                    return@post
                }
                tts!!.setAudioAttributes(SystemAudioAttributes.Builder().setUsage(SystemAudioAttributes.USAGE_MEDIA)
                    .setContentType(SystemAudioAttributes.CONTENT_TYPE_SPEECH).build())
                tts!!.setOnUtteranceProgressListener(progress)
                val jobs = waitingForTts.toList()
                waitingForTts.clear()
                jobs.forEach { it() }
            }
        }, engineInUse.ifBlank { null })
    }

    /** В настройках выбран другой движок — пересоздать синтезатор (следующее чтение пойдёт через него). */
    fun onEngineChanged() {
        val wasPlaying = _state.value.playing
        pause()
        resetAudio()
        _state.value = _state.value.copy(error = null)
        tts?.shutdown()
        tts = null
        ttsReady = false
        if (wasPlaying) play()
    }

    /** В настройках выбран другой голос: заготовленные фразы старым голосом — выбросить. */
    fun onVoiceChanged() {
        if (!applyLanguage()) return
        if (_state.value.playing) startFrom(index) else resetAudio()
    }

    /** Послушать голос: короткая фраза на языке lang (чтение книги ставится на паузу). */
    fun preview(lang: String, text: String) {
        pause()
        _state.value = _state.value.copy(error = null)
        ensureTts {
            val prev = this.lang
            this.lang = lang
            val ok = applyLanguage()
            this.lang = prev
            if (!ok) return@ensureTts
            val s = Prefs.settings.value
            tts?.setSpeechRate(s.ttsSpeed)
            tts?.setPitch(s.ttsPitch)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "preview")
        }
    }

    private fun applyLanguage(): Boolean {
        val t = tts ?: return false
        val code = lang.ifBlank { "en" }
        val voices = runCatching { t.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == Locale.forLanguageTag(code).language }
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        // 1) голос, выбранный в настройках для этого языка и движка
        Prefs.voice(engineInUse, code)?.let { name ->
            voices.firstOrNull { it.name == name }?.let { v ->
                if (t.setVoice(v) == TextToSpeech.SUCCESS) {
                    Log.d("ReadAloud", "voice $name (${engineLabel()})")
                    return true
                }
            }
        }
        // 2) язык как есть; 3) с регионом из списка движка (некоторые движки не принимают «ru», только «ru-RU»)
        val candidates = listOf(Locale.forLanguageTag(code)) +
            runCatching { t.availableLanguages }.getOrNull().orEmpty().filter { it.language == Locale.forLanguageTag(code).language }
        for (loc in candidates) {
            val res = runCatching { t.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (res >= TextToSpeech.LANG_AVAILABLE) return true
        }
        // 4) любой установленный голос этого языка (предпочтительно офлайн)
        voices.sortedWith(compareBy({ it.isNetworkConnectionRequired }, { -it.quality })).firstOrNull()?.let { v ->
            if (t.setVoice(v) == TextToSpeech.SUCCESS) return true
        }
        _state.value = _state.value.copy(playing = false,
            error = app.getString(R.string.tts_no_voice_engine, Locale.forLanguageTag(code).displayLanguage, engineLabel()))
        return false
    }

    /** Скорость и тон — у плеера (сразу, без пересинтеза); синтез всегда в обычном темпе. */
    private fun applyParams() {
        val s = Prefs.settings.value
        player?.playbackParameters = PlaybackParameters(s.ttsSpeed, s.ttsPitch)
    }

    // ---------- конвейер: синтез впрок → плеер ----------
    private fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(app)
        .setLooper(Looper.getMainLooper())
        .setAudioAttributes(MediaAudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)  // наушники выдернули — пауза
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()
        .also {
            it.addListener(playerListener)
            player = it
        }

    /** Начать с фразы i: всё заготовленное раньше — выбросить. */
    private fun startFrom(i: Int) {
        resetAudio()
        index = i.coerceIn(0, maxOf(0, sentences.lastIndex))
        synthRequested = index - 1
        playerTail = index - 1
        resumable = true
        publish()
        tts?.setSpeechRate(1f)
        tts?.setPitch(1f)
        ensurePlayer().playWhenReady = true
        requestSynthesis()
        armStall()
    }

    private fun resetAudio() {
        generation++
        tts?.stop()
        player?.run {
            stop()
            clearMediaItems()
        }
        ready.clear()
        synthRequested = -1
        playerTail = -1
        resumable = false
        failuresInRow = 0
        audioDir.listFiles()?.forEach { it.delete() }
    }

    private fun requestSynthesis() {
        val t = tts ?: return
        while (synthRequested < index + AHEAD && synthRequested + 1 < sentences.size) {
            val i = ++synthRequested
            val params = Bundle().apply {
                // онлайн-голос: подождать сеть подольше, а не сразу подменять офлайн-голосом — запас фраз это позволяет
                putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS, "10000")
                putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_RETRIES_COUNT, "2")
            }
            val res = t.synthesizeToFile(sentences[i].text, params, fileFor(generation, i), "$generation:$i")
            if (res == TextToSpeech.ERROR) {
                Log.w("ReadAloud", "synthesizeToFile() отклонён движком ${engineLabel()}")
                val name = engineLabel()
                pause()
                _state.value = _state.value.copy(error = app.getString(R.string.tts_engine_stalled, name))
                return
            }
        }
    }

    private fun fileFor(gen: Int, i: Int) = File(audioDir, "$gen-$i.wav")

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {}

        override fun onDone(utteranceId: String) {
            main.post { onSynthesized(utteranceId, ok = true) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            main.post { onSynthesized(utteranceId, ok = false) }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            Log.w("ReadAloud", "синтез $utteranceId: ошибка $errorCode")
            main.post { onSynthesized(utteranceId, ok = false) }
        }
    }

    private fun parse(id: String): Int? {
        val (gen, i) = id.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: -1) to (it.getOrNull(1)?.toIntOrNull() ?: -1) }
        return if (gen == generation && i >= 0) i else null
    }

    private fun onSynthesized(id: String, ok: Boolean) {
        val i = parse(id) ?: return
        val f = fileFor(generation, i)
        val good = ok && f.length() > 64  // пустой WAV — только заголовок
        failuresInRow = if (good) 0 else failuresInRow + 1
        if (failuresInRow >= 3) {  // движок раз за разом не может — не молчать
            val name = engineLabel()
            pause()
            _state.value = _state.value.copy(error = app.getString(R.string.tts_engine_stalled, name))
            return
        }
        ready[i] = if (good) f else null
        armStall()
        feedPlayer()
    }

    /** Готовые фразы — в плеер строго по порядку. */
    private fun feedPlayer() {
        val p = player ?: return
        var added = false
        while (ready.containsKey(playerTail + 1)) {
            val i = ++playerTail
            ready[i]?.let { f ->
                p.addMediaItem(MediaItem.Builder().setMediaId("$generation:$i").setUri(Uri.fromFile(f)).build())
                added = true
            }
        }
        when {
            added && p.playbackState == Player.STATE_IDLE -> p.prepare()
            added && p.playbackState == Player.STATE_ENDED -> {  // плеер обогнал синтез и ждал — продолжить
                p.seekToNextMediaItem()
                p.prepare()
            }
        }
        // глава кончилась на фразах, которые не удалось синтезировать
        if (playerTail >= sentences.lastIndex && (p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED)) {
            onChapterFinished()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val i = mediaItem?.mediaId?.let(::parse) ?: return
            armStall()
            Log.d("ReadAloud", "ch ${_state.value.chapter} sentence $i/${sentences.size}")
            val prevParagraph = sentences.getOrNull(index)?.paragraph
            index = i
            publish()
            if (prevParagraph != sentences.getOrNull(i)?.paragraph) savePosition()
            fileFor(generation, i - 2).delete()  // прочитанное больше не нужно
            requestSynthesis()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && _state.value.playing && playerTail >= sentences.lastIndex &&
                synthRequested >= sentences.lastIndex) onChapterFinished()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // плеер сам встал на паузу: другое приложение забрало звук или выдернули наушники
            if (!playWhenReady && _state.value.playing && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) pause()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w("ReadAloud", "плеер: ${error.errorCodeName}")  // битый файл фразы — пропустить её
            val p = player ?: return
            if (p.hasNextMediaItem()) p.seekToNextMediaItem()
            p.prepare()
        }
    }

    private fun onChapterFinished() {
        if (!_state.value.playing) return
        val pos = chapters.indexOfFirst { it.n == _state.value.chapter }
        val next = chapters.getOrNull(pos + 1)
        val book = currentBook
        if (next == null || book == null) {
            pause()
            return
        }
        open(book, next.n, 0, _state.value.mode, autoplay = true)
        TranslationController.readAhead(book.path, next.n)
    }

    private fun publish() {
        val s = sentences.getOrNull(index) ?: return
        _state.value = _state.value.copy(paragraph = s.paragraph, start = s.start, end = s.end)
    }

    private fun savePosition() {
        val book = _state.value.book ?: return
        val s = sentences.getOrNull(index) ?: return
        Prefs.savePosition(book, Position(_state.value.chapter, s.paragraph, _state.value.mode))
    }

    // ---------- сон процессора (аудиофокус и наушники — у плеера) ----------
    private fun acquireWake() {
        if (wakeLock == null) {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bayun:read-aloud").apply { setReferenceCounted(false) }
        }
        wakeLock?.acquire(3 * 60 * 60 * 1000L)
    }

    private fun releaseWake() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }
}
