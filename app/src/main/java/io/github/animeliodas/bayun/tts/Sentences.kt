package io.github.animeliodas.bayun.tts

/** Фраза для синтеза речи: абзац главы и диапазон символов внутри него (для подсветки). */
data class Sentence(val paragraph: Int, val start: Int, val end: Int, val text: String)

private val END = Regex("""(?<=[.!?…。！？])["»”’)\]」』]*\s+|(?<=[。！？])["»”’」』]*""")
private const val MAX_LEN = 350

/** Разбить главу на фразы: по концу предложения, слишком длинные — ещё по запятым/точкам с запятой. */
fun splitSentences(paragraphs: List<String>): List<Sentence> {
    val out = ArrayList<Sentence>()
    paragraphs.forEachIndexed { pi, para ->
        var start = 0
        val cuts = END.findAll(para).map { it.range.last + 1 }.filter { it in 1 until para.length }.toMutableList()
        cuts.add(para.length)
        for (cut in cuts) {
            if (cut <= start) continue
            addPiece(out, pi, para, start, cut)
            start = cut
        }
    }
    return out
}

private fun addPiece(out: MutableList<Sentence>, pi: Int, para: String, start: Int, end: Int) {
    var s = start
    while (end - s > MAX_LEN) {
        val window = para.substring(s, s + MAX_LEN)
        val cut = maxOf(window.lastIndexOf(", "), window.lastIndexOf("; "), window.lastIndexOf("，"), window.lastIndexOf(" "))
        val e = if (cut > MAX_LEN / 3) s + cut + 1 else s + MAX_LEN
        emit(out, pi, para, s, e)
        s = e
    }
    emit(out, pi, para, s, end)
}

private fun emit(out: MutableList<Sentence>, pi: Int, para: String, start: Int, end: Int) {
    val text = para.substring(start, end)
    if (text.isBlank()) return
    val lead = text.length - text.trimStart().length
    val trail = text.length - text.trimEnd().length
    out.add(Sentence(pi, start + lead, end - trail, text.trim()))
}
