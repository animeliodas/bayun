package io.github.animeliodas.bayun.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.animeliodas.bayun.R
import io.github.animeliodas.bayun.data.Book
import io.github.animeliodas.bayun.data.Library
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.engine.PolytomeEngine
import io.github.animeliodas.bayun.translate.NightTranslation
import io.github.animeliodas.bayun.translate.TranslationController
import io.github.animeliodas.bayun.translate.Translations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private data class Estimate(val chapters: Int, val tokens: Long, val minutes: Int, val profile: String)

fun Context.ignoresBatteryOptimizations(): Boolean =
    (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

/** Разрешение на уведомления (Android 13+): без него не видно прогресса перевода и кнопки «Остановить». */
@Composable
private fun rememberNotificationAsk(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Подсказка и кнопка «Разрешить работу в фоне» (исключение из экономии батареи), пока разрешения нет. */
@SuppressLint("BatteryLife")
@Composable
private fun BatteryHint() {
    val context = LocalContext.current
    var battery by remember { mutableStateOf(context.ignoresBatteryOptimizations()) }
    val screen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        battery = context.ignoresBatteryOptimizations()
    }
    if (battery) return
    Text(stringResource(R.string.battery_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = {
        screen.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.allow_background)) }
}

fun formatMinute(context: Context, minute: Int): String {
    val c = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, minute / 60)
        set(java.util.Calendar.MINUTE, minute % 60)
    }
    return DateFormat.getTimeFormat(context).format(c.time)
}

/**
 * «Перевести всю книгу»: оценка (главы, токены, время), порядок — с текущей главы; начать сейчас или перевести
 * по ночам. Без исключения из экономии батареи система может остановить долгий перевод — подсказка здесь же.
 */
@Composable
fun TranslateBookDialog(book: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var order by remember { mutableStateOf<List<Int>?>(null) }
    var estimate by remember { mutableStateOf<Estimate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var night by remember { mutableStateOf(false) }
    val askNotifications = rememberNotificationAsk()

    LaunchedEffect(book) {
        runCatching {
            val chs = TranslationController.chaptersFromCurrent(book)
            order = chs
            val e = withContext(Dispatchers.IO) {
                PolytomeEngine.configure(Prefs.polytomeConfig())
                PolytomeEngine.callObject("estimate", JSONObject().put("book", book).put("chapters", JSONArray(chs)))
            }
            estimate = Estimate(e.optInt("chapters"), e.optLong("tokens_estimate"), e.optInt("time_estimate_min"),
                e.optString("profile"))
        }.onFailure { error = it.message }
    }

    if (night) {
        NightTimeDialog(Prefs.nightTime(book) ?: 60, onPick = { m ->
            askNotifications()
            NightTranslation.schedule(book, m)
        }, onDismiss = { night = false; onDismiss() })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translate_book_q)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val e = estimate
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    e == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                        Text(stringResource(R.string.estimating))
                    }
                    e.chapters == 0 -> Text(stringResource(R.string.nothing_to_translate))
                    else -> {
                        Text(stringResource(R.string.estimate_text, e.chapters, formatTokens(e.tokens),
                            formatDuration(context, e.minutes), e.profile))
                        Text(stringResource(R.string.order_from_current), style = MaterialTheme.typography.bodySmall)
                    }
                }
                BatteryHint()
            }
        },
        confirmButton = {
            TextButton(enabled = (estimate?.chapters ?: 0) > 0, onClick = {
                askNotifications()
                TranslationController.start(book, order)
                onDismiss()
            }) { Text(stringResource(R.string.now)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(enabled = (estimate?.chapters ?: 0) > 0, onClick = { night = true }) {
                    Text(stringResource(R.string.night_short))
                }
            }
        },
    )
}

