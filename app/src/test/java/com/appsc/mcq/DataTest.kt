package com.appsc.mcq

import androidx.test.core.app.ApplicationProvider
import com.appsc.mcq.data.Origin
import com.appsc.mcq.data.ProgressStore
import com.appsc.mcq.data.QuizSource
import com.appsc.mcq.data.Repository
import com.appsc.mcq.platform.androidRepository
import com.appsc.mcq.platform.androidStore
import com.appsc.mcq.data.Sets
import com.appsc.mcq.data.Techniques
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every bundled question must load with the app's own parser and survive the technique analysis. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun everyQuestionLoadsAndAnalyses() = runBlocking {
        val repo = androidRepository(ctx)
        repo.load()
        assertEquals(90, repo.plan.days.size)
        assertEquals(6, repo.catalog.size)
        var total = 0
        var flashcards = 0
        for (b in 1..6) for (o in Origin.entries) {
            val ids = repo.index.book(o, b)
            val qs = repo.questions(ids)
            assertEquals("book $b $o: every indexed id loads", ids.size, qs.size)
            for (q in qs) {
                assertTrue(q.id, q.stem.isNotBlank())
                if (q.flashcard) {
                    flashcards++
                    assertTrue("flashcard ${q.id} has an answer", q.answerText.isNotBlank() && q.options.isEmpty() && q.answer == 0)
                    assertTrue(q.id, Techniques.hints(q).isEmpty())
                } else {
                    assertTrue(q.id, q.options.size >= 2)
                    assertTrue(q.id, q.answer in -1 until q.options.size)
                    Techniques.hints(q)
                    if (q.answer >= 0) Techniques.review(q, (q.answer + 1) % q.options.size)
                }
            }
            total += qs.size
        }
        assertTrue("expected a full bank, got $total", total > 90_000)
        assertTrue("flashcards included, got $flashcards", flashcards >= 1_800)
    }

    @Test
    fun currentAffairsTargetsFollowThePlan() = runBlocking {
        val repo = androidRepository(ctx)
        repo.load()
        val caDays = repo.plan.days.filter { repo.index.dayPools(it.n)?.ca?.isNotEmpty() == true }
        assertEquals("days with a current affairs block", 73, caDays.size)
        for (d in caDays) {
            val t = repo.index.caTarget(d.n, 40)
            assertTrue("day ${d.n}", t.isNotEmpty() && t.size <= 40 && t.toSet().size == t.size)
            val qs = repo.questions(t, Sets.preferredRows(repo, QuizSource("tc", 0, d.n)))
            assertEquals(t.size, qs.size)
            assertTrue("day ${d.n} CA is Book 6", qs.all { it.book == 6 })
        }
        // a later revision round of the same section starts with different questions
        val first = caDays.first()
        val again = caDays.first { it.n != first.n && repo.index.dayPools(it.n)!!.caRow == repo.index.dayPools(first.n)!!.caRow }
        assertTrue(repo.index.caTarget(first.n, 40).first() != repo.index.caTarget(again.n, 40).first())
    }

    @Test
    fun everyDayHasTargetsAndMockDaysHaveAPaper() = runBlocking {
        val repo = androidRepository(ctx)
        repo.load()
        val store = androidStore(ctx).also { it.load() }
        for (d in repo.plan.days) {
            if (d.isMock) {
                val ids = Sets.mock(repo, store, d, 150)
                assertEquals("mock day ${d.n}", 150, ids.size)
                assertEquals(ids.size, repo.questions(ids).size)
            } else {
                val p = Sets.pool(repo, store, QuizSource("tp", 0, d.n))
                val n = Sets.pool(repo, store, QuizSource("tn", 0, d.n))
                assertTrue("day ${d.n} has PYQs", p.isNotEmpty())
                assertTrue("day ${d.n} has notes MCQs", n.isNotEmpty())
                assertEquals(p.size, repo.questions(p).size)
                assertEquals(n.size, repo.questions(n).size)
            }
        }
    }
}
