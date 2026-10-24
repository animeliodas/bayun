package io.github.animeliodas.bayun.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.animeliodas.bayun.R
import io.github.animeliodas.bayun.data.Book
import io.github.animeliodas.bayun.data.Bookmark
import io.github.animeliodas.bayun.data.ChapterInfo
import io.github.animeliodas.bayun.data.ChapterText
import io.github.animeliodas.bayun.data.Library
import io.github.animeliodas.bayun.data.Position
import io.github.animeliodas.bayun.data.Prefs
import io.github.animeliodas.bayun.data.TextMode
import io.github.animeliodas.bayun.translate.TranslationController
import io.github.animeliodas.bayun.translate.Translations
import io.github.animeliodas.bayun.tts.ReadAloud
import io.github.animeliodas.bayun.tts.splitSentences
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEEDS = listOf(0.8f, 1f, 1.2f, 1.4f, 1.6f, 1.8f, 2f, 2.5f)

/** Первая строка списка — карточка «глава не переведена» или пустое место: абзац i — элемент i + HEADER. */
private const val HEADER = 1

/** Место под пальцем при долгом нажатии: абзац, символ в нём, фраза; anchor — точка на экране читалки. */
private data class Target(val paragraph: Int, val offset: Int, val sentence: String, val anchor: Offset)

/** Направления меню жестов. */
private enum class Dir { UP, LEFT, RIGHT, DOWN }

