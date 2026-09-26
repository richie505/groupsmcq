package com.appsc.mcq.data

import java.time.LocalDate

/** Where a question comes from: a previous-year paper, or generated from the Combined Notes. */
enum class Origin(val code: String, val label: String) {
    PYQ("p", "PYQ"),
    NOTES("n", "Notes MCQ");

    companion object {
        fun of(code: String) = if (code == "n") NOTES else PYQ
    }
}

data class Question(
    val id: String,
    val stem: String,
    val table: List<List<String>>,
    val options: List<String>,
    val answer: Int,
    /** PYQ paper and year, e.g. "APPSC Group 2 · 2019". Blank for notes MCQs. */
    val source: String,
    val appsc: Boolean,
    val explanation: String,
    val notes: List<String>,
    /** 's' scored, 'u' unscored (no official key / cancelled). */
    val kind: Char = 's',
    val cancelled: Boolean = false,
    /** Subject (book) the question is filed under: 1 History … 6 Current Affairs. Drives the technique hints. */
    val book: Int = 0,
    val origin: Origin = Origin.PYQ,
    /** Notes MCQs: question pattern (statements, match, assertion_reason…), the technique it trains, and its subsection. */
    val type: String = "",
    val technique: String = "",
    val section: String = "",
) {
    val scored get() = kind != 'u' && answer >= 0
    val year: Int get() = Regex("""(19|20)\d\d""").findAll(source).lastOrNull()?.value?.toInt() ?: 0
}

/** The questions of one subject, by notes row (Section) and by unit (Topic, general PYQs). */
data class Bank(
    val rows: Map<Int, List<Question>>,
    val units: Map<Int, List<Question>> = emptyMap(),
) {
    val size: Int get() = (rows.values.flatten() + units.values.flatten()).distinctBy { it.id }.size
}

// ---- catalogue ----

data class NoteUnit(val code: String, val title: String)

data class RowInfo(
    val book: Int,
    val index: Int,
    val unitIndex: Int,
    val codes: List<String>,
    val title: String,
    val tag: String,
)

data class BookInfo(
    val id: Int,
    val title: String,
    val short: String,
    val units: List<NoteUnit>,
    val rows: List<RowInfo>,
)

// ---- 90-day plan ----

data class PlanRow(
    val codes: List<String>,
    val topic: String,
    val priority: String,
    val book: Int,
    val row: Int,
)

data class PlanDay(
    val n: Int,
    val date: LocalDate,
    val dow: String,
    val phase: String,
    val focus: String,
    val left: String,
    val type: String,
    val rows: List<PlanRow>,
) {
    val isMock get() = rows.isEmpty()
}

data class Plan(
    val start: LocalDate,
    val exam: LocalDate,
    val examLabel: String,
    val days: List<PlanDay>,
)

/** One day's targets: the question ids to finish from each source. */
data class DayTarget(val pyq: List<String>, val notes: List<String>)

data class MockResult(val date: String, val day: Int, val total: Int, val correct: Int, val wrong: Int) {
    val net: Float get() = correct - wrong / 3f
}
