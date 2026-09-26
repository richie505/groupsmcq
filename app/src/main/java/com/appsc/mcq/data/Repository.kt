package com.appsc.mcq.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** Loads the bundled question banks and the 90-day plan from assets. Banks are parsed lazily and cached. */
class Repository(private val context: Context) {

    val plan: Plan by lazy { parsePlan(readJson("plan.json")) }
    val catalog: List<BookInfo> by lazy { parseCatalog(readJson("catalog.json")) }

    private val banks = HashMap<String, Bank>()
    private val mutex = Mutex()

    suspend fun bank(origin: Origin, book: Int): Bank = mutex.withLock {
        val key = origin.code + book
        banks[key] ?: withContext(Dispatchers.IO) {
            val name = if (origin == Origin.PYQ) "pyq$book.json" else "notes$book.json"
            parseBank(readJson(name), book, origin)
        }.also { banks[key] = it }
    }

    suspend fun rowQuestions(origin: Origin, book: Int, row: Int): List<Question> = bank(origin, book).rows[row] ?: emptyList()

    suspend fun unitQuestions(book: Int, unit: Int): List<Question> = bank(Origin.PYQ, book).units[unit] ?: emptyList()

    /** Every question of a source across the six subjects, each id once. */
    suspend fun all(origin: Origin): List<Question> {
        val seen = HashSet<String>()
        return (1..6).flatMap { b ->
            val bk = bank(origin, b)
            bk.rows.toSortedMap().values.flatten() + bk.units.toSortedMap().values.flatten()
        }.filter { seen.add(it.id) }
    }

    suspend fun byId(): Map<String, Question> = byIdCache ?: (all(Origin.PYQ) + all(Origin.NOTES)).associateBy { it.id }.also { byIdCache = it }

    private var byIdCache: Map<String, Question>? = null

    /**
     * All questions of a plan day's sections, each once, in study order.
     * PYQs: APPSC papers first, then the most recent years. Notes MCQs: in the order of the notes.
     */
    suspend fun dayPool(day: PlanDay, origin: Origin): List<Question> {
        val seen = HashSet<String>()
        val qs = day.rows.flatMap { rowQuestions(origin, it.book, it.row) }.filter { seen.add(it.id) }
        return if (origin == Origin.PYQ) qs.sortedWith(compareBy<Question> { if (it.appsc) 0 else 1 }.thenByDescending { it.year }) else qs
    }

    /** A day's target: the first [pyqTarget] / [notesTarget] questions of its pools. Stable for the day. */
    suspend fun dayTarget(day: PlanDay, pyqTarget: Int, notesTarget: Int): DayTarget {
        val key = Triple(day.n, pyqTarget, notesTarget)
        targetCache[key]?.let { return it }
        val t = DayTarget(
            pyq = dayPool(day, Origin.PYQ).take(pyqTarget).map { it.id },
            notes = dayPool(day, Origin.NOTES).take(notesTarget).map { it.id },
        )
        targetCache[key] = t
        return t
    }

    private val targetCache = HashMap<Triple<Int, Int, Int>, DayTarget>()

    /** Plan day for a date (clamped to the plan). */
    fun dayFor(date: LocalDate): PlanDay {
        val days = plan.days
        return days.firstOrNull { it.date == date }
            ?: if (date.isBefore(days.first().date)) days.first() else days.last()
    }

    fun rowInfo(book: Int, row: Int): RowInfo? = catalog.getOrNull(book - 1)?.rows?.getOrNull(row)

    /** Sections the plan has scheduled up to and including day [n]. */
    fun rowsStudiedBy(n: Int): List<PlanRow> = plan.days.filter { it.n <= n }.flatMap { it.rows }.distinctBy { it.book to it.row }

    private fun readJson(name: String): JsonElement =
        context.assets.open(name).bufferedReader().use { Json.parseToJsonElement(it.readText()) }

    // ---- parsing ----

    private fun JsonElement.str(key: String): String = (this.jsonObject[key] as? JsonPrimitive)?.content ?: ""

    private fun JsonElement.intOr(key: String, def: Int = 0): Int =
        (this.jsonObject[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: def

    private fun JsonElement.strList(key: String): List<String> =
        (this.jsonObject[key] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

    private fun parseBank(root: JsonElement, book: Int, origin: Origin): Bank {
        fun list(e: JsonElement): List<Question> = e.jsonArray.map { q ->
            val o = q.jsonObject
            Question(
                id = q.str("id"),
                stem = q.str("s"),
                table = (o["t"] as? JsonArray)?.map { r -> r.jsonArray.map { it.jsonPrimitive.content } } ?: emptyList(),
                options = q.strList("o"),
                answer = q.intOr("a", -1),
                source = q.str("src"),
                appsc = o.containsKey("ap"),
                explanation = q.str("x"),
                notes = q.strList("n"),
                kind = (o["k"] as? JsonPrimitive)?.content?.firstOrNull() ?: 's',
                cancelled = o.containsKey("cx"),
                book = book,
                origin = origin,
                type = q.str("ty"),
                technique = q.str("tq"),
                section = q.str("sec"),
            )
        }
        fun map(key: String): Map<Int, List<Question>> =
            (root.jsonObject[key] as? JsonObject)?.entries?.associate { (k, v) -> k.toInt() to list(v) } ?: emptyMap()
        return Bank(map("rows"), map("units"))
    }

    private fun parseCatalog(root: JsonElement): List<BookInfo> =
        root.jsonObject["books"]!!.jsonArray.map { b ->
            val id = b.intOr("id")
            BookInfo(
                id = id, title = b.str("title"), short = b.str("short"),
                units = b.jsonObject["units"]!!.jsonArray.map { NoteUnit(it.str("code"), it.str("title")) },
                rows = b.jsonObject["rows"]!!.jsonArray.mapIndexed { i, r ->
                    RowInfo(book = id, index = i, unitIndex = r.intOr("u"), codes = r.strList("codes"), title = r.str("title"), tag = r.str("tag"))
                },
            )
        }

    private fun parsePlan(root: JsonElement): Plan {
        val days = root.jsonObject["days"]!!.jsonArray.map { d ->
            PlanDay(
                n = d.intOr("n"),
                date = LocalDate.parse(d.str("date")),
                dow = d.str("dow"),
                phase = d.str("phase"),
                focus = d.str("focus"),
                left = d.str("left"),
                type = d.str("type"),
                rows = d.jsonObject["rows"]!!.jsonArray.mapNotNull { r ->
                    val ref = r.jsonObject["ref"] as? JsonArray ?: return@mapNotNull null
                    PlanRow(
                        codes = r.strList("codes"), topic = r.str("topic"), priority = r.str("pri"),
                        book = ref[0].jsonPrimitive.int, row = ref[1].jsonPrimitive.int,
                    )
                },
            )
        }
        return Plan(
            start = LocalDate.parse(root.str("start")),
            exam = LocalDate.parse(root.str("exam")),
            examLabel = root.str("examLabel"),
            days = days,
        )
    }
}
