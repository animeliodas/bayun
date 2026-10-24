package io.github.animeliodas.bayun.translate

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.animeliodas.bayun.data.Prefs
import java.util.Calendar

/**
 * Ночной перевод: каждую ночь в выбранное время продолжать перевод книги, пока она не переведена целиком.
 * Длинную книгу за одну ночь не перевести, а Android 15+ даёт фоновой работе не больше 6 часов в сутки — поэтому
 * не «один раз», а каждую ночь с того же места (прогресс сохраняется после каждого батча).
 *
 * Будильник точный, если разрешены «Будильники и напоминания»; иначе — неточный (Android может сдвинуть его на
 * несколько минут). Запустить фоновую работу из будильника Android разрешает при точном будильнике или если
 * Баюн исключён из экономии батареи.
 */
object NightTranslation {
    private const val ACTION = "io.github.animeliodas.bayun.NIGHT_TRANSLATION"
    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun schedule(book: String, minuteOfDay: Int) {
        Prefs.setNightTime(book, minuteOfDay)
        arm(book, minuteOfDay)
    }

    fun cancel(book: String) {
        Prefs.setNightTime(book, null)
        alarms().cancel(intent(book))
    }

    /** После перезагрузки или обновления приложения будильники пропадают — поставить заново. */
    fun rearmAll() = Prefs.nightBooks().forEach { (book, m) -> arm(book, m) }

    /** Можно ли ставить точные будильники (Android 12+ спрашивает разрешение «Будильники и напоминания»). */
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms().canScheduleExactAlarms()

    /** Ближайшее наступление минуты суток (сегодня, если ещё не прошла, иначе завтра). */
    fun nextTrigger(minuteOfDay: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now + 30_000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun arm(book: String, minuteOfDay: Int) {
        val at = nextTrigger(minuteOfDay)
        val am = alarms()
        if (exactAllowed()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(book))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(book))
        Log.d("Translation", "ночной перевод $book: ${java.util.Date(at)}")
    }

    /** Будильник сработал: продолжить перевод и поставить будильник на следующую ночь. */
    internal fun onAlarm(book: String) {
        val m = Prefs.nightTime(book) ?: return
        arm(book, m)
        TranslationController.startNight(book)
    }

    private fun alarms() = app.getSystemService(AlarmManager::class.java)

    private fun intent(book: String): PendingIntent = PendingIntent.getBroadcast(app, book.hashCode(),
        Intent(app, NightAlarmReceiver::class.java).setAction(ACTION).putExtra("book", book),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

class NightAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("book")?.let(NightTranslation::onAlarm)
    }
}

/** Перезагрузка, обновление приложения, смена времени — будильники ночного перевода ставятся заново. */
class NightRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NightTranslation.rearmAll()
    }
}
