package io.github.animeliodas.bayun.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.animeliodas.bayun.R
import io.github.animeliodas.bayun.data.Library
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.engine.PolytomeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Вызов Polytome; без ключа провайдера — понятное «перевод не настроен», а не «HTTP 401». */
private suspend fun polytome(context: Context, method: String, params: JSONObject): JSONObject = withContext(Dispatchers.IO) {
    PolytomeEngine.configure(Prefs.polytomeConfig())
    if (!PolytomeEngine.callObject("check").optBoolean("ready")) {
        throw IllegalStateException(context.getString(R.string.translation_not_configured))
    }
    PolytomeEngine.callObject(method, params)
}

@Composable
private fun Quote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(12.dp))
}

/** «Спросить ИИ» о фразе: слова, имена, термины, идиомы — с учётом глоссария и библии книги. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskSheet(book: String, chapter: Int, paragraph: Int, fragment: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.menu_ask), style = MaterialTheme.typography.titleLarge)
            Quote(fragment)
            OutlinedTextField(value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.ask_label)) },
                placeholder = { Text(stringResource(R.string.ask_hint)) })
            Button(enabled = !busy, onClick = {
                busy = true
                error = null
                scope.launch {
                    runCatching {
                        polytome(context, "explain", JSONObject().put("book", book).put("n", chapter).put("paragraph", paragraph)
                            .put("fragment", fragment).put("question", question.trim())).optString("answer")
                    }.onSuccess { answer = it }.onFailure { error = it.message ?: it.toString() }
                    busy = false
                }
            }) { Text(stringResource(R.string.ask_button)) }
            if (busy) {
                Text(stringResource(R.string.thinking), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            answer?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            Row(Modifier.padding(bottom = 16.dp)) {}
        }
    }
}

/**
 * «Исправить перевод» абзаца: модель предлагает вариант (по указанию читателя или сверив с оригиналом),
 * читатель может его поправить и сохранить. Прежний текст остаётся в истории главы.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixSheet(book: String, chapter: Int, paragraph: Int, source: String, current: String, onSaved: () -> Unit,
             onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var instruction by rememberSaveable { mutableStateOf("") }
    var proposal by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun suggest(mode: String) {
        busy = true
        error = null
        scope.launch {
            runCatching {
                polytome(context, "suggest_fix", JSONObject().put("book", book).put("n", chapter).put("paragraph", paragraph)
                    .put("instruction", instruction.trim()).put("mode", mode)).optString("new")
            }.onSuccess { proposal = it }.onFailure { error = it.message ?: it.toString() }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.menu_fix), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.original_label), style = MaterialTheme.typography.labelLarge)
            Quote(source)
            Text(stringResource(R.string.current_label), style = MaterialTheme.typography.labelLarge)
            Quote(current)
            OutlinedTextField(value = instruction, onValueChange = { instruction = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.fix_label)) },
                placeholder = { Text(stringResource(R.string.fix_hint)) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { suggest("fix") }) { Text(stringResource(R.string.fix_button)) }
                OutlinedButton(enabled = !busy, onClick = { suggest("retranslate") }) {
                    Text(stringResource(R.string.retranslate_button))
                }
            }
            if (busy) {
                Text(stringResource(R.string.thinking), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            proposal?.let { p ->
                OutlinedTextField(value = p, onValueChange = { proposal = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.fix_result)) })
                Button(enabled = !busy && p.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        runCatching {
                            polytome(context, "set_paragraph", JSONObject().put("book", book).put("n", chapter)
                                .put("paragraph", paragraph).put("text", p.trim()))
                            Library.invalidate(book, chapter)
                        }.onSuccess { onSaved() }.onFailure { error = it.message ?: it.toString() }
                        busy = false
                    }
                }) { Text(stringResource(R.string.save)) }
            }
            Row(Modifier.padding(bottom = 16.dp)) {}
        }
    }
}
