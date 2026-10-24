package io.github.animeliodas.bayun.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.animeliodas.bayun.R
import io.github.animeliodas.bayun.data.Book
import io.github.animeliodas.bayun.data.Importer
import io.github.animeliodas.bayun.data.Library
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.translate.TranslationController
import io.github.animeliodas.bayun.translate.Translations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(pendingImport: Uri?, consumeImport: () -> Unit, onOpen: (Book) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by Library.books.collectAsState()
    val progress by Translations.progress.collectAsState()
    val settings by Prefs.settings.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var loading by remember { mutableStateOf(true) }
    val import by Importer.state.collectAsState()
    var importHidden by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Book?>(null) }
    var toTranslate by remember { mutableStateOf<Book?>(null) }
    var settingsFor by remember { mutableStateOf<Book?>(null) }
    val metaRunning by Translations.meta.collectAsState()

    fun showError(t: Throwable) = scope.launch { snackbar.showSnackbar(t.message ?: t.toString()) }

    fun importUri(uri: Uri) {
        importHidden = false
        Importer.start(context, uri, settings.effectiveReadingLang)
    }
    // импорт закончился (даже если окно скрывали) — открыть книгу или показать ошибку
    LaunchedEffect(import.result, import.error) {
        import.result?.let { Importer.consume(); onOpen(it) }
        import.error?.let { msg -> Importer.consume(); snackbar.showSnackbar(msg) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importUri) }

    LaunchedEffect(Unit) {
        runCatching { Library.refresh() }.onFailure { showError(it) }
        loading = false
    }
    LaunchedEffect(pendingImport) {
        pendingImport?.let { importUri(it); consumeImport() }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_book)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (import.running && importHidden) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            when {
                loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.starting_engine), style = MaterialTheme.typography.bodyMedium)
                }
                books.isEmpty() -> EmptyLibrary(Modifier.align(Alignment.Center))
                else -> LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(books, key = { it.path }) { book ->
                        BookCard(book, progress[book.path], metaRunning = book.path in metaRunning, onOpen = { onOpen(book) },
                            onTranslate = { toTranslate = book }, onTranslationSettings = { settingsFor = book },
                            onStop = { TranslationController.stop(book.path) },
                            onDelete = { toDelete = book })
                    }
                }
            }
        }
    }

    if (import.running && !importHidden) {
        AlertDialog(onDismissRequest = { importHidden = true },
            confirmButton = { TextButton(onClick = { importHidden = true }) { Text(stringResource(R.string.hide)) } },
            title = { Text(stringResource(R.string.importing)) },
            text = {
                Column {
                    val total = import.total
                    Text(when (import.stage) {
                        "parse" -> stringResource(R.string.import_parse, import.done, total)
                        "write" -> stringResource(R.string.import_write, import.done, total)
                        else -> stringResource(R.string.importing_hint)
                    })
                    Spacer(Modifier.height(12.dp))
                    if (total > 0) {
                        // разбор файла — первая половина полосы, запись глав — вторая
                        val part = import.done.toFloat() / total
                        LinearProgressIndicator(progress = { if (import.stage == "write") 0.5f + part / 2 else part / 2 },
                            modifier = Modifier.fillMaxWidth())
                    } else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.import_background_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
    }
    toTranslate?.let { book -> TranslateBookDialog(book.path) { toTranslate = null } }
    settingsFor?.let { book -> BookTranslationSheet(book.path, Prefs.position(book.path)?.chapter) { settingsFor = null } }
    toDelete?.let { book ->
        AlertDialog(onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_book_q)) },
            text = { Text(book.title) },
            confirmButton = { TextButton(onClick = {
                toDelete = null
                scope.launch { runCatching { Library.delete(book) }.onFailure { showError(it) } }
            }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_hint), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}


@Composable
private fun BookCard(book: Book, progress: Translations.Progress?, metaRunning: Boolean, onOpen: () -> Unit,
                     onTranslate: () -> Unit, onTranslationSettings: () -> Unit, onStop: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val running = progress?.running == true
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(12.dp)) {
            Cover(book.cover, book.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(book.author, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                val langs = if (book.needsTranslation) "${langName(book.sourceLang)} → ${langName(book.targetLang)}"
                else langName(book.sourceLang)
                Text(langs, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                val pos = Prefs.position(book.path)
                val status = buildString {
                    append(if (pos != null) stringResource(R.string.reading_chapter, pos.chapter, book.chaptersTotal)
                    else stringResource(R.string.chapters_n, book.chaptersTotal))
                    if (book.needsTranslation) append(" · ").append(stringResource(R.string.translated_n, book.chaptersTranslated))
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    if (progress.total > 0) {
                        LinearProgressIndicator(progress = { progress.done.toFloat() / progress.total }, Modifier.fillMaxWidth())
                        Text(stringResource(R.string.translation_progress, progress.done, progress.total),
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.translation_preparing), style = MaterialTheme.typography.bodySmall)
                    }
                } else if (metaRunning) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.meta_running), style = MaterialTheme.typography.bodySmall)
                } else if (progress?.pausedBySystem == true) {
                    Text(stringResource(R.string.translation_paused_by_system), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                } else progress?.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.read)) }, onClick = { menu = false; onOpen() })
                    if (book.needsTranslation) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.book_translation_menu)) },
                            onClick = { menu = false; onTranslationSettings() })
                    }
                    if (book.needsTranslation && !running && book.chaptersTranslated < book.chaptersTotal) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.translate_book)) },
                            onClick = { menu = false; onTranslate() })
                    }
                    if (running) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.stop_translation)) },
                            onClick = { menu = false; onStop() })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun Cover(path: String?, title: String, width: Int = 64) {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = path?.let { p -> withContext(Dispatchers.IO) { decodeSampled(p, width * 3) } }
    }
    val shape = RoundedCornerShape(6.dp)
    val mod = Modifier.width(width.dp).height((width * 1.5).dp).clip(shape)
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), title, mod, contentScale = ContentScale.Crop)
    } else {
        Box(mod.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(title.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

private fun decodeSampled(path: String, targetWidth: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
