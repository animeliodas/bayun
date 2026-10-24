package io.github.animeliodas.bayun.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.animeliodas.bayun.BuildConfigInfo
import io.github.animeliodas.bayun.R
import io.github.animeliodas.bayun.data.Language
import io.github.animeliodas.bayun.data.Library
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.data.objects
import io.github.animeliodas.bayun.engine.PolytomeEngine
import io.github.animeliodas.bayun.tts.ReadAloud
import io.github.animeliodas.bayun.tts.TtsCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class Provider(val id: String, val title: String, val local: Boolean, val keysUrl: String?,
                            val suggestStrong: String?, val suggestFast: String?)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Prefs.settings.collectAsState()
    var languages by remember { mutableStateOf<List<Language>>(emptyList()) }
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var engineVersion by remember { mutableStateOf("") }
    var key by remember(s.provider) { mutableStateOf(Prefs.apiKey(s.provider)) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<TtsCatalog.Catalog?>(null) }
    var pickModelFor by remember { mutableStateOf<String?>(null) }  // "strong" | "fast"
    val ra by ReadAloud.state.collectAsState()
    val books by Library.books.collectAsState()
    // языки, на которых Баюн будет читать вслух: язык чтения + языки книг (перевод и оригинал)
    val ttsLangs = remember(books, s.readingLang) {
        (listOf(s.effectiveReadingLang) + books.flatMap { b ->
            listOf(if (b.needsTranslation) b.targetLang else b.sourceLang, b.sourceLang)
        }).filter { it.isNotBlank() }.distinct()
    }
    LaunchedEffect(s.ttsEngine) {
        catalog = null
        catalog = TtsCatalog.load(context, s.ttsEngine)
    }

    LaunchedEffect(Unit) {
        runCatching {
            languages = Library.languages()
            providers = withContext(Dispatchers.IO) {
                (PolytomeEngine.call("providers") as? JSONArray ?: JSONArray()).objects().map {
                    val sug = it.optJSONObject("suggest") ?: JSONObject()
                    Provider(it.optString("id"), it.optString("title"), it.optBoolean("local"),
                        it.optString("keys_url").takeIf { u -> u.startsWith("http") },
                        sug.optString("strong").ifBlank { null }, sug.optString("fast").ifBlank { null })
                }
            }
            engineVersion = withContext(Dispatchers.IO) { PolytomeEngine.callObject("version").optString("polytome") }
        }
    }

    fun applyEngineConfig() = scope.launch(Dispatchers.IO) { PolytomeEngine.configure(Prefs.polytomeConfig()) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_look, R.string.section_read_aloud, R.string.section_translation, R.string.section_about)
    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t)) })
                }
            }
        }
    }) { padding ->
        val scroll = remember(tab) { ScrollState(0) }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> {
                    LookControls()
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.gestures_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                1 -> {
                    val engineLabel = catalog?.engines?.firstOrNull { it.name == catalog?.defaultEngine }?.label ?: "…"
                    Picker(stringResource(R.string.tts_engine),
                        catalog?.engines?.firstOrNull { it.name == s.ttsEngine }?.label
                            ?: stringResource(R.string.engine_default, engineLabel),
                        listOf("" to stringResource(R.string.engine_default, engineLabel)) +
                            catalog?.engines.orEmpty().map { it.name to it.label }) { id ->
                        Prefs.update { it.copy(ttsEngine = id) }
                        ReadAloud.onEngineChanged()
                    }
                    // движок не смог озвучить (нет голоса, облачный движок недоступен) — причина здесь же, не только в читалке
                    ra.error?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    if (catalog != null && s.ttsEngine.isNotBlank() && catalog!!.voices.isEmpty()) {
                        Text(stringResource(R.string.engine_no_voices), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (catalog == null) {
                        Text(stringResource(R.string.voices_loading), style = MaterialTheme.typography.bodySmall)
                    } else ttsLangs.forEach { lang ->
                        val voices = catalog!!.voices.filter { it.lang == lang }
                        val chosen = Prefs.voice(s.ttsEngine, lang)
                        var picked by remember(s.ttsEngine, lang) { mutableStateOf(chosen.orEmpty()) }
                        fun label(v: TtsCatalog.Voice) = TtsCatalog.shortName(v.name, context.getString(R.string.voice_main)) +
                            " · " + context.getString(if (v.local) R.string.voice_offline else R.string.voice_online)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                if (voices.isEmpty()) {
                                    Column {
                                        Text(stringResource(R.string.voice_for, TtsCatalog.languageName(lang)),
                                            style = MaterialTheme.typography.titleSmall)
                                        Text(stringResource(R.string.voices_none), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    Picker(stringResource(R.string.voice_for, TtsCatalog.languageName(lang)),
                                        voices.firstOrNull { it.name == picked }?.let(::label) ?: stringResource(R.string.voice_default),
                                        listOf("" to context.getString(R.string.voice_default)) + voices.map { it.name to label(it) }) { name ->
                                        picked = name
                                        Prefs.setVoice(s.ttsEngine, lang, name.ifBlank { null })
                                        ReadAloud.onVoiceChanged()
                                    }
                                }
                            }
                            OutlinedButton(onClick = { ReadAloud.preview(lang, TtsCatalog.sample(lang)) }) {
                                Text(stringResource(R.string.listen))
                            }
                        }
                    }
                    Text(stringResource(R.string.speed_x, s.ttsSpeed), style = MaterialTheme.typography.titleSmall)
                    Slider(s.ttsSpeed, { v -> Prefs.update { it.copy(ttsSpeed = v) } }, valueRange = 0.5f..3f,
                        onValueChangeFinished = { ReadAloud.onParamsChanged() })
                    Text(stringResource(R.string.pitch_x, s.ttsPitch), style = MaterialTheme.typography.titleSmall)
                    Slider(s.ttsPitch, { v -> Prefs.update { it.copy(ttsPitch = v) } }, valueRange = 0.5f..2f,
                        onValueChangeFinished = { ReadAloud.onParamsChanged() })
                    OutlinedButton(onClick = { openTtsSettings(context) }) { Text(stringResource(R.string.tts_settings)) }
                    Text(stringResource(R.string.tts_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                }
                2 -> {
                    val langLabel = if (s.readingLang.isBlank()) stringResource(R.string.phone_language)
                    else languages.firstOrNull { it.code == s.readingLang }?.let { "${it.native} (${it.name})" } ?: s.readingLang
                    Picker(stringResource(R.string.reading_language), langLabel,
                        listOf("" to stringResource(R.string.phone_language)) + languages.map { it.code to "${it.native} (${it.name})" }) { code ->
                        Prefs.update { it.copy(readingLang = code) }
                        applyEngineConfig()
                    }
                    Text(stringResource(R.string.reading_language_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Text(stringResource(R.string.translation_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val prov = providers.firstOrNull { it.id == s.provider }
                    Picker(stringResource(R.string.provider), prov?.title ?: s.provider, providers.map { it.id to it.title }) { id ->
                        Prefs.update { it.copy(provider = id) }
                        applyEngineConfig()
                    }
                    if (prov?.local != true) {
                        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.api_key)) },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { Prefs.setApiKey(s.provider, key.trim()); applyEngineConfig() }) {
                                Text(stringResource(R.string.save_key))
                            }
                            prov?.keysUrl?.let { url ->
                                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                                    Text(stringResource(R.string.get_key))
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = s.baseUrl, onValueChange = { v -> Prefs.update { it.copy(baseUrl = v) } },
                        label = { Text(stringResource(R.string.base_url)) }, placeholder = { Text("http://192.168.1.10:11434/v1") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    ModelField(s.strongModel, stringResource(R.string.model_strong), prov?.suggestStrong, s.provider, s.baseUrl,
                        onChange = { v -> Prefs.update { it.copy(strongModel = v) } }, onPick = { pickModelFor = "strong" })
                    ModelField(s.fastModel, stringResource(R.string.model_fast), prov?.suggestFast, s.provider, s.baseUrl,
                        onChange = { v -> Prefs.update { it.copy(fastModel = v) } }, onPick = { pickModelFor = "fast" })
                    if (s.strongModel.isBlank() && prov?.suggestStrong != null) {
                        TextButton(onClick = {
                            Prefs.update { it.copy(strongModel = prov.suggestStrong, fastModel = prov.suggestFast ?: prov.suggestStrong) }
                        }) { Text(stringResource(R.string.use_suggested, prov.suggestStrong)) }
                    }
                    Text(stringResource(R.string.profile), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("economy", "balanced", "quality", "local").forEach { p ->
                            FilterChip(selected = s.profile == p, onClick = { Prefs.update { it.copy(profile = p) }; applyEngineConfig() },
                                label = { Text(p) })
                        }
                    }
                    Button(enabled = !checking, onClick = {
                        checking = true
                        checkResult = null
                        scope.launch {
                            checkResult = withContext(Dispatchers.IO) {
                                runCatching {
                                    PolytomeEngine.configure(Prefs.polytomeConfig())
                                    val r = PolytomeEngine.callObject("check", JSONObject().put("ping", true))
                                    if (r.optBoolean("ready")) context.getString(R.string.check_ok)
                                    else {
                                        val roles = r.optJSONObject("roles") ?: JSONObject()
                                        roles.keys().asSequence().mapNotNull { k ->
                                            roles.optJSONObject(k)?.optString("error")?.takeIf { it.isNotBlank() }?.let { "$k: $it" }
                                        }.firstOrNull() ?: context.getString(R.string.translation_not_configured)
                                    }
                                }.getOrElse { it.message }
                            }
                            checking = false
                        }
                    }) { Text(stringResource(if (checking) R.string.checking else R.string.check_connection)) }
                    checkResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                    HorizontalDivider()
                    Section(stringResource(R.string.new_books))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.auto_meta), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = s.autoMeta, onCheckedChange = { v -> Prefs.update { it.copy(autoMeta = v) } })
                    }
                    Text(stringResource(R.string.auto_meta_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var firstN by remember { mutableStateOf(s.autoChapters.toString()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.auto_chapters), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(value = firstN, singleLine = true, modifier = Modifier.width(96.dp),
                            label = { Text(stringResource(R.string.chapters_field)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            onValueChange = { t ->
                                firstN = t.filter(Char::isDigit).take(3)
                                Prefs.update { it.copy(autoChapters = firstN.toIntOrNull() ?: 0) }
                            })
                    }
                    Text(stringResource(R.string.auto_chapters_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.default_mode), style = MaterialTheme.typography.titleSmall)
                    AheadModePicker(s.defaultAhead) { v -> Prefs.update { it.copy(defaultAhead = v) } }

                }
                else -> {
                    Text(stringResource(R.string.about_text, BuildConfigInfo.VERSION, engineVersion.ifBlank { "…" }),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    pickModelFor?.let { slot ->
        ModelPickerSheet(s.provider, s.baseUrl, if (slot == "strong") s.strongModel else s.fastModel,
            onPick = { id ->
                Prefs.update { if (slot == "strong") it.copy(strongModel = id) else it.copy(fastModel = id) }
                applyEngineConfig()
            },
            onDismiss = { pickModelFor = null })
    }
}

/** Поле модели: можно вписать вручную или выбрать из списка провайдера (кнопка справа); под полем — цена. */
@Composable
private fun ModelField(value: String, label: String, suggest: String?, provider: String, baseUrl: String,
                       onChange: (String) -> Unit, onPick: () -> Unit) {
    val info = ModelCatalog.cached(provider, baseUrl)?.firstOrNull { it.id == value.trim() }
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        placeholder = { Text(suggest.orEmpty()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onPick) { Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.choose_model)) }
        },
        supportingText = info?.let { m -> modelDetails(m).takeIf { it.isNotBlank() }?.let { { Text(it) } } })
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Picker(label: String, current: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(current, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (id, title) ->
                    DropdownMenuItem(text = { Text(title) }, onClick = { open = false; onPick(id) })
                }
            }
        }
    }
}
