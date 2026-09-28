package com.appsc.mcq

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.appsc.mcq.data.Origin
import com.appsc.mcq.data.QuizSource
import com.appsc.mcq.data.Sets
import com.appsc.mcq.platform.DesktopHost
import com.appsc.mcq.platform.desktopRepository
import com.appsc.mcq.platform.desktopStore
import com.appsc.mcq.ui.AppContent
import com.appsc.mcq.ui.components.AppState
import com.appsc.mcq.ui.theme.McqTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Windows build reads the same question bank from its classpath and runs the same screens. */
class DesktopTest {
    private fun app() = AppState(desktopRepository(), desktopStore(Files.createTempDirectory("mcq").toFile()))

    @Test
    fun bankLoadsFromClasspath() = runBlocking {
        val app = app()
        app.repo.load()
        app.store.load()
        assertEquals(90, app.repo.plan.days.size)
        var total = 0
        for (b in 1..6) for (o in Origin.entries) {
            val ids = app.repo.index.book(o, b)
            assertEquals(ids.size, app.repo.questions(ids).size, "book $b $o")
            total += ids.size
        }
        assertTrue(total > 90_000, "got $total")
        val target = Sets.pool(app.repo, app.store, QuizSource("tp", 0, 1))
        assertEquals(100, target.size)
    }

    /**
     * In the real window every click arrives on the Swing thread, which is also Dispatchers.Main, so the
     * navigation library's "main thread" checks pass. The test harness drives the UI from its own thread;
     * making that thread "main" for the test reproduces the real conditions.
     */
    @OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun appStartsAndOpensAQuiz() = runComposeUiTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val app = app()
        setContent { McqTheme { DesktopHost { AppContent(app) } } }
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithText("to exam", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Subjects").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("notes MCQs", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Today").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("PYQ practice").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("PYQ practice").performClick()
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithText("Question 1 of", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
