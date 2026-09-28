package com.appsc.mcq.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.mcq.data.DayTarget
import com.appsc.mcq.data.Origin
import com.appsc.mcq.data.PlanDay
import com.appsc.mcq.ui.components.Card
import com.appsc.mcq.ui.components.LocalApp
import com.appsc.mcq.ui.components.PracticeCard
import com.appsc.mcq.ui.components.PriorityTag
import com.appsc.mcq.ui.components.ProgressLine
import com.appsc.mcq.ui.components.SectionHeader
import com.appsc.mcq.ui.components.Tag
import com.appsc.mcq.ui.components.TopBar
import com.appsc.mcq.ui.theme.C
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

interface Nav {
    /** kind/index as in [com.appsc.mcq.data.QuizSource]; mode "new" / "wrong" / "all". */
    fun quiz(kind: String, book: Int, index: Int, mode: String = "new")
    fun mock(day: Int)
    fun day(n: Int)
    fun book(id: Int)
    fun row(book: Int, row: Int)
    fun saved()
    fun back()
}

private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val shortFmt = DateTimeFormatter.ofPattern("d MMM")

/** A day's target ids: the first N of its PYQ pool and of its notes MCQ pool (N from settings). */
@Composable
fun dayTarget(day: PlanDay): DayTarget {
    val app = LocalApp.current
    val p = app.store.pyqTarget
    val n = app.store.notesTarget
    val c = app.store.caTarget
    return remember(day.n, p, n, c) {
        DayTarget(
            app.repo.index.day(Origin.PYQ, day.n).take(p),
            app.repo.index.day(Origin.NOTES, day.n).take(n),
            app.repo.index.caTarget(day.n, c),
        )
    }
}

@Composable
fun TodayScreen(nav: Nav) {
    val app = LocalApp.current
    val today = LocalDate.now()
    val plan = app.repo.plan
    val day = app.repo.dayFor(today)
    val beforePlan = today.isBefore(plan.days.first().date)
    val afterPlan = today.isAfter(plan.days.last().date)
    val daysToExam = ChronoUnit.DAYS.between(today, plan.exam).coerceAtLeast(0)
    val target = dayTarget(day)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp)) {
                Text(today.format(dateFmt), style = TextStyle(fontSize = 13.sp, color = C.Muted))
                Text("APPSC MCQ 90", style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.Ink))
            }
        }
        item { HeroCard(day, target, daysToExam, plan.examLabel, beforePlan, afterPlan, nav) }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("${app.store.answeredOn(today)}", "answered today", Modifier.weight(1f))
                MiniStat("${app.store.streak()} days", "streak", Modifier.weight(1f))
                MiniStat("${app.store.wrongIds().size}", "to retry", Modifier.weight(1f)) { nav.quiz("w", 0, 0, "wrong") }
            }
        }
        dayBody(day, target, nav)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(C.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink))
        Text(label, style = TextStyle(fontSize = 12.sp, color = C.Muted))
    }
}

