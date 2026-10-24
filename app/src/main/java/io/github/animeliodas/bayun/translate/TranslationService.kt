package io.github.animeliodas.bayun.translate

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.animeliodas.bayun.MainActivity
import io.github.animeliodas.bayun.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Держит приложение живым, пока идут переводы: foreground-сервис с уведомлением о прогрессе и кнопкой «Остановить»,
 * wake lock (процессор не засыпает ночью). Сам перевод — в TranslationController и Polytome.
 * Android 15+ даёт таким сервисам не больше 6 часов в сутки: по onTimeout перевод мягко останавливается и
 * продолжается, когда пользователь откроет приложение (прогресс сохраняется после каждого батча).
 */
class TranslationService : Service() {
    companion object {
        private const val NOTIFY_ID = 42
        private const val ACTION_STOP = "stop"

        fun stopIntent(context: Context, book: String): Intent =
            Intent(context, TranslationService::class.java).setAction(ACTION_STOP).putExtra("book", book)
    }

    private val scope = MainScope()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(this, NOTIFY_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bayun:translation").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
            }
        scope.launch {
            Translations.progress.collect {
                if (!Translations.anyRunning) {
                    ServiceCompat.stopForeground(this@TranslationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java).notify(NOTIFY_ID, notification())
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) intent.getStringExtra("book")?.let(TranslationController::stop)
        return START_NOT_STICKY
    }

    /** Android 15+: лимит фоновой работы исчерпан — остановиться за несколько секунд, продолжить при открытии. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        TranslationController.pauseAllBySystem()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): Notification {
        val running = Translations.progress.value.filterValues { it.running }
        val book = running.keys.firstOrNull()
        val p = book?.let { running[it] } ?: Translations.Progress()
        val text = when {
            book == null -> getString(R.string.translation_starting)
            p.preparing || p.total == 0 -> getString(R.string.translation_preparing)
            else -> getString(R.string.translation_progress, p.done, p.total)
        }
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(this, TranslationController.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_translate)
            .setContentTitle(book?.let(TranslationController::title) ?: getString(R.string.channel_translation))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(p.total, p.done, p.total == 0)
            .setContentIntent(open)
        if (book != null) {
            b.addAction(0, getString(R.string.stop), PendingIntent.getService(this, book.hashCode(), stopIntent(this, book),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        return b.build()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }
}
