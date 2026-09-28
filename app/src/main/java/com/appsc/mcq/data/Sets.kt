package com.appsc.mcq.data

import kotlin.random.Random

/** Where a quiz's questions come from; see [Sets.pool]. */
data class QuizSource(val kind: String, val book: Int, val index: Int)

/** Question sets, worked out on ids from the index; questions are loaded only for the set itself. */
object Sets {

    /**
     * Ids behind a quiz source, each once.
     * kinds: "tp"/"tn"/"tc" a day's target (PYQ / notes / current affairs), "xp"/"xn"/"xc" the rest of that day's pool,
     * "rp"/"rn" one section, "w" mistakes (book 0 = all subjects).
     */
    fun pool(repo: Repository, store: ProgressStore, src: QuizSource): List<String> {
        val idx = repo.index
        return when (src.kind) {
            "tp" -> idx.day(Origin.PYQ, src.index).take(store.pyqTarget)
            "tn" -> idx.day(Origin.NOTES, src.index).take(store.notesTarget)
            "xp" -> idx.day(Origin.PYQ, src.index).drop(store.pyqTarget)
            "xn" -> idx.day(Origin.NOTES, src.index).drop(store.notesTarget)
            "tc" -> idx.caTarget(src.index, store.caTarget)
            "xc" -> idx.caTarget(src.index, store.caTarget).toSet().let { t -> idx.dayPools(src.index)?.ca?.filter { it !in t } ?: emptyList() }
            "rp" -> idx.row(Origin.PYQ, src.book, src.index)
            "rn" -> idx.row(Origin.NOTES, src.book, src.index)
            "w" -> store.wrongIds().filter { src.book == 0 || idx.locate(it)?.second == src.book }
            else -> emptyList()
        }
    }

    /**
     * mode: "new" = every question not attempted yet (the whole set in one run), "wrong" = answered wrong,
     * "all" = every question. The caller freezes the result for the round.
     */
    fun pick(pool: List<String>, store: ProgressStore, mode: String): List<String> = when (mode) {
        "wrong" -> pool.filter { store.result(it) == false }
        "all" -> pool
        else -> pool.filter { !store.attempted(it) }
    }

    /**
     * A mock paper for plan day [day]: [size] ids spread evenly over the six subjects, from the sections
     * studied so far (every section of a subject the plan has not reached yet), PYQs and notes MCQs mixed.
     * Unattempted first, then ones answered wrong. The same day and progress give the same paper.
     */
    fun mock(repo: Repository, store: ProgressStore, day: PlanDay, size: Int): List<String> {
        val idx = repo.index
        val studied = repo.rowsStudiedBy(day.n).groupBy { it.book }
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        for (book in 1..6) {
            val share = size / 6 + if (book <= size % 6) 1 else 0
            val rows = studied[book]?.map { it.row } ?: repo.catalog.getOrNull(book - 1)?.rows?.indices?.toList() ?: emptyList()
            val cand = rows.flatMap { r -> idx.row(Origin.PYQ, book, r) + idx.row(Origin.NOTES, book, r) }
                .filter { seen.add(it) }
                .shuffled(Random(day.n * 31 + book))
            out += cand.sortedBy { when (store.result(it)) { null -> 0; false -> 1; true -> 2 } }.take(share)
        }
        return out
    }
}
