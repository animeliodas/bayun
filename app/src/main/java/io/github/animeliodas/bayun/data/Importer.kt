package io.github.animeliodas.bayun.data

import android.content.Context
import android.net.Uri
import io.github.animeliodas.bayun.engine.PolytomeEngine
import io.github.animeliodas.bayun.translate.TranslationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Импорт книги на уровне приложения: не прерывается, если уйти с экрана, и сообщает этап и прогресс
 * (Polytome присылает события import_progress: parse — разбор файла, write — запись глав).
 */
object Importer {
    data class State(
        val running: Boolean = false,
        val stage: String = "",
        val done: Int = 0,
        val total: Int = 0,
        val result: Book? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            PolytomeEngine.events.collect { ev ->
                if (ev.optString("ev") == "import_progress" && _state.value.running) {
                    _state.value = _state.value.copy(stage = ev.optString("stage"), done = ev.optInt("done"),
                        total = ev.optInt("total"))
                }
            }
        }
    }

    fun start(context: Context, uri: Uri, targetLang: String) {
        if (_state.value.running) return
        _state.value = State(running = true, stage = "copy")
        scope.launch {
            _state.value = runCatching { Library.import(context.applicationContext, uri, targetLang) }
                .fold({ State(result = it) }, { State(error = it.message ?: it.toString()) })
            // настройки «Новые книги»: сразу перевести название и оглавление и/или первые главы
            _state.value.result?.let { launch(Dispatchers.Main) { TranslationController.afterImport(it) } }
        }
    }

    /** Экран показал результат (открыл книгу или ошибку) — забыть его. */
    fun consume() {
        if (!_state.value.running) _state.value = State()
    }

    /** Остатки импорта, прерванного закрытием приложения: копия файла и недописанная папка книги. */
    fun cleanupLeftovers(context: Context) {
        File(context.cacheDir, "import").deleteRecursively()
        PolytomeEngine.booksDir.listFiles { f -> f.isDirectory && f.name.startsWith(".importing-") }
            ?.forEach { it.deleteRecursively() }
    }
}
