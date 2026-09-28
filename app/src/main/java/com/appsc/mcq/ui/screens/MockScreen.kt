package com.appsc.mcq.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.appsc.mcq.platform.PlatformBackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.mcq.data.MockResult
import com.appsc.mcq.data.Question
import com.appsc.mcq.data.Sets
import com.appsc.mcq.ui.components.Loading
import com.appsc.mcq.ui.components.LocalApp
import com.appsc.mcq.ui.components.Message
import com.appsc.mcq.ui.components.ProgressLine
import com.appsc.mcq.ui.components.TopBar
import com.appsc.mcq.ui.theme.C
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Timed mock paper for a plan day: one minute per question, no hints, answers can be changed until
 * submitting; then the key, explanations and techniques are shown and every answer is recorded.
 * The paper, the picks and the clock are saved, so rotation or the app being killed does not lose the attempt.
 */
@Composable
fun MockScreen(dayN: Int, nav: Nav) {
    val app = LocalApp.current
    val day = app.repo.day(dayN)
    val ids = rememberSaveable(dayN) { ArrayList(Sets.mock(app.repo, app.store, day, app.store.mockSize)) }
    val set by produceState<List<Question>?>(null, ids) { value = app.repo.questions(ids).filter { it.scored && !it.flashcard } }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (submitted || set.isNullOrEmpty()) {
            nav.back()
        } else {
            askLeave = true
        }
    }
    PlatformBackHandler(onBack = leave)

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("Leave the mock?") },
            text = { Text("This attempt is not submitted. Leaving now discards it.") },
            confirmButton = { TextButton(onClick = { askLeave = false; nav.back() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text("Keep going") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Mock test · Day $dayN", onBack = leave)
        val s = set
        when {
            s == null -> Loading()
            s.isEmpty() -> Message("No questions available for a mock yet.")
            else -> MockRun(dayN, s, submitted, onSubmitted = { submitted = true }, nav)
        }
    }
}

@Composable
private fun MockRun(dayN: Int, set: List<Question>, submitted: Boolean, onSubmitted: () -> Unit, nav: Nav) {
    val store = LocalApp.current.store
    var picks by rememberSaveable { mutableStateOf(IntArray(set.size) { -1 }) }
    if (picks.size != set.size) picks = IntArray(set.size) { -1 }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val left = rememberSaveable { mutableLongStateOf(set.size * 60L) }
    val listState = rememberLazyListState()
    LaunchedEffect(index) { listState.scrollToItem(0) }

    fun submit() {
        if (submitted) return
        set.forEachIndexed { i, q -> if (picks[i] >= 0) store.record(q, picks[i]) }
        val correct = set.indices.count { picks[it] == set[it].answer }
        val wrong = set.indices.count { picks[it] >= 0 && picks[it] != set[it].answer }
        store.addMock(MockResult(LocalDate.now().toString(), dayN, set.size, correct, wrong))
        onSubmitted()
        index = set.size
    }

    LaunchedEffect(submitted) {
        while (!submitted && left.longValue > 0) {
            delay(1000)
            left.longValue--
        }
        if (!submitted && left.longValue <= 0) submit()
    }

    if (confirm) {
        val unanswered = picks.count { it < 0 }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Submit the mock?") },
            text = { Text(if (unanswered > 0) "$unanswered questions are unanswered. They count as skipped (no negative mark)." else "All questions answered.") },
            confirmButton = { TextButton(onClick = { confirm = false; submit() }) { Text("Submit") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep going") } },
        )
    }

    if (index >= set.size) {
        if (submitted) {
            MockResults(set, picks.toList(), onReview = { index = 0 }, onDone = nav::back)
            return
        }
        index = set.size - 1
    }

    val q = set[index]
    val picked = picks[index]
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Question ${index + 1} of ${set.size}",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Accent),
                    modifier = Modifier.weight(1f),
                )
                if (!submitted) Clock { left.longValue } else Text("Review", style = TextStyle(fontSize = 13.sp, color = C.Muted))
            }
            Spacer(Modifier.height(6.dp))
            ProgressLine(picks.count { it >= 0 } / set.size.toFloat())
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            item(key = "m-${q.id}-$index") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    QuestionHeader(q)
                    QuestionBody(q)
                    Spacer(Modifier.height(14.dp))
                    q.options.forEachIndexed { i, opt ->
                        OptionCard(i, opt, picked, if (submitted) q.answer else -1, reveal = submitted) {
                            if (!submitted) picks = picks.copyOf().also { it[index] = if (it[index] == i) -1 else i }
                        }
                    }
                    if (submitted) {
                        Spacer(Modifier.height(8.dp))
                        Explanation(q, picked)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (index > 0) index-- },
                enabled = index > 0,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Previous", color = if (index > 0) C.Ink else C.Faint) }
            if (index < set.size - 1) {
                Button(
                    onClick = { index++ },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Accent),
                ) { Text("Next") }
            } else {
                Button(
                    onClick = { if (submitted) index = set.size else confirm = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Green),
                ) { Text(if (submitted) "Results" else "Submit") }
            }
        }
        if (!submitted && index < set.size - 1) {
            TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)) {
                Text("Submit now", color = C.Muted)
            }
        }
    }
}

/** Only this text reads the clock, so the ticking does not redraw the question every second. */
@Composable
private fun Clock(left: () -> Long) {
    val l = left()
    Text(
        "%d:%02d left".format(l / 60, l % 60),
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (l < 300) C.High else C.Ink),
    )
}

@Composable
private fun MockResults(set: List<Question>, picks: List<Int>, onReview: () -> Unit, onDone: () -> Unit) {
    val correct = set.indices.count { set[it].scored && picks[it] == set[it].answer }
    val wrong = set.indices.count { set[it].scored && picks[it] >= 0 && picks[it] != set[it].answer }
    val skipped = set.indices.count { picks[it] < 0 }
    val subjects = listOf("History", "Polity", "Economy", "Geography", "Sci-Tech & Env", "Current Affairs")
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreCircle("%.1f".format(correct - wrong / 3f))
                Text(
                    "Net score out of ${set.size} (1/3 negative)",
                    style = TextStyle(fontSize = 15.sp, color = C.Ink, fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "$correct correct · $wrong wrong · $skipped skipped",
                    style = TextStyle(fontSize = 13.sp, color = C.Muted),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                (1..6).forEach { b ->
                    val idx = set.indices.filter { set[it].book == b }
                    if (idx.isEmpty()) return@forEach
                    val c = idx.count { set[it].scored && picks[it] == set[it].answer }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(subjects[b - 1], style = TextStyle(fontSize = 14.sp, color = C.Ink), modifier = Modifier.weight(1f))
                        Text("$c / ${idx.size}", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
                    }
                    ProgressLine(c / idx.size.toFloat())
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onReview,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Accent),
                ) { Text("Review every question") }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("Done", color = C.Ink)
                }
            }
        }
        reviewList(set, picks)
    }
}
