package com.appsc.mcq.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.time.LocalDate

/**
 * Answers, daily counts, targets and mock results.
 * Answers go to an append-only log (answers.log: id, correct, book, date per line; the latest line
 * for an id wins), so recording one answer never rewrites thousands of entries.
 */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("mcq", Context.MODE_PRIVATE)
    private val log = File(context.filesDir, "answers.log")

    /** question id -> answered correctly (latest attempt). Unscored questions are in [seen] instead. */
    var answers by mutableStateOf(emptyMap<String, Boolean>())
        private set
    /** question id -> subject, for questions that have been answered (to group mistakes). */
    var bookOf by mutableStateOf(emptyMap<String, Int>())
        private set
    var seen by mutableStateOf(emptySet<String>())
        private set
    /** date (yyyy-mm-dd) -> questions answered that day. */
    var daily by mutableStateOf(emptyMap<String, Int>())
        private set

    var pyqTarget by mutableIntStateOf(prefs.getInt(KEY_PYQ_TARGET, 100))
        private set
    var notesTarget by mutableIntStateOf(prefs.getInt(KEY_NOTES_TARGET, 60))
        private set
    var mockSize by mutableIntStateOf(prefs.getInt(KEY_MOCK_SIZE, 150))
        private set
    var mocks by mutableStateOf(decodeMocks(prefs.getStringSet(KEY_MOCKS, emptySet())!!))
        private set

    init {
        load()
    }

    private fun load() {
        if (!log.exists()) return
        val a = HashMap<String, Boolean>()
        val b = HashMap<String, Int>()
        val s = HashSet<String>()
        val d = HashMap<String, Int>()
        log.forEachLine { line ->
            val p = line.split('\t')
            if (p.size < 4) return@forEachLine
            when (p[1]) {
                "u" -> s += p[0]
                else -> a[p[0]] = p[1] == "1"
            }
            p[2].toIntOrNull()?.let { b[p[0]] = it }
            d[p[3]] = (d[p[3]] ?: 0) + 1
        }
        answers = a
        bookOf = b
        seen = s
        daily = d
    }

    private fun append(q: Question, mark: String) {
        val today = LocalDate.now().toString()
        log.appendText("${q.id}\t$mark\t${q.book}\t$today\n")
        bookOf = bookOf + (q.id to q.book)
        daily = daily + (today to (daily[today] ?: 0) + 1)
    }

    fun record(q: Question, picked: Int) {
        if (!q.scored) {
            if (q.id !in seen) seen = seen + q.id
            append(q, "u")
            return
        }
        val ok = picked == q.answer
        answers = answers + (q.id to ok)
        append(q, if (ok) "1" else "0")
    }

    fun attempted(id: String) = id in answers || id in seen

    /** (attempted, correct) among the given ids. */
    fun stats(ids: Collection<String>): Pair<Int, Int> {
        var attempted = 0
        var correct = 0
        for (id in ids) {
            val a = answers[id]
            if (a == null) {
                if (id in seen) attempted++
                continue
            }
            attempted++
            if (a) correct++
        }
        return attempted to correct
    }

    val wrongIds: List<String> get() = answers.filterValues { !it }.keys.toList()

    fun answeredToday(today: LocalDate = LocalDate.now()) = daily[today.toString()] ?: 0

    /** Consecutive days (ending today or yesterday) with at least one answer. */
    fun streak(today: LocalDate = LocalDate.now()): Int {
        var d = if ((daily[today.toString()] ?: 0) > 0) today else today.minusDays(1)
        var n = 0
        while ((daily[d.toString()] ?: 0) > 0) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun changePyqTarget(delta: Int) {
        pyqTarget = (pyqTarget + delta).coerceIn(10, 1000)
        prefs.edit().putInt(KEY_PYQ_TARGET, pyqTarget).apply()
    }

    fun changeNotesTarget(delta: Int) {
        notesTarget = (notesTarget + delta).coerceIn(10, 1000)
        prefs.edit().putInt(KEY_NOTES_TARGET, notesTarget).apply()
    }

    fun changeMockSize(delta: Int) {
        mockSize = (mockSize + delta).coerceIn(30, 200)
        prefs.edit().putInt(KEY_MOCK_SIZE, mockSize).apply()
    }

    fun addMock(r: MockResult) {
        mocks = listOf(r) + mocks
        prefs.edit().putStringSet(KEY_MOCKS, mocks.mapIndexed { i, m -> "$i\t${m.date}\t${m.day}\t${m.total}\t${m.correct}\t${m.wrong}" }.toSet()).apply()
    }

    private fun decodeMocks(raw: Set<String>): List<MockResult> =
        raw.mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 6) null
            else (p[0].toIntOrNull() ?: 0) to MockResult(p[1], p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0, p[5].toIntOrNull() ?: 0)
        }.sortedBy { it.first }.map { it.second }

    private companion object {
        const val KEY_PYQ_TARGET = "pyq_target"
        const val KEY_NOTES_TARGET = "notes_target"
        const val KEY_MOCK_SIZE = "mock_size"
        const val KEY_MOCKS = "mocks"
    }
}
