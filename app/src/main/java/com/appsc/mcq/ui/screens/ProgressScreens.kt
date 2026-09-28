package com.appsc.mcq.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.mcq.data.Origin
import com.appsc.mcq.ui.components.Card
import com.appsc.mcq.ui.components.LocalApp
import com.appsc.mcq.ui.components.PracticeCard
import com.appsc.mcq.ui.components.ProgressLine
import com.appsc.mcq.ui.components.SectionHeader
import com.appsc.mcq.ui.components.StatBox
import com.appsc.mcq.ui.components.TopBar
import com.appsc.mcq.ui.theme.C
import java.time.LocalDate

private val SUBJECTS = listOf("History & Culture", "Polity, Society & IR", "Economy", "Geography", "Sci-Tech & Environment", "Current Affairs")

@Composable
fun MistakesScreen(nav: Nav) {
    val store = LocalApp.current.store
    val app = LocalApp.current
    val wrong = store.wrongIds()
    val byBook = remember(wrong) { wrong.groupingBy { app.repo.index.locate(it)?.second ?: 0 }.eachCount() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Review")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                val n = store.savedIds().size
                PracticeCard(
                    title = "Saved for revision",
                    subtitle = if (n == 0) "Tap ☆ on any question to keep it here" else "$n questions, with answers and explanations",
                    icon = Icons.Filled.Star,
                    iconBg = C.MedSoft,
                    iconFg = C.Med,
                    done = 0,
                    correct = 0,
                    total = 0,
                    onStart = { nav.saved() },
                    startLabel = if (n == 0) null else "Open saved list",
                )
            }
            item { SectionHeader("Mistakes") }
            item {
                Text(
                    "Every question you got wrong, PYQs and notes MCQs, until you answer it right. Each retry shows the technique that would have reached the key.",
                    style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = C.Muted),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            item {
                PracticeCard(
                    title = "All subjects",
                    subtitle = if (wrong.isEmpty()) "Nothing to retry yet" else "${wrong.size} questions to get right",
                    icon = Icons.Filled.ErrorOutline,
                    iconBg = C.HighSoft,
                    iconFg = C.High,
                    done = 0,
                    correct = 0,
                    total = 0,
                    onStart = if (wrong.isEmpty()) null else { { nav.quiz("w", 0, 0, "wrong") } },
                    startLabel = if (wrong.isEmpty()) null else "Retry all",
                )
            }
            item { SectionHeader("By subject") }
            items((1..6).toList()) { b ->
                val n = byBook[b] ?: 0
                PracticeCard(
                    title = SUBJECTS[b - 1],
                    subtitle = if (n == 0) "No mistakes" else "$n to retry",
                    icon = Icons.Filled.ErrorOutline,
                    iconBg = C.Chip,
                    iconFg = if (n == 0) C.Faint else C.High,
                    done = 0,
                    correct = 0,
                    total = 0,
                    onStart = if (n == 0) null else { { nav.quiz("w", b, 0, "wrong") } },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ProgressScreen() {
    val app = LocalApp.current
    val store = app.store
    val totals: Map<Pair<Int, Origin>, List<String>> = remember {
        buildMap { for (b in 1..6) for (o in Origin.entries) put(b to o, app.repo.index.book(o, b)) }
    }
    val answered = store.totalAnswered
    val correct = store.totalCorrect
    val scored = store.totalScored

    Column(Modifier.fillMaxSize()) {
        TopBar("Progress")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("$answered", "questions done", Icons.Outlined.CheckCircle, Modifier.weight(1f))
                    StatBox(if (scored == 0) "–" else "${correct * 100 / scored}%", "accuracy", Icons.Outlined.Percent, Modifier.weight(1f))
                    StatBox("${store.streak()}", "day streak", Icons.Outlined.LocalFireDepartment, Modifier.weight(1f))
                }
            }
            item { SectionHeader("Last 14 days") }
            item { ActivityBars() }
            item { SectionHeader("By subject") }
            val t = totals
            run {
                items((1..6).toList()) { b ->
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text(SUBJECTS[b - 1], style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
                            for (o in Origin.entries) {
                                val ids = t[b to o] ?: emptyList()
                                val (d, c) = store.stats(ids)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(o.label, style = TextStyle(fontSize = 12.sp, color = C.Muted), modifier = Modifier.width(78.dp))
                                    ProgressLine(if (ids.isEmpty()) 0f else d / ids.size.toFloat(), Modifier.weight(1f), color = if (o == Origin.PYQ) C.ExamInk else C.Accent)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "$d/${ids.size}" + if (d > 0) " · ${c * 100 / d}%" else "",
                                        style = TextStyle(fontSize = 11.sp, color = C.Muted),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (store.mocks.isNotEmpty()) {
                item { SectionHeader("Mock tests") }
                items(store.mocks.take(10)) { m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text("Day ${m.day} · ${m.date}", style = TextStyle(fontSize = 14.sp, color = C.Ink), modifier = Modifier.weight(1f))
                        Text(
                            "net %.1f / %d  (%d✓ %d✗)".format(m.net, m.total, m.correct, m.wrong),
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink),
                        )
                    }
                }
            }
            item { SectionHeader("Settings") }
            item {
                Card {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Stepper("PYQ target per day", store.pyqTarget, "Drawn from the day's sections, APPSC first", { store.changePyqTarget(-10) }, { store.changePyqTarget(10) })
                        Stepper("Notes MCQ target per day", store.notesTarget, "From the Combined Notes of the day's sections", { store.changeNotesTarget(-10) }, { store.changeNotesTarget(10) })
                        Stepper("Current affairs target per day", store.caTarget, "From the day's Book 6 section; revision rounds take the next set", { store.changeCaTarget(-10) }, { store.changeCaTarget(10) })
                        Stepper("Mock test size", store.mockSize, "One minute per question", { store.changeMockSize(-10) }, { store.changeMockSize(10) })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Stepper(title: String, value: Int, hint: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 15.sp, color = C.Ink, fontWeight = FontWeight.Medium))
            Text(hint, style = TextStyle(fontSize = 12.sp, color = C.Muted))
        }
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, "Less", tint = C.Accent) }
        Text("$value", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Ink), modifier = Modifier.width(42.dp))
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, "More", tint = C.Accent) }
    }
}

@Composable
private fun ActivityBars() {
    val store = LocalApp.current.store
    val today = LocalDate.now()
    val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val counts = days.map { store.answeredOn(it) }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { i, d ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (counts[i] > 0) Text("${counts[i]}", style = TextStyle(fontSize = 9.sp, color = C.Muted))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((70 * counts[i] / max).coerceAtLeast(2).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (d == today) C.ExamInk else if (counts[i] > 0) C.Accent else C.Chip),
                )
                Text("${d.dayOfMonth}", style = TextStyle(fontSize = 10.sp, color = C.Muted), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    Spacer(Modifier.size(4.dp))
}
