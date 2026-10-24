package io.github.animeliodas.bayun.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.animeliodas.bayun.R
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.data.objects
import io.github.animeliodas.bayun.engine.PolytomeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Модель из списка провайдера; цены — USD за 1 млн токенов (если провайдер их сообщает). */
data class ModelInfo(val id: String, val name: String, val context: Int?, val priceIn: Double?, val priceOut: Double?,
                     val free: Boolean) {
    val price: Double? get() = if (priceIn != null && priceOut != null && priceIn >= 0 && priceOut >= 0) priceIn + priceOut else null
}

/** Списки моделей за сессию: провайдеры меняют их редко, а запрос /models бывает тяжёлым (OpenRouter — сотни моделей). */
object ModelCatalog {
    private val cache = HashMap<String, List<ModelInfo>>()

    fun cached(provider: String, baseUrl: String): List<ModelInfo>? = cache["$provider|$baseUrl"]

    suspend fun load(provider: String, baseUrl: String): List<ModelInfo> = cache["$provider|$baseUrl"]
        ?: withContext(Dispatchers.IO) {
            PolytomeEngine.configure(Prefs.polytomeConfig())
            val arr = PolytomeEngine.call("models", JSONObject().put("provider", provider)) as? JSONArray ?: JSONArray()
            arr.objects().map {
                ModelInfo(it.optString("id"), it.optString("name").ifBlank { it.optString("id") },
                    it.optInt("context").takeIf { c -> c > 0 },
                    if (it.has("price_in")) it.optDouble("price_in") else null,
                    if (it.has("price_out")) it.optDouble("price_out") else null,
                    it.optBoolean("free"))
            }.also { cache["$provider|$baseUrl"] = it }
        }
}

private fun money(v: Double): String = when {
    v == 0.0 -> "0"
    v < 0.1 -> "%.3f".format(v)
    v < 10 -> "%.2f".format(v)
    else -> "%.0f".format(v)
}

@Composable
fun modelDetails(m: ModelInfo): String = listOfNotNull(
    when {
        m.free -> stringResource(R.string.model_free)
        m.price != null -> stringResource(R.string.model_price, money(m.priceIn!!), money(m.priceOut!!))
        else -> null
    },
    m.context?.let { stringResource(R.string.model_context, it / 1000) },
).joinToString(" · ")

/** Выбор модели из живого списка провайдера: поиск, «только бесплатные», «сначала дешёвые». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(provider: String, baseUrl: String, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var models by remember { mutableStateOf(ModelCatalog.cached(provider, baseUrl)) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var onlyFree by remember { mutableStateOf(false) }
    var cheapFirst by remember { mutableStateOf(false) }
    LaunchedEffect(provider, baseUrl) {
        if (models == null) runCatching { models = ModelCatalog.load(provider, baseUrl) }.onFailure { error = it.message ?: it.toString() }
    }
    val shown = remember(models, query, onlyFree, cheapFirst) {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        models.orEmpty()
            .filter { m -> words.all { w -> w in m.id.lowercase() || w in m.name.lowercase() } }
            .filter { !onlyFree || it.free }
            .let { list -> if (cheapFirst) list.sortedBy { it.price ?: Double.MAX_VALUE } else list }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.choose_model), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(stringResource(R.string.model_search)) },
                modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = onlyFree, onClick = { onlyFree = !onlyFree }, label = { Text(stringResource(R.string.models_free)) })
                FilterChip(selected = cheapFirst, onClick = { cheapFirst = !cheapFirst },
                    label = { Text(stringResource(R.string.models_cheap_first)) })
            }
            Text(stringResource(R.string.models_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 16.dp))
                models == null -> Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.models_loading))
                }
                shown.isEmpty() -> Text(stringResource(R.string.models_none), modifier = Modifier.padding(vertical = 16.dp))
                else -> LazyColumn(Modifier.heightIn(max = 560.dp)) {
                    items(shown, key = { it.id }) { m ->
                        ListItem(
                            headlineContent = { Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Column {
                                    if (m.name != m.id) Text(m.id, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall)
                                    val d = modelDetails(m)
                                    if (d.isNotBlank()) Text(d, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary)
                                }
                            },
                            trailingContent = { if (m.id == current) Icon(Icons.Default.Check, null) },
                            modifier = Modifier.clickable { onPick(m.id); onDismiss() },
                        )
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
