package com.appsc.mcq.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.time.LocalDate

/**
 * Question ids of every section, topic and plan day (assets/index.json). Small enough to keep in memory;
 * it answers every count and target without touching the questions themselves.
 */
class QIndex(
    private val rows: Map<String, Pair<List<String>, List<String>>>,
    private val days: Map<Int, Pair<List<String>, List<String>>>,
) {
    fun row(origin: Origin, book: Int, row: Int): List<String> =
        rows["$book:$row"]?.let { if (origin == Origin.PYQ) it.first else it.second } ?: emptyList()

    /** A plan day's pool: PYQs (APPSC first, newest first) or notes MCQs (notes order). */
    fun day(origin: Origin, n: Int): List<String> =
        days[n]?.let { if (origin == Origin.PYQ) it.first else it.second } ?: emptyList()

    private val bookIds = HashMap<String, List<String>>()

    /** Every question id of a subject from one source, each once. */
    fun book(origin: Origin, book: Int): List<String> = synchronized(bookIds) {
        bookIds.getOrPut(origin.code + book) {
            val seen = LinkedHashSet<String>()
            rows.keys.filter { it.startsWith("$book:") }.sortedBy { it.substringAfter(':').toInt() }
                .forEach { seen += row(origin, book, it.substringAfter(':').toInt()) }
            seen.toList()
        }
    }

    /** id -> (origin, book, row) of the first section that holds it. Built on first use. */
    private val where: Map<String, Triple<Origin, Int, Int>> by lazy {
        val m = HashMap<String, Triple<Origin, Int, Int>>(120_000)
        for ((key, v) in rows) {
            val b = key.substringBefore(':').toInt()
            val r = key.substringAfter(':').toInt()
            v.first.forEach { m.putIfAbsent(it, Triple(Origin.PYQ, b, r)) }
            v.second.forEach { m.putIfAbsent(it, Triple(Origin.NOTES, b, r)) }
        }
        m
    }

    fun locate(id: String): Triple<Origin, Int, Int>? = where[id]
}

/** Loads the plan, the catalogue, the id index and (on demand) the questions of single sections. */
class Repository(private val context: Context) {

    val plan: Plan by lazy { context.assets.open("plan.json").use { parsePlan(reader(it)) } }
    val catalog: List<BookInfo> by lazy { context.assets.open("catalog.json").use { parseCatalog(reader(it)) } }

    @Volatile
    private var idx: QIndex? = null
    private val indexLock = Mutex()

    /** The id index; call [load] first (the app does so before showing any screen). */
    val index: QIndex get() = idx ?: error("index not loaded")

    /** Loads everything the screens need synchronously: plan, catalogue and index. */
    suspend fun load() = indexLock.withLock {
        if (idx != null) return@withLock
        withContext(Dispatchers.IO) {
            plan
            catalog
            idx = context.assets.open("index.json").use { parseIndex(reader(it)) }
        }
    }

    // ---- questions (per section file, cached) ----

    private val cache = LruCache<String, List<Question>>(160)

    /** Questions of one section from one source. */
    suspend fun row(origin: Origin, book: Int, row: Int): List<Question> {
        val key = "${origin.code}$book/r$row"
        cache.get(key)?.let { return it }
        val qs = withContext(Dispatchers.IO) {
            if (index.row(origin, book, row).isEmpty()) emptyList()
            else runCatching { context.assets.open("q/$key.json").use { parseQuestions(reader(it), book, origin, row) } }.getOrDefault(emptyList())
        }
        cache.put(key, qs)
        return qs
    }

    /** The questions for [ids], in the same order; ids that cannot be found are skipped. */
    suspend fun questions(ids: List<String>): List<Question> {
        val byId = HashMap<String, Question>(ids.size * 2)
        val sections = ids.mapNotNull { index.locate(it) }.distinct()
        for ((o, b, r) in sections) row(o, b, r).forEach { byId.putIfAbsent(it.id, it) }
        return ids.mapNotNull { byId[it] }
    }

    fun dayFor(date: LocalDate): PlanDay {
        val days = plan.days
        return days.firstOrNull { it.date == date }
            ?: if (date.isBefore(days.first().date)) days.first() else days.last()
    }

    fun day(n: Int): PlanDay = plan.days.firstOrNull { it.n == n } ?: plan.days.first()

    fun rowInfo(book: Int, row: Int): RowInfo? = catalog.getOrNull(book - 1)?.rows?.getOrNull(row)

    /** Sections the plan has scheduled up to and including day [n]. */
    fun rowsStudiedBy(n: Int): List<PlanRow> = plan.days.filter { it.n <= n }.flatMap { it.rows }.distinctBy { it.book to it.row }

    // ---- parsing (streaming, so a large file never becomes a big in-memory tree) ----

    private fun reader(s: java.io.InputStream) = JsonReader(InputStreamReader(s, Charsets.UTF_8))

    private fun JsonReader.strings(): List<String> {
        val out = ArrayList<String>()
        beginArray()
        while (hasNext()) out += nextString()
        endArray()
        return out
    }

    private fun JsonReader.stringOrNull(): String? = if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()

