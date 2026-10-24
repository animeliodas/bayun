package io.github.animeliodas.bayun.ui

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.animeliodas.bayun.tts.PlaybackService
import io.github.animeliodas.bayun.tts.ReadAloud

/**
 * Связь интерфейса с PlaybackService: пока контроллер подключён, сервис запущен и при начале чтения сам
 * переходит в foreground (уведомление, экран блокировки) — чтение не прерывается при выключенном экране.
 *
 * Контроллер живёт на контексте приложения, а не Activity: Activity система пересоздаёт (блокировка экрана,
 * переключение приложений), и привязка к сервису через её контекст рвалась — release() потом падал с
 * «Service not registered» и ронял всё приложение.
 */
object Playback {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    fun connect(context: Context) {
        if (future != null) return
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token)
            .setListener(object : MediaController.Listener {
                // сервис остановился или упал — при следующем подключении собрать контроллер заново
                override fun onDisconnected(c: MediaController) {
                    if (controller === c) {
                        controller = null
                        future = null
                    }
                }
            })
            .buildAsync()
        future = f
        f.addListener({ controller = runCatching { f.get() }.getOrNull() }, ContextCompat.getMainExecutor(app))
    }

    fun release() {
        future?.let { runCatching { MediaController.releaseFuture(it) } }
        future = null
        controller = null
    }

    fun toggle() {
        val c = controller?.takeIf { it.isConnected }
        if (ReadAloud.state.value.playing) c?.pause() ?: ReadAloud.pause() else c?.play() ?: ReadAloud.play()
    }
}