private class MenuAction(val icon: ImageVector, val label: String, val enabled: Boolean, val run: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Prefs.settings.collectAsState()
    val ra by ReadAloud.state.collectAsState()
    val progress by Translations.progress.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val list = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    var book by remember { mutableStateOf<Book?>(null) }
    var toc by remember { mutableStateOf<List<ChapterInfo>>(emptyList()) }
    var chapter by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf<ChapterText?>(null) }
    var mode by remember { mutableStateOf(TextMode.TRANSLATION) }
    var startParagraph by remember { mutableIntStateOf(0) }
    var reloadTick by remember { mutableIntStateOf(0) }
    val expanded = remember { mutableStateListOf<Int>() }
    var showToc by remember { mutableStateOf(false) }
    var showLook by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var showTranslateBook by remember { mutableStateOf(false) }
    var showBookSheet by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var chrome by rememberSaveable { mutableStateOf(true) }  // панели видны; касание текста — спрятать/показать
    var target by remember { mutableStateOf<Target?>(null) }  // открыто меню жестов
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var askFor by remember { mutableStateOf<Target?>(null) }
    var fixFor by remember { mutableStateOf<Target?>(null) }
    var bookmarks by remember(path) { mutableStateOf(Prefs.bookmarks(path)) }
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(path) {
        runCatching {
            val b = Library.book(path)
            book = b
            toc = Library.chapters(path)
            val pos = Prefs.position(path)
            chapter = pos?.chapter?.takeIf { n -> toc.any { it.n == n } } ?: toc.firstOrNull()?.n ?: 1
            startParagraph = pos?.paragraph ?: 0
            mode = pos?.mode ?: if (b.needsTranslation) TextMode.TRANSLATION else TextMode.ORIGINAL
        }.onFailure { error = it.message }
    }
    // название и оглавление перевелись (или книга обновилась) — заголовки на языке читателя
    val books by Library.books.collectAsState()
    val metaRunning by Translations.meta.collectAsState()
    LaunchedEffect(books) { books.firstOrNull { it.path == path }?.let { if (book != null) book = it } }
    LaunchedEffect(path in metaRunning) {
        if (path !in metaRunning && book != null) toc = runCatching { Library.chapters(path) }.getOrDefault(toc)
    }
    LaunchedEffect(chapter, reloadTick, book != null) {
        if (book == null || chapter == 0) return@LaunchedEffect
        runCatching {
            text = Library.chapter(path, chapter, fresh = reloadTick > 0)
            expanded.clear()
            list.scrollToItem(if (startParagraph > 0) startParagraph + HEADER else 0)
            startParagraph = 0
        }.onFailure { error = it.message }
    }
    // опережение перевода: открыли главу — держать переведёнными следующие N
    LaunchedEffect(chapter, book?.needsTranslation) {
        if (book?.needsTranslation == true && chapter > 0) TranslationController.readAhead(path, chapter)
    }
    // перевод этой книги дошёл до открытой главы — перечитать
    val tr = progress[path]
    LaunchedEffect(tr?.done) {
        if (tr != null && text?.translation == null) {
            Library.invalidate(path, chapter)
            toc = Library.chapters(path)
            reloadTick++
        }
    }
    // чтение вслух перешло на следующую главу — читалка следует за ним
    LaunchedEffect(ra.chapter, ra.book) {
        if (ra.book == path && ra.playing && ra.chapter != chapter && ra.chapter != 0) {
            chapter = ra.chapter
        }
    }
    LaunchedEffect(ra.paragraph, ra.chapter) {
        if (ra.book == path && ra.chapter == chapter && ra.playing && ra.paragraph >= 0) {
            val visible = list.layoutInfo.visibleItemsInfo.map { it.index }
            if (ra.paragraph + HEADER !in visible.dropLast(1)) list.animateScrollToItem(ra.paragraph + HEADER)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { list.firstVisibleItemIndex }.debounce(800).collect { idx ->
            if (chapter > 0 && !(ReadAloud.state.value.playing && ReadAloud.state.value.book == path)) {
                Prefs.savePosition(path, Position(chapter, (idx - HEADER).coerceAtLeast(0), mode))
            }
        }
    }
    LaunchedEffect(ra.error) {
        val msg = ra.error ?: return@LaunchedEffect
        val res = snackbar.showSnackbar(msg, actionLabel = context.getString(R.string.tts_settings))
        if (res == SnackbarResult.ActionPerformed) openTtsSettings(context)
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); error = null } }
    // первый раз — подсказать про касание и долгое нажатие
    LaunchedEffect(Unit) {
        if (!Prefs.flag("reader_gestures")) {
            Prefs.setFlag("reader_gestures")
            snackbar.showSnackbar(context.getString(R.string.reader_hint))
        }
    }
    // панели спрятаны — прячем и системные (вернуть: свайп от края или касание текста)
    DisposableEffect(chrome) {
        val ctl = view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (chrome) ctl?.show(WindowInsetsCompat.Type.systemBars())
        else {
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler(enabled = target != null) { target = null }

    val t = text
    val showTranslation = mode == TextMode.TRANSLATION && t?.translation != null
    val paragraphs = (if (showTranslation) t.translation else t?.source).orEmpty()
    val alternate = when {
        t == null || t.translation == null || !t.aligned -> null
        showTranslation -> t.source
        else -> t.translation
    }
    val effectiveMode = if (showTranslation) TextMode.TRANSLATION else TextMode.ORIGINAL
    val chapterIdx = toc.indexOfFirst { it.n == chapter }
    val readingHere = ra.book == path && ra.chapter == chapter && ra.mode == effectiveMode
    val b = book

    fun readFrom(paragraph: Int, offset: Int = 0) {
        b ?: return
        ReadAloud.open(b, chapter, paragraph, mode, autoplay = true, offset = offset)
    }

    fun goChapter(delta: Int) {
        val next = toc.getOrNull(chapterIdx + delta) ?: return
        chapter = next.n
        startParagraph = 0
    }

    fun translate(chapters: List<Int>) {
        if (chapters.isNotEmpty()) TranslationController.start(path, chapters)
    }

    fun isBookmarked(paragraph: Int) = bookmarks.any { it.chapter == chapter && it.paragraph == paragraph }

    fun toggleBookmark(paragraph: Int) {
        val existing = bookmarks.firstOrNull { it.chapter == chapter && it.paragraph == paragraph }
        if (existing != null) Prefs.removeBookmark(path, existing)
        else Prefs.addBookmark(path, Bookmark(chapter, paragraph, paragraphs.getOrNull(paragraph).orEmpty().take(160),
            toc.getOrNull(chapterIdx)?.title.orEmpty(), System.currentTimeMillis()))
        bookmarks = Prefs.bookmarks(path)
        scope.launch {
            snackbar.showSnackbar(context.getString(if (existing != null) R.string.bookmark_removed else R.string.bookmark_added))
        }
    }

    /** Действия меню жестов для места tg: ↑ читать вслух, ← закладка, → спросить ИИ, ↓ исправить перевод. */
    fun actionsFor(tg: Target): Map<Dir, MenuAction> = mapOf(
        Dir.UP to MenuAction(Icons.Default.PlayArrow, context.getString(R.string.menu_read_here), true) {
            readFrom(tg.paragraph, tg.offset)
        },
        Dir.LEFT to (if (isBookmarked(tg.paragraph))
            MenuAction(Icons.Default.BookmarkRemove, context.getString(R.string.menu_unbookmark), true) { toggleBookmark(tg.paragraph) }
        else MenuAction(Icons.Default.BookmarkAdd, context.getString(R.string.menu_bookmark), true) { toggleBookmark(tg.paragraph) }),
        Dir.RIGHT to MenuAction(Icons.Default.Psychology, context.getString(R.string.menu_ask), true) { askFor = tg },
        Dir.DOWN to MenuAction(Icons.Default.AutoFixHigh, context.getString(R.string.menu_fix),
            showTranslation && t?.aligned == true && b?.needsTranslation == true) { fixFor = tg },
    )

    fun centerFor(tg: Target): MenuAction? = alternate?.let {
        MenuAction(Icons.Default.Translate, context.getString(if (showTranslation) R.string.menu_original else R.string.menu_translation),
            true) { if (tg.paragraph in expanded) expanded.remove(tg.paragraph) else expanded.add(tg.paragraph) }
    }

    val dragThreshold = with(LocalDensity.current) { 36.dp.toPx() }
    fun dirOf(d: Offset): Dir? = when {
        d.getDistance() < dragThreshold -> null
        abs(d.x) > abs(d.y) -> if (d.x > 0) Dir.RIGHT else Dir.LEFT
        else -> if (d.y > 0) Dir.DOWN else Dir.UP
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBarsIgnoringVisibility,
        topBar = {
            AnimatedVisibility(chrome, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(b?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium)
                                Text(toc.getOrNull(chapterIdx)?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                        actions = {
                            if (b?.needsTranslation == true) {
                                IconButton(onClick = {
                                    mode = if (mode == TextMode.TRANSLATION) TextMode.ORIGINAL else TextMode.TRANSLATION
                                    expanded.clear()
                                }) {
                                    Icon(Icons.Default.Translate, stringResource(
                                        if (mode == TextMode.TRANSLATION) R.string.show_original else R.string.show_translation),
                                        tint = if (showTranslation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { showToc = true }) { Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.contents)) }
                            IconButton(onClick = { showLook = true }) { Icon(Icons.Default.TextFields, stringResource(R.string.appearance)) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    if (b?.needsTranslation == true) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.book_translation_menu)) },
                                            onClick = { menu = false; showBookSheet = true })
                                    }
                                    if (b?.needsTranslation == true && tr?.running != true && b.chaptersTranslated < b.chaptersTotal) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.translate_book)) },
                                            onClick = { menu = false; showTranslateBook = true })
                                    }
                                    if (tr?.running == true) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.stop_translation)) },
                                            onClick = { menu = false; TranslationController.stop(path) })
                                    }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.tts_settings)) },
                                        onClick = { menu = false; openTtsSettings(context) })
                                }
                            }
                        },
                    )
                    if (tr?.running == true) {
                        if (tr.total > 0) LinearProgressIndicator(progress = { tr.done.toFloat() / tr.total }, Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(chrome, enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()) {
                BottomAppBar {
                    IconButton(onClick = { if (readingHere || ra.book == path) ReadAloud.prevChapter() else goChapter(-1) },
                        enabled = chapterIdx > 0) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.prev_chapter)) }
                    FilledIconButton(onClick = {
                        if (ra.book == path && ra.chapter == chapter) Playback.toggle()
                        else readFrom((list.firstVisibleItemIndex - HEADER).coerceAtLeast(0))
                    }) {
                        if (ra.loading && ra.book == path) CircularProgressIndicator(Modifier.padding(6.dp), strokeWidth = 2.dp)
                        else Icon(if (ra.playing && ra.book == path) Icons.Default.Pause else Icons.Default.PlayArrow,
                            stringResource(R.string.read_aloud))
                    }
                    IconButton(onClick = { if (ra.book == path) ReadAloud.nextChapter() else goChapter(+1) },
                        enabled = chapterIdx in 0 until toc.lastIndex) { Icon(Icons.Default.SkipNext, stringResource(R.string.next_chapter)) }
                    Spacer(Modifier.weight(1f))
                    AssistChip(onClick = {
                        val i = SPEEDS.indexOfFirst { it >= settings.ttsSpeed - 0.01f }
                        val next = SPEEDS[(i + 1).mod(SPEEDS.size)]
                        Prefs.update { it.copy(ttsSpeed = next) }
                        ReadAloud.onParamsChanged()
                    }, label = { Text("×" + "%.1f".format(settings.ttsSpeed)) })
                    Box {
                        IconButton(onClick = { sleepMenu = true }) {
                            Icon(Icons.Default.Bedtime, stringResource(R.string.sleep_timer),
                                tint = if (ra.sleepUntil > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                            listOf(15, 30, 45, 60, 90).forEach { m ->
                                DropdownMenuItem(text = { Text(stringResource(R.string.minutes_n, m)) },
                                    onClick = { sleepMenu = false; ReadAloud.setSleepTimer(m) })
                            }
                            if (ra.sleepUntil > 0) DropdownMenuItem(text = { Text(stringResource(R.string.sleep_off)) },
                                onClick = { sleepMenu = false; ReadAloud.setSleepTimer(0) })
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).onGloballyPositioned { boxCoords = it }) {
            if (t == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            val mark = MaterialTheme.colorScheme.primary
            LazyColumn(state = list,
                // касание — спрятать или показать панели (кнопки и долгое нажатие касание «съедают»)
                modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { chrome = !chrome }) },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                item(key = "header") {
                    if (mode == TextMode.TRANSLATION && t.translation == null && b?.needsTranslation == true) {
                        NotTranslatedCard(running = tr?.running == true,
                            waiting = ra.book == path && ra.chapter == chapter && ra.waitingTranslation && ra.playing,
                            onTranslateChapter = { translate(listOf(chapter)) },
                            onSettings = { showBookSheet = true })
                    } else {
                        Spacer(Modifier.height(0.dp))
                    }
                }
                itemsIndexed(paragraphs) { i, p ->
                    val isCurrent = readingHere && ra.paragraph == i
                    val annotated = buildAnnotatedString {
                        append(p)
                        if (isCurrent && ra.end <= p.length && ra.start < ra.end) {
                            addStyle(SpanStyle(background = highlight), ra.start, ra.end)
                        }
                    }
                    Paragraph(
                        text = annotated,
                        style = if (i == 0) MaterialTheme.typography.titleLarge.copy(fontSize = (settings.fontSize * 1.25f).sp)
                        else MaterialTheme.typography.bodyLarge,
                        fontSize = settings.fontSize.sp,
                        bookmarked = isBookmarked(i),
                        mark = mark,
                        alternate = if (i in expanded) alternate?.getOrNull(i) else null,
                        onLongPress = { offset, local, coords ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val anchor = boxCoords?.let { box -> coords?.let { box.localPositionOf(it, local) } } ?: local
                            val sentence = splitSentences(listOf(p)).firstOrNull { it.end > offset }?.text ?: p
                            drag = Offset.Zero
                            dragging = true
                            target = Target(i, offset, sentence.trim(), anchor)
                        },
                        onDrag = { drag = it },
                        onRelease = {
                            val tg = target
                            val d = dirOf(drag)
                            val action = if (tg != null && d != null) actionsFor(tg)[d] else null
                            if (action?.enabled == true) {
                                target = null
                                action.run()
                            } else dragging = false  // отпустили на месте — меню остаётся, можно нажать пункт
                        },
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = { goChapter(-1); scope.launch { list.scrollToItem(0) } },
                            enabled = chapterIdx > 0) { Text(stringResource(R.string.prev_chapter)) }
                        OutlinedButton(onClick = { goChapter(+1); scope.launch { list.scrollToItem(0) } },
                            enabled = chapterIdx in 0 until toc.lastIndex) { Text(stringResource(R.string.next_chapter)) }
                    }
                }
            }
            target?.let { tg ->
                GestureMenu(anchor = tg.anchor, selected = if (dragging) dirOf(drag) else null,
                    actions = actionsFor(tg), center = centerFor(tg),
                    onRun = { a -> target = null; a.run() }, onDismiss = { target = null })
            }
        }
    }

    if (showToc) {
        ModalBottomSheet(onDismissRequest = { showToc = false }) {
            var tocTab by rememberSaveable { mutableIntStateOf(0) }
            PrimaryTabRow(selectedTabIndex = tocTab) {
                Tab(selected = tocTab == 0, onClick = { tocTab = 0 }, text = { Text(stringResource(R.string.contents)) })
                Tab(selected = tocTab == 1, onClick = { tocTab = 1 },
                    text = { Text(stringResource(R.string.bookmarks) + if (bookmarks.isNotEmpty()) " · ${bookmarks.size}" else "") })
            }
            if (tocTab == 0) {
                val tocState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, chapterIdx - 3))
                LazyColumn(state = tocState, modifier = Modifier.navigationBarsPadding()) {
                    itemsIndexed(toc) { _, c ->
                        ListItem(
                            headlineContent = { Text(c.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = if (c.n == chapter) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                            trailingContent = { if (c.translated && b?.needsTranslation == true) Icon(Icons.Default.Check, null) },
                            modifier = Modifier.clickable {
                                showToc = false
                                chapter = c.n
                                startParagraph = 0
                            },
                        )
                        HorizontalDivider()
                    }
                }
            } else if (bookmarks.isEmpty()) {
                Text(stringResource(R.string.bookmarks_empty), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp).navigationBarsPadding())
            } else {
                LazyColumn(modifier = Modifier.navigationBarsPadding()) {
                    items(bookmarks, key = { "${it.chapter}:${it.paragraph}" }) { bm ->
                        ListItem(
                            leadingContent = { Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text(bm.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(bm.chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                IconButton(onClick = {
                                    Prefs.removeBookmark(path, bm)
                                    bookmarks = Prefs.bookmarks(path)
                                }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            },
                            modifier = Modifier.clickable {
                                showToc = false
                                if (bm.chapter == chapter) scope.launch { list.scrollToItem(bm.paragraph + HEADER) }
                                else {
                                    startParagraph = bm.paragraph
                                    chapter = bm.chapter
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (showTranslateBook) TranslateBookDialog(path) { showTranslateBook = false }
    if (showBookSheet) BookTranslationSheet(path, chapter) { showBookSheet = false }
    askFor?.let { tg -> AskSheet(path, chapter, tg.paragraph, tg.sentence) { askFor = null } }
    fixFor?.let { tg ->
        FixSheet(path, chapter, tg.paragraph, source = t?.source?.getOrNull(tg.paragraph).orEmpty(),
            current = t?.translation?.getOrNull(tg.paragraph).orEmpty(),
            onSaved = {
                fixFor = null
                startParagraph = tg.paragraph
                reloadTick++
            },
            onDismiss = { fixFor = null })
    }
    if (showLook) {
        ModalBottomSheet(onDismissRequest = { showLook = false }) {
            Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LookControls()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Абзац: долгое нажатие открывает меню жестов (и ведёт палец по нему), закладка — полоска слева. */
@Composable
private fun Paragraph(text: AnnotatedString, style: TextStyle, fontSize: TextUnit, bookmarked: Boolean, mark: Color,
                      alternate: String?, onLongPress: (offset: Int, local: Offset, coords: LayoutCoordinates?) -> Unit,
                      onDrag: (Offset) -> Unit, onRelease: () -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val pad = with(LocalDensity.current) { 6.dp.toPx() }
    val longPress by rememberUpdatedState(onLongPress)
    val dragTo by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    Column(Modifier
        .fillMaxWidth()
        .onGloballyPositioned { coords = it }
        .pointerInput(Unit) {
            detectLongPressDrag(
                onStart = { pos ->
                    val offset = layout?.getOffsetForPosition(pos - Offset(0f, pad)) ?: 0
                    longPress(offset, pos, coords)
                },
                onDrag = { dragTo(it) },
                onEnd = { release() })
        }
        .drawBehind {
            if (bookmarked) drawRect(mark, topLeft = Offset(-12.dp.toPx(), pad), size = Size(3.dp.toPx(), size.height - 2 * pad))
        }
        .padding(vertical = 6.dp)) {
        Text(text, fontSize = fontSize, lineHeight = fontSize * 1.5f, style = style, onTextLayout = { layout = it })
        alternate?.let { alt ->
            Text(alt, fontSize = fontSize * 0.88f, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(8.dp))
        }
    }
}

/**
 * Долгое нажатие и перетаскивание: до срабатывания ничего не перехватывает (список прокручивается, касание
 * доходит до списка), после — ведёт палец и не даёт списку прокручиваться.
 */
private suspend fun PointerInputScope.detectLongPressDrag(onStart: (Offset) -> Unit, onDrag: (Offset) -> Unit,
                                                          onEnd: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitUpOrMove(down.id, down.position, viewConfiguration.touchSlop)
        }
        if (endedEarly != null) return@awaitEachGesture
        onStart(down.position)
        while (true) {
            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            c.consume()
            if (!c.pressed) break
            onDrag(c.position - down.position)
        }
        onEnd()
    }
}

private suspend fun AwaitPointerEventScope.waitUpOrMove(id: PointerId, start: Offset, slop: Float): Boolean {
    while (true) {
        val c = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return true
        if (!c.pressed || c.isConsumed || (c.position - start).getDistance() > slop) return true
    }
}

/** Крестовина действий вокруг пальца: потянуть в сторону и отпустить — или нажать пункт. */
@Composable
private fun GestureMenu(anchor: Offset, selected: Dir?, actions: Map<Dir, MenuAction>, center: MenuAction?,
                        onRun: (MenuAction) -> Unit, onDismiss: () -> Unit) {
    val dirs = actions.keys.toList()
    val gap = with(LocalDensity.current) { 30.dp.toPx() }
    val margin = with(LocalDensity.current) { 8.dp.toPx() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))
        .pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Layout(modifier = Modifier.fillMaxSize(), content = {
            dirs.forEach { d ->
                val a = actions.getValue(d)
                Surface(onClick = { if (a.enabled) onRun(a) }, enabled = a.enabled, shape = RoundedCornerShape(24.dp),
                    color = if (selected == d && a.enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 6.dp, modifier = Modifier.alpha(if (a.enabled) 1f else 0.45f)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(a.icon, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(a.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Surface(onClick = { center?.let(onRun) }, enabled = center != null, shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 6.dp, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (center != null) Icon(center.icon, center.label)
                    else Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
        }) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val ps = measurables.map { it.measure(loose) }
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            fun size(d: Dir) = ps[dirs.indexOf(d)].let { it.width.toFloat() to it.height.toFloat() }
            val half = ps.last().width / 2f
            val left = if (Dir.LEFT in dirs) size(Dir.LEFT).first + gap else half
            val right = if (Dir.RIGHT in dirs) size(Dir.RIGHT).first + gap else half
            val up = if (Dir.UP in dirs) size(Dir.UP).second + gap else half
            val down = if (Dir.DOWN in dirs) size(Dir.DOWN).second + gap else half
            val cx = anchor.x.coerceIn(left + margin, maxOf(left + margin, w - right - margin))
            val cy = anchor.y.coerceIn(up + margin, maxOf(up + margin, h - down - margin))
            layout(constraints.maxWidth, constraints.maxHeight) {
                ps.forEachIndexed { k, p ->
                    val (x, y) = when (dirs.getOrNull(k)) {
                        Dir.UP -> cx - p.width / 2f to cy - gap - p.height
                        Dir.DOWN -> cx - p.width / 2f to cy + gap
                        Dir.LEFT -> cx - gap - p.width to cy - p.height / 2f
                        Dir.RIGHT -> cx + gap to cy - p.height / 2f
                        null -> cx - p.width / 2f to cy - p.height / 2f
                    }
                    p.place(x.roundToInt(), y.roundToInt())
                }
            }
        }
    }
}

@Composable
private fun NotTranslatedCard(running: Boolean, waiting: Boolean, onTranslateChapter: () -> Unit,
                              onSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(when {
                waiting -> R.string.waiting_translation
                running -> R.string.chapter_translating
                else -> R.string.chapter_not_translated
            }), style = MaterialTheme.typography.bodyMedium)
            if (waiting) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (!running) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onTranslateChapter, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.translate_chapter))
                }
                OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.book_translation_menu))
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun openTtsSettings(context: Context) {
    try {
        context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
