package io.github.animeliodas.bayun.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.animeliodas.bayun.R
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.data.strings
import io.github.animeliodas.bayun.engine.PolytomeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val BENCH_URL = "https://github.com/animeliodas/polytome/tree/main/bench"
private const val LEADERBOARD_URL = "https://github.com/animeliodas/polytome/blob/main/bench/LEADERBOARD.md"

/** Пара языков набора для замеров в понятном виде: «китайский → русский». */
private fun pairLabel(pair: String): String = pair.split("-").let { "${langName(it[0])} → ${langName(it[1])}" }

/** Замечания проверок — человеческими словами. */
private fun issueLabel(context: Context, type: String): String = context.getString(when (type) {
    "untranslated", "punct" -> R.string.issue_untranslated
    "name_variant", "unknown_name" -> R.string.issue_names
    "name_missing" -> R.string.issue_name_missing
    "gender" -> R.string.issue_gender
    "number", "number_words" -> R.string.issue_numbers
    "short", "missing" -> R.string.issue_short
    "long" -> R.string.issue_long
    "duplicate" -> R.string.issue_duplicate
    else -> R.string.issue_other
})

/**
 * «Проверить модель»: модель переводит общий тестовый текст тем же конвейером, что и книги; качество меряет код
 * Polytome (имена, род, числа, пропуски). Результат можно отправить в общую таблицу моделей.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelBenchSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Prefs.settings.value
    val models = listOfNotNull(s.strongModel.trim().takeIf { it.isNotBlank() },
        s.fastModel.trim().takeIf { it.isNotBlank() && it != s.strongModel.trim() })
    var model by remember { mutableStateOf(models.firstOrNull()) }
    var pairs by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var budget by remember { mutableStateOf(100_000L) }
    // пары — на язык чтения пользователя (их в наборе 13: семь исходных языков на русский и на английский)
    val lang = s.effectiveReadingLang
    var pair by remember { mutableStateOf("zh-$lang") }
    var running by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                PolytomeEngine.configure(Prefs.polytomeConfig())
                val info = PolytomeEngine.callObject("bench_info")
                budget = info.optLong("budget", 100_000L)
                val arr = info.optJSONArray("pairs")
                (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
                    .map { it.optString("pair") to it.optLong("tokens_estimate") }
            }
        }.onSuccess { all ->
            pairs = all.filter { it.first.endsWith("-$lang") }.ifEmpty { all }
            if (pairs.none { it.first == pair }) pairs.firstOrNull()?.let { pair = it.first }
        }.onFailure { error = it.message }
    }
    // прогресс замера: события Polytome bench_progress
    LaunchedEffect(Unit) {
        PolytomeEngine.events.collect { ev ->
            if (ev.optString("ev") == "bench_progress" && running) {
                stage = context.getString(if (ev.optString("stage") == "repair") R.string.bench_stage_repair
                    else R.string.bench_stage_translate, ev.optInt("chapter"), ev.optInt("chapters"))
            }
        }
    }
    // закрыли окно во время замера — остановить, чтобы не тратить токены впустую
    DisposableEffect(Unit) {
        onDispose {
            if (running) scope.launch(Dispatchers.IO) { runCatching { PolytomeEngine.call("bench_cancel") } }
        }
    }

    fun start() {
        val m = model ?: return
        running = true
        result = null
        error = null
        stage = context.getString(R.string.bench_starting)
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    PolytomeEngine.configure(Prefs.polytomeConfig())
                    if (!PolytomeEngine.callObject("check").optBoolean("ready")) {
                        throw IllegalStateException(context.getString(R.string.translation_not_configured))
                    }
                    PolytomeEngine.callObject("bench", JSONObject().put("model", "${s.provider}:$m").put("pair", pair))
                }
            }
            running = false
            r.onSuccess { rec ->
                when (val e = rec.optString("error")) {
                    "" -> result = rec
                    "cancelled" -> {}
                    // жжёт токены: прогон остановлен на лимите — так и сказать, с цифрой
                    "budget" -> error = context.getString(R.string.bench_over_budget, rec.optLong("budget", budget) / 1000)
                    else -> error = e
                }
            }.onFailure { error = it.message ?: it.toString() }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.bench_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.bench_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (models.isEmpty()) {
                Text(stringResource(R.string.translation_not_configured), color = MaterialTheme.colorScheme.error)
            }
            if (models.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    models.forEach { m ->
                        FilterChip(selected = model == m, enabled = !running, onClick = { model = m }, label = { Text(m) })
                    }
                }
            } else model?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pairs.forEach { (p, _) ->
                    FilterChip(selected = pair == p, enabled = !running, onClick = { pair = p }, label = { Text(pairLabel(p)) })
                }
            }
            pairs.firstOrNull { it.first == pair }?.let { (_, tokens) ->
                Text(stringResource(R.string.bench_cost, (tokens + 500) / 1000, budget / 1000),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (running) {
                Text(stage, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    scope.launch(Dispatchers.IO) { runCatching { PolytomeEngine.call("bench_cancel") } }
                }) { Text(stringResource(R.string.cancel)) }
            } else {
                Button(enabled = model != null && pairs.isNotEmpty(), onClick = ::start) {
                    Text(stringResource(if (result == null) R.string.bench_start else R.string.bench_again))
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            result?.let { BenchResult(context, it) }
            HorizontalDivider()
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LEADERBOARD_URL))) }) {
                Text(stringResource(R.string.bench_leaderboard))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BenchResult(context: Context, r: JSONObject) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%.0f".format(r.optDouble("score")), style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(start = 12.dp))
            Column {
                Text(stringResource(R.string.bench_score), style = MaterialTheme.typography.titleSmall)
                Text(pairLabel(r.optString("pair")), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.bench_quality, r.optDouble("first_pass_ok"), r.optDouble("final_ok")),
            style = MaterialTheme.typography.bodyMedium)
        val tokens = r.optJSONObject("tokens")?.let { it.optLong("prompt") + it.optLong("completion") } ?: 0L
        val cost = if (r.isNull("cost_usd")) null else r.optDouble("cost_usd")
        Text(stringResource(R.string.bench_usage, r.optDouble("seconds"), tokens / 1000.0) +
            (cost?.let { " · $" + "%.4f".format(it) } ?: ""), style = MaterialTheme.typography.bodyMedium)
        if (!r.isNull("cost_per_1m_chars_usd")) {
            Text(stringResource(R.string.bench_book_cost, r.optDouble("cost_per_1m_chars_usd")), style = MaterialTheme.typography.bodySmall)
        }
        val left = r.optJSONObject("issues_final")
        if (left != null && left.length() > 0) {
            val byLabel = LinkedHashMap<String, Int>()
            left.keys().forEach { k -> issueLabel(context, k).let { byLabel[it] = (byLabel[it] ?: 0) + left.optInt(k) } }
            Text(stringResource(R.string.bench_left, byLabel.entries.joinToString(", ") { "${it.key} ×${it.value}" }),
                style = MaterialTheme.typography.bodySmall)
        } else Text(stringResource(R.string.bench_clean), style = MaterialTheme.typography.bodySmall)
        // пример перевода — оценить и глазами
        r.optJSONObject("translation")?.optJSONArray("1")?.strings()?.drop(1)?.take(3)?.let { sample ->
            Text(sample.joinToString("\n\n"), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { share(context, r) }) { Text(stringResource(R.string.bench_share)) }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BENCH_URL))) }) {
            Text(stringResource(R.string.bench_how_to_add))
        }
    }
}

/** Отправить результат (JSON без ключей и адресов) — например, себе в почту или в GitHub, чтобы добавить в таблицу. */
private fun share(context: Context, r: JSONObject) {
    val name = "${r.optString("date")}_${r.optString("model").replace(Regex("[^A-Za-z0-9._-]+"), "-")}.json"
    val text = r.toString(1)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Polytome bench: ${r.optString("model")} (${r.optString("pair")}) — bench/results/${r.optString("pair")}/$name")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.bench_share)))
}
