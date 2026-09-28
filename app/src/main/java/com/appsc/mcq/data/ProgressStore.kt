package com.appsc.mcq.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Answers, daily counts, targets and mock results.
 *
 * Answers live in plain hash maps (so recording one is O(1), never a copy of 90,000 entries) and are
 * appended to answers.log on a background thread: "id \t 1|0|u \t book \t date" per line, the latest
 * line for an id wins. Composables observe changes through [rev]: every reader function reads it, so
 * a screen recomposes after an answer without any list being rebuilt.
 */
class ProgressStore(dir: File, private val prefs: KeyValueStore) {
    private val log = File(dir, "answers.log")
    private val io = Executors.newSingleThreadExecutor()

    private val answers = HashMap<String, Boolean>()
    private val seen = HashSet<String>()
    private val daily = HashMap<String, Int>()
    /** Bookmarked question ids, newest first. */
    private val saved = LinkedHashSet<String>()
    private val savedFile = File(dir, "saved.txt")

    /** Bumped on every change; read by all getters so Compose tracks them. */
    private var rev by mutableIntStateOf(0)

    var pyqTarget by mutableIntStateOf(prefs.getInt(KEY_PYQ_TARGET, 100))
        private set
    var notesTarget by mutableIntStateOf(prefs.getInt(KEY_NOTES_TARGET, 60))
        private set
    var caTarget by mutableIntStateOf(prefs.getInt(KEY_CA_TARGET, 40))
        private set
    var mockSize by mutableIntStateOf(prefs.getInt(KEY_MOCK_SIZE, 150))
        private set
    var mocks by mutableStateOf(decodeMocks(prefs.getStringSet(KEY_MOCKS)))
        private set

    /** Reads the answer log. Call once, off the main thread, before the UI uses the store. */
    fun load() {
        runCatching { if (savedFile.exists()) savedFile.readLines().filter { it.isNotBlank() }.forEach { saved += it } }
        if (!log.exists()) { rev++; return }
        runCatching {
            log.forEachLine { line ->
                val p = line.split('\t')
                if (p.size < 4 || p[0].isEmpty()) return@forEachLine
                if (p[1] == "u") seen += p[0] else answers[p[0]] = p[1] == "1"
                daily[p[3]] = (daily[p[3]] ?: 0) + 1
            }
        }
        rev++
    }

    fun record(q: Question, picked: Int) {
        val mark = if (!q.scored) "u" else if (picked == q.answer) "1" else "0"
        if (mark == "u") seen += q.id else answers[q.id] = mark == "1"
        val today = LocalDate.now().toString()
        daily[today] = (daily[today] ?: 0) + 1
        rev++
        val line = "${q.id}\t$mark\t${q.book}\t$today\n"
        io.execute { runCatching { FileWriter(log, true).use { it.write(line) } } }
    }

    fun isSaved(id: String): Boolean {
        rev
        return id in saved
    }

    /** Bookmarked ids, most recently saved first. */
    fun savedIds(): List<String> {
        rev
        return saved.toList().asReversed()
    }

    fun toggleSaved(id: String) {
        if (!saved.remove(id)) saved += id
        rev++
        val snapshot = saved.joinToString("\n")
        io.execute { runCatching { savedFile.writeText(snapshot) } }
    }

    fun result(id: String): Boolean? {
        rev
        return answers[id]
    }

    fun attempted(id: String): Boolean {
        rev
        return id in answers || id in seen
    }

    /** (attempted, correct, wrong) among [ids]. */
    fun stats(ids: Collection<String>): Triple<Int, Int, Int> {
        rev
        var attempted = 0
        var correct = 0
        var wrong = 0
        for (id in ids) {
            val a = answers[id]
            if (a == null) {
                if (id in seen) attempted++
                continue
            }
            attempted++
            if (a) correct++ else wrong++
        }
        return Triple(attempted, correct, wrong)
    }

    fun wrongIds(): List<String> {
        rev
        return answers.filterValues { !it }.keys.toList()
    }

    val totalAnswered: Int get() { rev; return answers.size + seen.size }
    val totalScored: Int get() { rev; return answers.size }
    val totalCorrect: Int get() { rev; return answers.count { it.value } }

    fun answeredOn(date: LocalDate): Int {
        rev
        return daily[date.toString()] ?: 0
    }

    /** Consecutive days (ending today or yesterday) with at least one answer. */
    fun streak(today: LocalDate = LocalDate.now()): Int {
        rev
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
        prefs.putInt(KEY_PYQ_TARGET, pyqTarget)
    }

    fun changeNotesTarget(delta: Int) {
        notesTarget = (notesTarget + delta).coerceIn(10, 1000)
        prefs.putInt(KEY_NOTES_TARGET, notesTarget)
    }

    fun changeCaTarget(delta: Int) {
        caTarget = (caTarget + delta).coerceIn(10, 500)
        prefs.putInt(KEY_CA_TARGET, caTarget)
    }

    fun changeMockSize(delta: Int) {
        mockSize = (mockSize + delta).coerceIn(30, 200)
        prefs.putInt(KEY_MOCK_SIZE, mockSize)
    }

    fun addMock(r: MockResult) {
        mocks = (listOf(r) + mocks).take(50)
        prefs.putStringSet(KEY_MOCKS, mocks.mapIndexed { i, m -> "$i\t${m.date}\t${m.day}\t${m.total}\t${m.correct}\t${m.wrong}" }.toSet())
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
        const val KEY_CA_TARGET = "ca_target"
        const val KEY_MOCKS = "mocks"
    }
}