/** Время ночного перевода: каждую ночь с этого времени, пока книга не переведена. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightTimeDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60,
        is24Hour = DateFormat.is24HourFormat(context))
    var exact by remember { mutableStateOf(NightTranslation.exactAllowed()) }
    val exactScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exact = NightTranslation.exactAllowed()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.night_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeInput(state)
                Text(stringResource(R.string.night_hint), style = MaterialTheme.typography.bodySmall)
                if (!exact && Build.VERSION.SDK_INT >= 31) {
                    Text(stringResource(R.string.exact_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        exactScreen.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.allow_exact)) }
                }
                BatteryHint()
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(state.hour * 60 + state.minute)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Режим перевода: 0 — вручную, 1 — открытая глава, N ≥ 2 — держать N глав впереди (число вводится вручную). */
@Composable
fun AheadModePicker(value: Int, onChange: (Int) -> Unit) {
    var custom by remember { mutableStateOf(if (value >= 2) value.toString() else "5") }
    Column {
        ModeRow(value == 0, stringResource(R.string.mode_manual), stringResource(R.string.mode_manual_hint), onClick = { onChange(0) })
        ModeRow(value == 1, stringResource(R.string.mode_open), stringResource(R.string.mode_open_hint), onClick = { onChange(1) })
        ModeRow(value >= 2, stringResource(R.string.mode_ahead), stringResource(R.string.mode_ahead_hint),
            onClick = { onChange(custom.toIntOrNull()?.coerceIn(2, 999) ?: 5) }) {
            OutlinedTextField(value = custom, singleLine = true, modifier = Modifier.width(96.dp),
                label = { Text(stringResource(R.string.chapters_field)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { t ->
                    custom = t.filter(Char::isDigit).take(3)
                    custom.toIntOrNull()?.takeIf { it >= 2 }?.let(onChange)
                })
        }
    }
}

@Composable
private fun ModeRow(selected: Boolean, label: String, hint: String, onClick: () -> Unit,
                    extra: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        extra?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

/**
 * Перевод книги в одном месте: режим «пока читаю» (вручную / открытая глава / на N глав вперёд), вся книга
 * сейчас или по ночам, название и оглавление на языке читателя.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookTranslationSheet(path: String, currentChapter: Int?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val books by Library.books.collectAsState()
    val progress by Translations.progress.collectAsState()
    val metaRunning by Translations.meta.collectAsState()
    var book by remember { mutableStateOf<Book?>(books.firstOrNull { it.path == path }) }
    LaunchedEffect(path, books) { book = books.firstOrNull { it.path == path } ?: runCatching { Library.book(path) }.getOrNull() }
    var ahead by remember { mutableIntStateOf(Prefs.readAhead(path)) }
    var night by remember { mutableStateOf(Prefs.nightTime(path)) }
    var showWhole by remember { mutableStateOf(false) }
    var showNight by remember { mutableStateOf(false) }
    var metaError by remember { mutableStateOf<String?>(null) }
    val askNotifications = rememberNotificationAsk()
    val tr = progress[path]

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.book_translation), style = MaterialTheme.typography.titleLarge)
            book?.let { b ->
                Text(b.title, style = MaterialTheme.typography.titleMedium)
                if (b.originalTitle.isNotBlank() && b.originalTitle != b.title) {
                    Text(stringResource(R.string.original_title, b.originalTitle), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${langName(b.sourceLang)} → ${langName(b.targetLang)} · " +
                    stringResource(R.string.translated_of, b.chaptersTranslated, b.chaptersTotal),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            if (tr?.running == true) {
                if (tr.total > 0) {
                    LinearProgressIndicator(progress = { tr.done.toFloat() / tr.total }, Modifier.fillMaxWidth())
                    Text(stringResource(R.string.translation_progress, tr.done, tr.total), style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.translation_preparing), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { TranslationController.stop(path) }) { Text(stringResource(R.string.stop_translation)) }
            }

            HorizontalDivider()
            Text(stringResource(R.string.while_reading), style = MaterialTheme.typography.titleSmall)
            AheadModePicker(ahead) { v ->
                ahead = v
                Prefs.setReadAhead(path, v)
                if (v > 0) currentChapter?.let { TranslationController.readAhead(path, it) }
            }

            HorizontalDivider()
            Text(stringResource(R.string.whole_book), style = MaterialTheme.typography.titleSmall)
            Button(enabled = tr?.running != true && (book?.let { it.chaptersTranslated < it.chaptersTotal } ?: false),
                onClick = { showWhole = true }) { Text(stringResource(R.string.translate_now)) }
            val n = night
            if (n != null) {
                Text(stringResource(R.string.night_scheduled, formatMinute(context, n)), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showNight = true }) { Text(stringResource(R.string.night_change)) }
                    TextButton(onClick = { NightTranslation.cancel(path); night = null }) { Text(stringResource(R.string.night_off)) }
                }
            } else {
                OutlinedButton(enabled = book?.let { it.chaptersTranslated < it.chaptersTotal } ?: false,
                    onClick = { showNight = true }) { Text(stringResource(R.string.night_button)) }
            }

            HorizontalDivider()
            Text(stringResource(R.string.title_and_contents), style = MaterialTheme.typography.titleSmall)
            val running = path in metaRunning
            Text(stringResource(when {
                running -> R.string.meta_running
                book?.metaTranslated == true -> R.string.meta_done
                else -> R.string.meta_not_done
            }), style = MaterialTheme.typography.bodySmall)
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(enabled = !running && tr?.running != true, onClick = {
                metaError = null
                TranslationController.translateMeta(path) { metaError = it }
            }) { Text(stringResource(if (book?.metaTranslated == true) R.string.meta_update else R.string.meta_translate)) }
            metaError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showWhole) TranslateBookDialog(path) { showWhole = false; night = Prefs.nightTime(path) }
    if (showNight) NightTimeDialog(night ?: 60, onPick = { m ->
        askNotifications()
        NightTranslation.schedule(path, m)
        night = m
    }, onDismiss = { showNight = false })
}

fun langName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).ifBlank { code }

private fun formatTokens(t: Long): String {
    val ru = Locale.getDefault().language == "ru"
    return when {
        t >= 1_000_000 -> if (ru) "%.1f млн".format(t / 1_000_000.0) else "%.1fM".format(t / 1_000_000.0)
        t >= 1_000 -> if (ru) "${t / 1000} тыс." else "${t / 1000}k"
        else -> t.toString()
    }
}

private fun formatDuration(context: Context, minutes: Int): String =
    if (minutes >= 90) context.getString(R.string.hours_n, (minutes + 30) / 60)
    else context.getString(R.string.minutes_n, maxOf(1, minutes))
