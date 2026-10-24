package io.github.animeliodas.bayun.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.animeliodas.bayun.engine.PolytomeEngine
import io.github.animeliodas.bayun.translate.NightTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Книги, импорт и текст глав — всё через Polytome (папка книги = workspace Polytome). */
object Library {
    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books

    private val chapterCache = object : LinkedHashMap<String, ChapterText>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChapterText>?) = size > 12
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val arr = PolytomeEngine.call("books", JSONObject().put("library", PolytomeEngine.booksDir.absolutePath))
            as? JSONArray ?: JSONArray()
        _books.value = arr.objects().map(Book::from).sortedBy { it.title.lowercase() }
    }

    suspend fun book(path: String): Book = withContext(Dispatchers.IO) {
        Book.from(PolytomeEngine.callObject("book_info", JSONObject().put("book", path)))
    }

    /** Скопировать файл из системного выбора файлов и импортировать: EPUB, FB2, FB2.ZIP, TXT. */
    suspend fun import(context: Context, uri: Uri, targetLang: String): Book = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: "book.epub"
        val tmpDir = File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
        val tmp = File(tmpDir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        try {
            val info = PolytomeEngine.callObject("import_book", JSONObject()
                .put("source", tmp.absolutePath)
                .put("library", PolytomeEngine.booksDir.absolutePath)
                .put("target_lang", targetLang))
            refresh()
            Book.from(info)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        val dir = File(book.path)
        if (dir.canonicalPath.startsWith(PolytomeEngine.booksDir.canonicalPath)) dir.deleteRecursively()
        NightTranslation.cancel(book.path)
        Prefs.forget(book.path)
        synchronized(chapterCache) { chapterCache.keys.removeAll { it.startsWith(book.path + "#") } }
        refresh()
    }

    suspend fun chapters(book: String): List<ChapterInfo> = withContext(Dispatchers.IO) {
        (PolytomeEngine.call("chapters", JSONObject().put("book", book)) as? JSONArray ?: JSONArray())
            .objects().map { ChapterInfo(it.optInt("n"), it.optString("title"), it.optBoolean("translated")) }
    }

    suspend fun chapter(book: String, n: Int, fresh: Boolean = false): ChapterText = withContext(Dispatchers.IO) {
        val key = "$book#$n"
        if (!fresh) synchronized(chapterCache) { chapterCache[key] }?.let { return@withContext it }
        val o = PolytomeEngine.callObject("chapter", JSONObject().put("book", book).put("n", n))
        val tr = o.optJSONArray("translation")?.strings()
        val text = ChapterText(n, o.optJSONArray("source")?.strings() ?: emptyList(), tr, o.optBoolean("aligned"))
        synchronized(chapterCache) { chapterCache[key] = text }
        text
    }

    fun invalidate(book: String, n: Int? = null) = synchronized(chapterCache) {
        if (n == null) chapterCache.keys.removeAll { it.startsWith("$book#") } else chapterCache.remove("$book#$n")
    }

    suspend fun languages(): List<Language> = withContext(Dispatchers.IO) {
        (PolytomeEngine.call("languages") as? JSONArray ?: JSONArray()).objects()
            .map { Language(it.optString("code"), it.optString("name"), it.optString("native")) }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
