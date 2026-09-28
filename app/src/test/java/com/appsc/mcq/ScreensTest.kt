package com.appsc.mcq

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.appsc.mcq.data.Origin
import com.appsc.mcq.data.ProgressStore
import com.appsc.mcq.data.QuizSource
import com.appsc.mcq.data.Repository
import com.appsc.mcq.ui.components.AppState
import com.appsc.mcq.ui.components.LocalApp
import com.appsc.mcq.ui.screens.BookScreen
import com.appsc.mcq.ui.screens.DayScreen
import com.appsc.mcq.ui.screens.MistakesScreen
import com.appsc.mcq.ui.screens.MockScreen
import com.appsc.mcq.ui.screens.Nav
import com.appsc.mcq.ui.screens.PlanScreen
import com.appsc.mcq.ui.screens.ProgressScreen
import com.appsc.mcq.ui.screens.QuizScreen
import com.appsc.mcq.ui.screens.RowScreen
import com.appsc.mcq.ui.screens.SavedScreen
import com.appsc.mcq.ui.screens.SubjectsScreen
import com.appsc.mcq.ui.screens.TodayScreen
import com.appsc.mcq.ui.theme.McqTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders every screen with the real data and clicks through a quiz and a mock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class ScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var app: AppState
    private val calls = mutableListOf<String>()
    private val nav = object : Nav {
        override fun quiz(kind: String, book: Int, index: Int, mode: String) { calls += "quiz/$kind/$book/$index/$mode" }
        override fun mock(day: Int) { calls += "mock/$day" }
        override fun day(n: Int) { calls += "day/$n" }
        override fun book(id: Int) { calls += "book/$id" }
        override fun row(book: Int, row: Int) { calls += "row/$book/$row" }
        override fun saved() { calls += "saved" }
        override fun back() { calls += "back" }
    }

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = Repository(ctx)
        val store = ProgressStore(ctx)
        runBlocking { repo.load() }
        store.load()
        app = AppState(repo, store)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { McqTheme { CompositionLocalProvider(LocalApp provides app) { content() } } }
    }

    private fun waitFor(text: String) {
        rule.waitUntil(30_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun today() { show { TodayScreen(nav) }; waitFor("Day "); waitFor("to exam") }

    @Test fun plan() { show { PlanScreen(nav) }; waitFor("90-day plan") }

    @Test fun studyDay() {
        show { DayScreen(1, nav) }
        waitFor("PYQ practice")
        rule.onNodeWithText("PYQ practice").performClick()
        assert(calls.last().startsWith("quiz/tp/0/1")) { calls.toString() }
        waitFor("Current affairs notes MCQs")
        rule.onNodeWithText("Current affairs notes MCQs").performClick()
        assert(calls.last().startsWith("quiz/tc/0/1")) { calls.toString() }
        // today's CA section is listed with the day's sections
        waitFor("Current affairs · G-1")
        rule.onNodeWithText("Current affairs · G-1", substring = true).performClick()
        assert(calls.last() == "row/6/0") { calls.toString() }
    }

    @Test fun currentAffairsQuiz() {
        show { QuizScreen(QuizSource("tc", 0, 1), "all", nav) }
        waitFor("Question 1 of")
        waitFor("Current Affairs")
    }

    @Test fun mockDay() {
        val n = app.repo.plan.days.first { it.isMock }.n
        show { DayScreen(n, nav) }
        waitFor("Mock test")
    }

    @Test fun subjectsAndBooks() {
        show { SubjectsScreen(nav) }
        waitFor("notes MCQs")
    }

    @Test fun everyBook() {
        for (b in 1..6) {
            val r = ScreensTestHelper.firstRow(app, b)
            assert(r >= 0)
        }
        show { BookScreen(2, nav) }
        waitFor("PYQs ·")
    }

    @Test fun section() {
        val r = ScreensTestHelper.firstRow(app, 2)
        show { RowScreen(2, r, nav) }
        waitFor("Notes MCQs")
    }

    @Test fun quizAnswerAndFinish() {
        val src = QuizSource("tn", 0, 1)
        val first = runBlocking { app.repo.questions(com.appsc.mcq.data.Sets.pool(app.repo, app.store, src).take(1)).first() }
        show { QuizScreen(src, "all", nav) }
        waitFor("Question 1 of")
        rule.onAllNodesWithText(first.options[0]).onLast().performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Correct!").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Answer: (", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Next").performClick()
        waitFor("Question 2 of")
        rule.onNodeWithText("Previous").performClick()
        waitFor("Question 1 of")
    }

    @Test fun flashcardRevealAndGrade() {
        // find a section whose first PYQ is a flashcard and run it
        val (b, r, q) = runBlocking {
            var hit: Triple<Int, Int, com.appsc.mcq.data.Question>? = null
            loop@ for (b in 1..6) for (r in app.repo.catalog[b - 1].rows.indices) {
                val first = app.repo.row(Origin.PYQ, b, r).firstOrNull() ?: continue
                if (first.flashcard) { hit = Triple(b, r, first); break@loop }
            }
            hit!!
        }
        show { QuizScreen(QuizSource("rp", b, r), "all", nav) }
        waitFor("Question 1 of")
        waitFor("Flashcard")
        rule.onNodeWithText("Show answer").performClick()
        waitFor(q.answerText)
        rule.onNodeWithText("Didn't know").performClick()
        waitFor("Marked as not known")
        rule.waitUntil(5_000) { app.store.result(q.id) == false }
    }

    @Test fun pyqQuizLoads() {
        show { QuizScreen(QuizSource("tp", 0, 5), "all", nav) }
        waitFor("Question 1 of")
        waitFor("PYQ")
    }

    @Test fun mockRuns() {
        val n = app.repo.plan.days.first { it.isMock }.n
        show { MockScreen(n, nav) }
        waitFor("left")
        waitFor("Question 1 of")
    }

    @Test fun mistakesAndProgress() {
        show { MistakesScreen(nav) }
        waitFor("Saved for revision")
        waitFor("All subjects")
    }

    @Test fun bookmarkAndSavedList() {
        val src = QuizSource("tc", 0, 1)
        val first = runBlocking { app.repo.questions(com.appsc.mcq.data.Sets.pool(app.repo, app.store, src).take(1)).first() }
        show { QuizScreen(src, "all", nav) }
        waitFor("Question 1 of")
        rule.onNodeWithContentDescription("Save for revision").performClick()
        rule.waitUntil(5_000) { app.store.isSaved(first.id) }
        assert(app.store.savedIds().first() == first.id)
    }

    @Test fun savedScreenShowsAnswers() {
        val ids = app.repo.index.row(Origin.NOTES, 2, ScreensTestHelper.firstRow(app, 2)).take(2)
        ids.forEach { if (!app.store.isSaved(it)) app.store.toggleSaved(it) }
        val q = runBlocking { app.repo.questions(ids).first() }
        show { SavedScreen(nav) }
        waitFor("Show explanation")
        waitFor(q.options[q.answer])
    }

    @Test fun progress() {
        show { ProgressScreen() }
        waitFor("questions done")
    }
}

object ScreensTestHelper {
    fun firstRow(app: AppState, book: Int): Int =
        app.repo.catalog[book - 1].rows.indices.firstOrNull { app.repo.index.row(Origin.NOTES, book, it).isNotEmpty() } ?: -1
}
