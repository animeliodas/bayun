package io.github.animeliodas.bayun.engine

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Python вызывает onEvent(json) для каждого события перевода (см. Polytome docs/INTEGRATION.md). */
interface PolytomeListener {
    fun onEvent(json: String)
}

class PolytomeException(val type: String, message: String) : Exception(message)

/**
 * Мост к движку Polytome (Python внутри APK через Chaquopy). Весь обмен — JSON-строками через
 * Polytome.call(method, params). Вызывать НЕ из главного потока: первый вызов запускает Python.
 */
object PolytomeEngine {
    private val lock = Any()
    private lateinit var appContext: Context
    private var api: PyObject? = null
    private var configJson = "{}"

    lateinit var home: File
        private set

    private val _events = MutableSharedFlow<JSONObject>(extraBufferCapacity = 256)
    val events: SharedFlow<JSONObject> = _events

    private val listener = object : PolytomeListener {
        override fun onEvent(json: String) {
            runCatching { _events.tryEmit(JSONObject(json)) }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        home = File(appContext.filesDir, "polytome").apply { mkdirs() }
    }

    val booksDir: File get() = File(home, "books")

    /** Новый конфиг (ключи, модели, язык) применяется к следующим вызовам. Идущий перевод не прерывается. */
    fun configure(json: String) = synchronized(lock) {
        if (json != configJson) {
            configJson = json
            api = null
        }
    }

    private fun ensure(): PyObject = synchronized(lock) {
        api ?: run {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(appContext))
                // сообщения движка — на языке интерфейса телефона (русский или английский)
                val ui = if (Locale.getDefault().language == "ru") "ru" else "en"
                Python.getInstance().getModule("os").get("environ")!!.callAttr("__setitem__", "POLYTOME_UI_LANG", ui)
            }
            val cls = Python.getInstance().getModule("polytome.api").get("Polytome")!!
            cls.callAttr("from_json", configJson, home.absolutePath, listener).also { api = it }
        }
    }

    /** Любой метод API Polytome. Ошибка движка → PolytomeException с текстом для пользователя. */
    fun call(method: String, params: JSONObject = JSONObject()): Any? {
        val raw = ensure().callAttr("call", method, params.toString()).toString()
        val res = JSONObject(raw)
        if (!res.optBoolean("ok")) {
            val e = res.optJSONObject("error") ?: JSONObject()
            throw PolytomeException(e.optString("type"), e.optString("message"))
        }
        return res.opt("result")
    }

    fun callObject(method: String, params: JSONObject = JSONObject()): JSONObject =
        call(method, params) as? JSONObject ?: JSONObject()

    fun warmUp() {
        ensure()
    }
}