    private fun parseQuestions(r: JsonReader, book: Int, origin: Origin, row: Int): List<Question> {
        val out = ArrayList<Question>()
        r.beginArray()
        while (r.hasNext()) {
            var id = ""; var s = ""; var o: List<String> = emptyList(); var a = -1; var src = ""; var ap = false
            var x = ""; var n: List<String> = emptyList(); var k = 's'; var cx = false; var t: List<List<String>> = emptyList()
            var ty = ""; var tq = ""; var sec = ""
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "id" -> id = r.nextString()
                    "s" -> s = r.nextString()
                    "o" -> o = r.strings()
                    "a" -> a = r.nextInt()
                    "src" -> src = r.stringOrNull() ?: ""
                    "ap" -> { r.skipValue(); ap = true }
                    "x" -> x = r.stringOrNull() ?: ""
                    "n" -> n = r.strings()
                    "k" -> k = r.nextString().firstOrNull() ?: 's'
                    "cx" -> { r.skipValue(); cx = true }
                    "t" -> {
                        val rows = ArrayList<List<String>>()
                        r.beginArray()
                        while (r.hasNext()) rows += r.strings()
                        r.endArray()
                        t = rows
                    }
                    "ty" -> ty = r.nextString()
                    "tq" -> tq = r.nextString()
                    "sec" -> sec = r.nextString()
                    else -> r.skipValue()
                }
            }
            r.endObject()
            if (id.isNotEmpty() && o.size >= 2) {
                out += Question(
                    id = id, stem = s, table = t, options = o, answer = if (a in o.indices) a else -1, source = src, appsc = ap,
                    explanation = x, notes = n, kind = k, cancelled = cx, book = book, origin = origin, row = row,
                    type = ty, technique = tq, section = sec,
                )
            }
        }
        r.endArray()
        return out
    }

    private fun parseIndex(r: JsonReader): QIndex {
        val rows = HashMap<String, Pair<List<String>, List<String>>>()
        val days = HashMap<Int, Pair<List<String>, List<String>>>()
        fun pair(): Pair<List<String>, List<String>> {
            r.beginArray()
            val p = r.strings()
            val n = r.strings()
            r.endArray()
            return p to n
        }
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "rows" -> { r.beginObject(); while (r.hasNext()) { val k = r.nextName(); rows[k] = pair() }; r.endObject() }
                "days" -> { r.beginObject(); while (r.hasNext()) { val k = r.nextName().toInt(); days[k] = pair() }; r.endObject() }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return QIndex(rows, days)
    }

    private fun parseCatalog(r: JsonReader): List<BookInfo> {
        val books = ArrayList<BookInfo>()
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() != "books") { r.skipValue(); continue }
            r.beginArray()
            while (r.hasNext()) {
                var id = 0; var title = ""; var short = ""
                val units = ArrayList<NoteUnit>()
                val rows = ArrayList<RowInfo>()
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "id" -> id = r.nextInt()
                        "title" -> title = r.nextString()
                        "short" -> short = r.nextString()
                        "units" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                var code = ""; var t = ""
                                r.beginObject()
                                while (r.hasNext()) when (r.nextName()) { "code" -> code = r.nextString(); "title" -> t = r.nextString(); else -> r.skipValue() }
                                r.endObject()
                                units += NoteUnit(code, t)
                            }
                            r.endArray()
                        }
                        "rows" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                var u = 0; var codes: List<String> = emptyList(); var t = ""; var tag = ""
                                r.beginObject()
                                while (r.hasNext()) when (r.nextName()) {
                                    "u" -> u = r.nextInt()
                                    "codes" -> codes = r.strings()
                                    "title" -> t = r.nextString()
                                    "tag" -> tag = r.stringOrNull() ?: ""
                                    else -> r.skipValue()
                                }
                                r.endObject()
                                rows += RowInfo(book = 0, index = rows.size, unitIndex = u, codes = codes, title = t, tag = tag)
                            }
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                books += BookInfo(id, title, short, units, rows.map { it.copy(book = id) })
            }
            r.endArray()
        }
        r.endObject()
        return books
    }

    private fun parsePlan(r: JsonReader): Plan {
        var start = ""; var exam = ""; var label = ""
        val days = ArrayList<PlanDay>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "start" -> start = r.nextString()
                "exam" -> exam = r.nextString()
                "examLabel" -> label = r.nextString()
                "days" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        var n = 0; var date = ""; var dow = ""; var phase = ""; var focus = ""; var left = ""; var type = ""
                        val rows = ArrayList<PlanRow>()
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "n" -> n = r.nextInt()
                                "date" -> date = r.nextString()
                                "dow" -> dow = r.nextString()
                                "phase" -> phase = r.stringOrNull() ?: ""
                                "focus" -> focus = r.stringOrNull() ?: ""
                                "left" -> left = r.stringOrNull() ?: ""
                                "type" -> type = r.stringOrNull() ?: ""
                                "rows" -> {
                                    r.beginArray()
                                    while (r.hasNext()) {
                                        var codes: List<String> = emptyList(); var topic = ""; var pri = ""; var ref: List<Int>? = null
                                        r.beginObject()
                                        while (r.hasNext()) when (r.nextName()) {
                                            "codes" -> codes = r.strings()
                                            "topic" -> topic = r.nextString()
                                            "pri" -> pri = r.stringOrNull() ?: ""
                                            "ref" -> {
                                                val l = ArrayList<Int>()
                                                r.beginArray(); while (r.hasNext()) l += r.nextInt(); r.endArray()
                                                ref = l
                                            }
                                            else -> r.skipValue()
                                        }
                                        r.endObject()
                                        ref?.takeIf { it.size == 2 }?.let { rows += PlanRow(codes, topic, pri, it[0], it[1]) }
                                    }
                                    r.endArray()
                                }
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        days += PlanDay(n, LocalDate.parse(date), dow, phase, focus, left, type, rows)
                    }
                    r.endArray()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Plan(LocalDate.parse(start), LocalDate.parse(exam), label, days)
    }
}