@Composable
private fun HeroCard(
    day: PlanDay,
    target: DayTarget,
    daysToExam: Long,
    examLabel: String,
    beforePlan: Boolean,
    afterPlan: Boolean,
    nav: Nav,
) {
    val store = LocalApp.current.store
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7C2D12), Color(0xFFC2410C))))
            .clickable { nav.day(day.n) }
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        beforePlan -> "Starts ${day.date.format(shortFmt)}"
                        afterPlan -> "Plan complete · final revision"
                        else -> "Today's target"
                    },
                    style = TextStyle(fontSize = 13.sp, color = Color(0xFFFED7AA), fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$daysToExam days to exam",
                    style = TextStyle(fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Day ${day.n} of 90", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White))
            Text(
                listOf(prettyPhase(day), day.focus.titleCase()).filter { it.isNotBlank() }.joinToString(" · "),
                style = TextStyle(fontSize = 14.sp, color = Color(0xFFFFEDD5)),
            )
            if (!day.isMock) {
                val ids = target.pyq + target.notes + target.ca
                val done = store.stats(ids).first
                if (ids.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressLine(done / ids.size.toFloat(), Modifier.weight(1f), color = Color(0xFFFBBF24))
                        Spacer(Modifier.width(10.dp))
                        Text("$done / ${ids.size}", style = TextStyle(fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold))
                    }
                    Text(
                        "MCQs of today's target · exam $examLabel",
                        style = TextStyle(fontSize = 12.sp, color = Color(0xFFFED7AA)),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** The target cards and sections of a plan day; shared by Today and the Day screen. */
fun LazyListScope.dayBody(day: PlanDay, target: DayTarget, nav: Nav) {
    if (day.isMock) {
        item { SectionHeader("Mock day") }
        item { MockCard(day, nav) }
        item { MistakesCard(nav) }
        return
    }
    item { SectionHeader("Daily targets") }
    item { TargetCard(day, target, Origin.PYQ, nav) }
    item { TargetCard(day, target, Origin.NOTES, nav) }
    if (target.ca.isNotEmpty()) item { CurrentAffairsCard(day, target, nav) }
    if (day.type == "revision" || day.type == "sunday") item { MistakesCard(nav) }
    item { SectionHeader("Today's sections (${day.rows.size + if (target.ca.isNotEmpty()) 1 else 0})") }
    items(day.rows, key = { "${it.book}-${it.row}" }) { r ->
        RowItem(r.book, r.row, r.topic, r.priority, nav)
    }
    if (target.ca.isNotEmpty()) item(key = "ca") { CaSectionItem(day, nav) }
}

@Composable
private fun TargetCard(day: PlanDay, target: DayTarget, origin: Origin, nav: Nav) {
    val app = LocalApp.current
    val pyq = origin == Origin.PYQ
    val ids = if (pyq) target.pyq else target.notes
    val (done, correct, wrong) = app.store.stats(ids)
    val total = ids.size
    val extra = app.repo.index.day(origin, day.n).size - total
    PracticeCard(
        title = if (pyq) "PYQ practice" else "Notes MCQ practice",
        subtitle = when {
            total == 0 -> if (pyq) "No PYQs filed under these sections" else "No notes MCQs for these sections"
            done >= total -> "Target done! " + if (extra > 0) "$extra more in these sections" else "Every question here is done"
            else -> "Target $total ${if (pyq) "previous-year questions, APPSC first" else "questions from the Combined Notes"}"
        },
        icon = if (pyq) Icons.Filled.History else Icons.Outlined.AutoStories,
        iconBg = if (pyq) C.ExamBg else C.AccentSoft,
        iconFg = if (pyq) C.ExamInk else C.Accent,
        done = done,
        correct = correct,
        wrong = wrong,
        total = total,
        onStart = if (total == 0) null else {
            {
                if (done >= total && extra > 0) nav.quiz(if (pyq) "xp" else "xn", 0, day.n)
                else nav.quiz(if (pyq) "tp" else "tn", 0, day.n, if (done >= total) "all" else "new")
            }
        },
        onWrong = { nav.quiz(if (pyq) "tp" else "tn", 0, day.n, "wrong") },
        startLabel = when {
            total == 0 -> null
            done == 0 -> "Start · all $total"
            done < total -> "Continue · ${total - done} left"
            extra > 0 -> "Practise $extra more"
            else -> "Practise all again"
        },
    )
}

/** The day's current-affairs block: MCQs from the Current Affairs notes section it names (G-1 … G-18). */
@Composable
private fun CurrentAffairsCard(day: PlanDay, target: DayTarget, nav: Nav) {
    val app = LocalApp.current
    val pools = app.repo.index.dayPools(day.n)
    val ids = target.ca
    val (done, correct, wrong) = app.store.stats(ids)
    val total = ids.size
    val extra = (pools?.ca?.size ?: 0) - total
    val round = pools?.caRound ?: 0
    PracticeCard(
        title = "Current affairs notes MCQs",
        subtitle = (pools?.caTitle ?: "") + when {
            done >= total -> " · target done!" + if (extra > 0) " $extra more in this section" else ""
            round == 0 -> " · first read · target $total"
            else -> " · revision ${round} · next $total questions"
        },
        icon = Icons.Filled.Newspaper,
        iconBg = C.SeeBg,
        iconFg = C.SeeInk,
        done = done,
        correct = correct,
        wrong = wrong,
        total = total,
        onStart = {
            if (done >= total && extra > 0) nav.quiz("xc", 0, day.n)
            else nav.quiz("tc", 0, day.n, if (done >= total) "all" else "new")
        },
        onWrong = { nav.quiz("tc", 0, day.n, "wrong") },
        startLabel = when {
            done == 0 -> "Start · all $total"
            done < total -> "Continue · ${total - done} left"
            extra > 0 -> "Practise $extra more"
            else -> "Practise all again"
        },
    )
}

@Composable
private fun MockCard(day: PlanDay, nav: Nav) {
    val store = LocalApp.current.store
    val last = store.mocks.firstOrNull { it.day == day.n }
    PracticeCard(
        title = "Mock test · ${store.mockSize} questions",
        subtitle = if (last != null) "Last attempt: net %.1f / %d".format(last.net, last.total)
        else "${store.mockSize} minutes · all six subjects · 1/3 negative marking",
        icon = Icons.Filled.Timer,
        iconBg = C.HighSoft,
        iconFg = C.High,
        done = 0,
        correct = 0,
        total = 0,
        onStart = { nav.mock(day.n) },
        startLabel = if (last != null) "Take it again" else "Start the mock",
    )
}

@Composable
private fun MistakesCard(nav: Nav) {
    val store = LocalApp.current.store
    val n = store.wrongIds().size
    PracticeCard(
        title = "Retry mistakes",
        subtitle = if (n == 0) "No wrong answers to retry" else "$n questions you answered wrong, PYQs and notes MCQs",
        icon = Icons.Filled.ErrorOutline,
        iconBg = C.HighSoft,
        iconFg = C.High,
        done = 0,
        correct = 0,
        total = 0,
        onStart = if (n == 0) null else { { nav.quiz("w", 0, 0, "wrong") } },
        startLabel = if (n == 0) null else "Retry $n",
    )
}

/** Today's current-affairs section (from the plan's CA block), listed with the day's sections. */
@Composable
private fun CaSectionItem(day: PlanDay, nav: Nav) {
    val pools = LocalApp.current.repo.index.dayPools(day.n) ?: return
    if (pools.caRow < 0) return
    RowItem(6, pools.caRow, "Current affairs · ${pools.caTitle}", "", nav)
}

/** One section (syllabus row) with its PYQ and notes MCQ progress. */
@Composable
fun RowItem(book: Int, row: Int, title: String, priority: String, nav: Nav) {
    val app = LocalApp.current
    val pyqIds = app.repo.index.row(Origin.PYQ, book, row)
    val notesIds = app.repo.index.row(Origin.NOTES, book, row)
    val total = pyqIds.size + notesIds.size
    val done = app.store.stats(pyqIds).first + app.store.stats(notesIds).first
    Row(
        Modifier.fillMaxWidth().clickable { nav.row(book, row) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (total > 0 && done >= total) C.GreenSoft else C.ExamBg),
            contentAlignment = Alignment.Center,
        ) {
            if (total > 0 && done >= total) Icon(Icons.Filled.CheckCircle, null, tint = C.Green, modifier = Modifier.size(20.dp))
            else Icon(Icons.Filled.Quiz, null, tint = C.ExamInk, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = C.Ink))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PriorityTag(priority)
                Text(
                    "${pyqIds.size} PYQs · ${notesIds.size} notes MCQs",
                    style = TextStyle(fontSize = 12.sp, color = C.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (total > 0) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(done / total.toFloat(), Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("$done/$total", style = TextStyle(fontSize = 11.sp, color = C.Muted))
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Faint)
    }
}

@Composable
fun DayScreen(n: Int, nav: Nav) {
    val app = LocalApp.current
    val plan = app.repo.plan
    val day = app.repo.day(n)
    val target = dayTarget(day)
    Column(Modifier.fillMaxSize()) {
        TopBar("Day ${day.n}", onBack = nav::back, actions = {
            IconButton(onClick = { nav.back(); nav.day(n - 1) }, enabled = n > 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day", tint = if (n > 1) C.Ink else C.Line)
            }
            IconButton(onClick = { nav.back(); nav.day(n + 1) }, enabled = n < plan.days.size) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day", tint = if (n < plan.days.size) C.Ink else C.Line)
            }
        })
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Text("${day.dow}, ${day.date.format(shortFmt)} · ${day.left}", style = TextStyle(fontSize = 13.sp, color = C.Muted))
                    Text(
                        listOf(prettyPhase(day), day.focus.titleCase()).filter { it.isNotBlank() }.joinToString(" · "),
                        style = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = C.Ink),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            dayBody(day, target, nav)
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun PlanScreen(nav: Nav) {
    val app = LocalApp.current
    val plan = app.repo.plan
    val today = LocalDate.now()
    val todayIndex = plan.days.indexOfFirst { it.date == today }
    val listState = rememberLazyListState()
    // all 90 targets at once (needs every bank; done in the background)
    val p = app.store.pyqTarget
    val nt = app.store.notesTarget
    val ct = app.store.caTarget
    val targets = remember(p, nt, ct) {
        plan.days.associate {
            it.n to (app.repo.index.day(Origin.PYQ, it.n).take(p) + app.repo.index.day(Origin.NOTES, it.n).take(nt) + app.repo.index.caTarget(it.n, ct))
        }
    }
    LaunchedEffect(todayIndex) { if (todayIndex > 2) listState.scrollToItem(todayIndex + 1) }

    Column(Modifier.fillMaxSize()) {
        TopBar("90-day plan")
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item {
                Text(
                    "Targets per study day: ${app.store.pyqTarget} PYQs + ${app.store.notesTarget} notes MCQs from that day's sections + ${app.store.caTarget} current affairs MCQs from its CA section (change them in Progress → Settings). Mock days: a timed ${app.store.mockSize}-question paper.",
                    style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = C.Muted),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            var lastPhase = ""
            plan.days.forEach { d ->
                val phase = prettyPhase(d)
                if (phase != lastPhase && d.type != "sunday") {
                    lastPhase = phase
                    item(key = "h-${d.n}") { SectionHeader(phase) }
                }
                item(key = "d-${d.n}") {
                    PlanDayItem(d, targets[d.n] ?: emptyList(), d.date == today, nav)
                    HorizontalDivider(color = C.Line, modifier = Modifier.padding(start = 70.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PlanDayItem(d: PlanDay, ids: List<String>, isToday: Boolean, nav: Nav) {
    val store = LocalApp.current.store
    val done = if (ids.isEmpty()) 0 else store.stats(ids).first
    val mockDone = d.isMock && store.mocks.any { it.day == d.n }
    val complete = (ids.isNotEmpty() && done >= ids.size) || mockDone
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isToday) C.ExamBg else Color.White)
            .clickable { nav.day(d.n) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (complete) C.GreenSoft else if (isToday) C.ExamInk else C.Chip),
            contentAlignment = Alignment.Center,
        ) {
            if (complete) Icon(Icons.Filled.CheckCircle, null, tint = C.Green)
            else Text("${d.n}", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isToday) Color.White else C.Ink))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${d.dow} ${d.date.format(shortFmt)}", style = TextStyle(fontSize = 12.sp, color = C.Muted))
                when (d.type) {
                    "mock" -> Tag("MOCK", C.HighSoft, C.High)
                    "revision" -> Tag("REVISION", C.SeeBg, C.SeeInk)
                    "sunday" -> Tag("SUNDAY", C.MedSoft, C.Med)
                }
                if (isToday) Tag("TODAY", C.ExamInk, Color.White)
            }
            Text(
                d.focus.titleCase().ifBlank { prettyPhase(d) },
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ids.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(done / ids.size.toFloat(), Modifier.weight(1f), color = C.ExamInk)
                    Spacer(Modifier.width(8.dp))
                    Text("$done/${ids.size}", style = TextStyle(fontSize = 11.sp, color = C.Muted))
                }
            } else if (d.isMock) {
                Text(if (mockDone) "Mock taken" else "Mock test", style = TextStyle(fontSize = 12.sp, color = C.Muted))
            }
        }
    }
}

fun prettyPhase(day: PlanDay): String {
    val p = day.phase.trim()
    return when {
        p.startsWith("PHASE", ignoreCase = true) -> {
            val m = Regex("""PHASE\s*(\d+)\s*(.*)""", RegexOption.IGNORE_CASE).find(p)
            if (m != null) "Phase ${m.groupValues[1]} · ${m.groupValues[2].titleCase()}".trimEnd(' ', '·') else p.titleCase()
        }
        else -> p.titleCase()
    }
}

fun String.titleCase(): String = lowercase().split(' ').joinToString(" ") { w ->
    when {
        w in setOf("&", "and", "of", "the", "+", "ir") -> if (w == "ir") "IR" else w
        w.isEmpty() -> w
        else -> w.replaceFirstChar { it.uppercase() }
    }
}.replace("Ap ", "AP ").replace("S&t", "S&T")
