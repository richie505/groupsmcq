package com.appsc.mcq.data

import kotlin.random.Random

/** Question sets for the quiz screen, built from a [QuizSource]. */
object Sets {
    const val SESSION = 25

    /**
     * Question pool behind a quiz source, each id once.
     * kinds: "tp"/"tn" a day's target (PYQ / notes), "xp"/"xn" the rest of the day's sections beyond the target,
     * "rp"/"rn" one section, "u" a unit's general PYQs, "w" mistakes (book 0 = all subjects).
     */
    suspend fun pool(repo: Repository, store: ProgressStore, src: QuizSource): List<Question> = when (src.kind) {
        "tp", "tn", "xp", "xn" -> {
            val origin = if (src.kind.endsWith("p")) Origin.PYQ else Origin.NOTES
            val day = repo.plan.days.first { it.n == src.index }
            val all = repo.dayPool(day, origin)
            val size = if (origin == Origin.PYQ) store.pyqTarget else store.notesTarget
            if (src.kind.startsWith("t")) all.take(size) else all.drop(size)
        }
        "rp" -> repo.rowQuestions(Origin.PYQ, src.book, src.index)
        "rn" -> repo.rowQuestions(Origin.NOTES, src.book, src.index)
        "u" -> repo.unitQuestions(src.book, src.index)
        "w" -> {
            val byId = repo.byId()
            store.wrongIds.mapNotNull { byId[it] }.filter { src.book == 0 || it.book == src.book }
        }
        else -> emptyList()
    }

    /**
     * mode: "new" = the next [SESSION] unattempted questions, "wrong" = answered wrong, "all" = every question.
     * Sets are frozen for a round by the caller, so answering does not reshuffle them.
     */
    fun pick(pool: List<Question>, store: ProgressStore, mode: String): List<Question> = when (mode) {
        "wrong" -> pool.filter { store.answers[it.id] == false }
        "all" -> pool
        else -> pool.filter { !store.attempted(it.id) }.take(SESSION)
    }

    /**
     * A mock paper for plan day [day]: [size] questions spread evenly over the six subjects, from the
     * sections studied so far (all sections if none yet), PYQs and notes MCQs mixed. Unattempted
     * questions first, then ones answered wrong. The same day always gives the same paper.
     */
    suspend fun mock(repo: Repository, store: ProgressStore, day: PlanDay, size: Int): List<Question> {
        val studied = repo.rowsStudiedBy(day.n).groupBy { it.book }
        val out = ArrayList<Question>()
        val seen = HashSet<String>()
        for (book in 1..6) {
            val share = size / 6 + if (book <= size % 6) 1 else 0
            val rows = studied[book]?.map { it.row } ?: repo.catalog[book - 1].rows.indices.toList()
            val cand = rows.flatMap { r ->
                repo.rowQuestions(Origin.PYQ, book, r).filter { it.scored } + repo.rowQuestions(Origin.NOTES, book, r)
            }.filter { seen.add(it.id) }.shuffled(Random(day.n * 31 + book))
            val ranked = cand.sortedBy { q ->
                when (store.answers[q.id]) {
                    null -> 0
                    false -> 1
                    true -> 2
                }
            }
            out += ranked.take(share)
        }
        return out
    }
}

/** Where a quiz's questions come from; see [Sets.pool]. */
data class QuizSource(val kind: String, val book: Int, val index: Int)
