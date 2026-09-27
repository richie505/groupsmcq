package com.appsc.mcq.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.mcq.data.Origin
import com.appsc.mcq.data.Question
import com.appsc.mcq.data.QuizSource
import com.appsc.mcq.data.Sets
import com.appsc.mcq.data.Techniques
import com.appsc.mcq.ui.components.Loading
import com.appsc.mcq.ui.components.LocalApp
import com.appsc.mcq.ui.components.Message
import com.appsc.mcq.ui.components.ProgressLine
import com.appsc.mcq.ui.components.Tag
import com.appsc.mcq.ui.components.TopBar
import com.appsc.mcq.ui.theme.C

fun quizTitle(src: QuizSource): String = when (src.kind) {
    "tp" -> "Day ${src.index} · PYQ target"
    "tn" -> "Day ${src.index} · Notes MCQ target"
    "xp" -> "Day ${src.index} · more PYQs"
    "xn" -> "Day ${src.index} · more notes MCQs"
    "rp" -> "Section PYQs"
    "rn" -> "Section notes MCQs"
    "u" -> "Topic PYQs"
    "w" -> "Mistakes"
    else -> "Practice"
}

@Composable
fun QuizScreen(src: QuizSource, mode: String, nav: Nav) {
    val app = LocalApp.current
    val store = app.store
    // Every id of the source; cheap (index only), so it is simply recomputed.
    val pool = remember(src, store.pyqTarget, store.notesTarget) { Sets.pool(app.repo, store, src) }
    var currentMode by rememberSaveable { mutableStateOf(mode) }
    var round by rememberSaveable { mutableIntStateOf(0) }
    // The round's ids are frozen and saved, so rotation or the app being killed never reshuffles the run.
    val setIds = rememberSaveable(round, currentMode) { ArrayList(Sets.pick(pool, store, currentMode)) }
    val questions by produceState<List<Question>?>(null, setIds) { value = app.repo.questions(setIds) }

    Column(Modifier.fillMaxSize()) {
        TopBar(quizTitle(src), onBack = nav::back)
        val qs = questions
        when {
            pool.isEmpty() -> Message(
                when {
                    src.kind == "w" -> "No mistakes to retry. Well done!"
                    src.kind.endsWith("n") -> "No notes MCQs in this set."
                    else -> "No questions here."
                },
            )
            setIds.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (currentMode == "wrong") "No wrong answers left to retry." else "All ${pool.size} questions here are done.",
                    style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = C.Muted),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { currentMode = "all"; round++ }, shape = RoundedCornerShape(12.dp)) { Text("Practise all ${pool.size} again") }
            }
            qs == null -> Loading()
            qs.isEmpty() -> Message("These questions could not be loaded.")
            else -> QuizRound(
                key = "$round-$currentMode",
                set = qs,
                pool = pool,
                mode = currentMode,
                onNext = { m ->
                    currentMode = m
                    round++
                },
                onDone = nav::back,
            )
        }
    }
}

@Composable
private fun QuizRound(
    key: String,
    set: List<Question>,
    pool: List<String>,
    mode: String,
    onNext: (String) -> Unit,
    onDone: () -> Unit,
) {
    val store = LocalApp.current.store
    var index by rememberSaveable(key) { mutableIntStateOf(0) }
    var picks by rememberSaveable(key) { mutableStateOf(IntArray(set.size) { -1 }) }
    if (picks.size != set.size) picks = IntArray(set.size) { -1 }
    val listState = rememberLazyListState()
    LaunchedEffect(index) { listState.scrollToItem(0) }

    if (index >= set.size) {
        Results(set, picks.toList(), pool, mode, onNext, onDone)
        return
    }
    val q = set[index]
    val picked = picks[index]
    val answered = picked >= 0

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Question ${index + 1} of ${set.size}",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Accent),
                    modifier = Modifier.weight(1f),
                )
                val score = set.indices.count { set[it].scored && picks[it] >= 0 && picks[it] == set[it].answer }
                Text("Score $score", style = TextStyle(fontSize = 13.sp, color = C.Muted))
            }
            Spacer(Modifier.height(6.dp))
            ProgressLine((index + if (answered) 1 else 0) / set.size.toFloat())
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            item(key = "q-${q.id}-$index") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    QuestionHeader(q)
                    QuestionBody(q)
                    Spacer(Modifier.height(14.dp))
                    q.options.forEachIndexed { i, opt ->
                        OptionCard(i, opt, picked, if (q.scored) q.answer else -1, reveal = true) {
                            if (picks[index] < 0) {
                                picks = picks.copyOf().also { it[index] = i }
                                store.record(q, i)
                            }
                        }
                    }
                    if (answered) {
                        Spacer(Modifier.height(8.dp))
                        if (q.scored) Explanation(q, picked) else UnscoredNote(q)
                    } else {
                        HintBox(q)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        NavButtons(
            index = index,
            last = set.size - 1,
            answered = answered,
            onPrev = { if (index > 0) index-- },
            onSkip = { index++ },
            onNext = { index++ },
        )
    }
}

@Composable
internal fun NavButtons(index: Int, last: Int, answered: Boolean, onPrev: () -> Unit, onSkip: () -> Unit, onNext: () -> Unit, nextLabel: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onPrev,
            enabled = index > 0,
            modifier = Modifier.weight(1f).height(50.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) { Text("Previous", color = if (index > 0) C.Ink else C.Faint, maxLines = 1, style = TextStyle(fontSize = 14.sp)) }
        if (!answered) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Skip", color = C.Muted, maxLines = 1, style = TextStyle(fontSize = 14.sp)) }
        }
        Button(
            onClick = onNext,
            enabled = answered,
            modifier = Modifier.weight(1.4f).height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Accent),
        ) {
            Text(nextLabel ?: if (index == last) "See results" else "Next", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

private val TYPE_LABEL = mapOf(
    "statements" to "Statements", "assertion_reason" to "Assertion–Reason", "match" to "Match", "chronology" to "Chronology",
    "negative" to "NOT / EXCEPT", "odd_one_out" to "Odd one out", "confusable" to "Confusable pair", "numerical" to "Numbers",
    "application" to "Application", "comparison" to "Comparison",
)

private val SUBJECT = mapOf(1 to "History", 2 to "Polity", 3 to "Economy", 4 to "Geography", 5 to "Sci-Tech & Env", 6 to "Current Affairs")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun QuestionHeader(q: Question) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        if (q.origin == Origin.PYQ) {
            Tag("PYQ", C.ExamBg, C.ExamInk)
            if (q.appsc) Tag("APPSC", C.ExamBg, C.ExamInk)
            if (q.source.isNotBlank()) Tag(q.source)
            if (q.cancelled) Tag("Cancelled", C.HighSoft, C.High) else if (!q.scored) Tag("No key", C.MedSoft, C.Med)
        } else {
            Tag("Notes MCQ", C.AccentSoft, C.Accent)
            TYPE_LABEL[q.type]?.let { Tag(it) }
        }
        SUBJECT[q.book]?.let { Tag(it) }
    }
}

@Composable
internal fun QuestionBody(q: Question) {
    Text(q.stem, style = TextStyle(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium, color = Color.Black))
    if (q.table.isNotEmpty()) QuestionTable(q.table)
}

/** [answer] < 0 means there is no key to show (unscored, or a mock before submitting). */
@Composable
internal fun OptionCard(i: Int, text: String, picked: Int, answer: Int, reveal: Boolean, onClick: () -> Unit) {
    val answered = picked >= 0 && reveal
    val isAnswer = i == answer
    val isPicked = i == picked
    val (bg, border, ink) = when {
        !reveal && isPicked -> Triple(C.AccentSoft, C.Accent, C.Accent)
        answered && answer < 0 && isPicked -> Triple(C.AccentSoft, C.Accent, C.Accent)
        answered && isAnswer -> Triple(C.GreenSoft, C.Green, C.Green)
        answered && isPicked -> Triple(C.HighSoft, C.High, C.High)
        else -> Triple(Color.White, C.Line, C.Ink)
    }
    Row(
        Modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, border, RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = !answered, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val filled = (answered && (isAnswer || isPicked)) || (!reveal && isPicked)
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (filled) border else C.Chip), contentAlignment = Alignment.Center) {
            Text("${i + 1}", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.White else C.Muted))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = ink), modifier = Modifier.weight(1f))
        if (answered && isAnswer) Icon(Icons.Filled.CheckCircle, null, tint = C.Green, modifier = Modifier.size(22.dp))
        if (answered && isPicked && !isAnswer && answer >= 0) Icon(Icons.Filled.Cancel, null, tint = C.High, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun UnscoredNote(q: Question) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.MedSoft).padding(14.dp)) {
        Text(
            if (q.cancelled) "Cancelled by APPSC — practice only" else "No official answer key",
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Med),
        )
        Text(
            "This question is not scored. Check the answer in your notes.",
            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Body),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun Explanation(q: Question, picked: Int) {
    val correct = picked == q.answer
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (correct) C.GreenSoft else C.HighSoft).padding(14.dp),
    ) {
        Text(
            when {
                correct -> "Correct!"
                picked < 0 -> "Not answered · Answer: (${q.answer + 1}) ${q.options.getOrElse(q.answer) { "" }}"
                else -> "Answer: (${q.answer + 1}) ${q.options.getOrElse(q.answer) { "" }}"
            },
            style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = if (correct) C.Green else C.High),
        )
        if (q.explanation.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Label("EXPLANATION", C.Muted)
            q.explanation.split('\n').filter { it.isNotBlank() }.forEach {
                Text(it, style = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = C.Body), modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (q.technique.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Label("TECHNIQUE THIS QUESTION TRAINS", C.Accent)
            Text(q.technique, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Body), modifier = Modifier.padding(top = 4.dp))
        }
        val review = remember(q.id, picked) { Techniques.review(q, picked) }
        if (review.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Label("WHY IT WENT WRONG · TECHNIQUE", C.Accent)
            review.forEach {
                Text("• $it", style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Body), modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (q.notes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Label("EXAM NOTE", C.ExamInk)
            q.notes.forEach {
                Text("• $it", style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Body), modifier = Modifier.padding(top = 3.dp))
            }
        }
        if (q.section.isNotBlank()) {
            Text(
                "From the notes: ${q.section}",
                style = TextStyle(fontSize = 12.sp, color = C.Muted),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String, color: Color) {
    Text(text, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.8.sp))
}

/** "Stuck?" hint from the MCQ techniques guide; shown only before answering and never reveals the key. */
@Composable
private fun HintBox(q: Question) {
    val hints = remember(q.id) { Techniques.hints(q) }
    if (hints.isEmpty()) return
    var open by rememberSaveable(q.id) { mutableStateOf(false) }
    Spacer(Modifier.height(10.dp))
    if (!open) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp)) {
            Text("Stuck? Show a hint", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Accent))
        }
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.AccentSoft).padding(14.dp)) {
        Label("HINT · MCQ TECHNIQUE", C.Accent)
        hints.forEach {
            Text("• $it", style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Body), modifier = Modifier.padding(top = 4.dp))
        }
        Text("Knowledge first: use these only when you are stuck.", style = TextStyle(fontSize = 12.sp, color = C.Muted), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun QuestionTable(rows: List<List<String>>) {
    val cols = rows.maxOf { it.size }
    Box(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, C.Line, RoundedCornerShape(10.dp))
            .horizontalScroll(rememberScrollState()),
    ) {
        Column {
            rows.forEachIndexed { ri, r ->
                Row(Modifier.height(IntrinsicSize.Min).background(if (ri == 0) C.AccentSoft else if (ri % 2 == 0) C.Surface else Color.White)) {
                    for (c in 0 until cols) {
                        Text(
                            r.getOrElse(c) { "" },
                            style = TextStyle(
                                fontSize = 14.sp, lineHeight = 19.sp, color = if (ri == 0) C.Navy else C.Body,
                                fontWeight = if (ri == 0) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            modifier = Modifier.width(if (cols <= 2) 160.dp else 130.dp).fillMaxHeight().padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Results(
    set: List<Question>,
    picks: List<Int>,
    pool: List<String>,
    mode: String,
    onNext: (String) -> Unit,
    onDone: () -> Unit,
) {
    val store = LocalApp.current.store
    val correct = set.indices.count { set[it].scored && picks[it] == set[it].answer }
    val wrong = set.indices.count { set[it].scored && picks[it] >= 0 && picks[it] != set[it].answer }
    val unscored = set.count { !it.scored }
    val skipped = set.size - correct - wrong - unscored
    val (attempted, poolCorrect, poolWrong) = store.stats(pool)
    val remaining = pool.size - attempted
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreCircle("$correct/${set.size - unscored}")
                Spacer(Modifier.height(12.dp))
                Text(
                    "Net score ${"%.2f".format(correct - wrong / 3f)} with 1/3 negative",
                    style = TextStyle(fontSize = 15.sp, color = C.Ink, fontWeight = FontWeight.Medium),
                )
                Text(
                    "$correct correct · $wrong wrong · $skipped skipped" + if (unscored > 0) " · $unscored unscored" else "",
                    style = TextStyle(fontSize = 13.sp, color = C.Muted),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(18.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Surface).padding(14.dp)) {
                    Text("This set", style = TextStyle(fontSize = 13.sp, color = C.Muted))
                    Text(
                        "$attempted of ${pool.size} done · ${if (attempted == 0) 0 else poolCorrect * 100 / attempted}% accuracy",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink),
                    )
                    Spacer(Modifier.height(8.dp))
                    ProgressLine(if (pool.isEmpty()) 0f else attempted / pool.size.toFloat())
                }
                Spacer(Modifier.height(18.dp))
                if (remaining > 0) {
                    Button(
                        onClick = { onNext("new") },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.Accent),
                    ) { Text("Practise the $remaining unattempted", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)) }
                    Spacer(Modifier.height(10.dp))
                }
                if (poolWrong > 0) {
                    OutlinedButton(
                        onClick = { onNext("wrong") },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Retry wrong answers ($poolWrong)", color = C.High) }
                    Spacer(Modifier.height(10.dp))
                }
                if (remaining == 0 && mode != "all") {
                    OutlinedButton(
                        onClick = { onNext("all") },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Practise all ${pool.size} again", color = C.Ink) }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("Done", color = C.Ink)
                }
            }
        }
        reviewList(set, picks)
    }
}

@Composable
internal fun ScoreCircle(text: String) {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.size(120.dp).clip(CircleShape).background(C.AccentSoft), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.Accent))
    }
}

internal fun androidx.compose.foundation.lazy.LazyListScope.reviewList(set: List<Question>, picks: List<Int>) {
    item {
        Text(
            "REVIEW",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Muted, letterSpacing = 1.sp),
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
        )
    }
    set.forEachIndexed { i, q ->
        item(key = "r-${q.id}-$i") {
            val ok = q.scored && picks[i] == q.answer
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Icon(
                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null,
                    tint = if (ok) C.Green else if (picks[i] < 0) C.Faint else C.High,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(q.stem, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Ink), maxLines = 3)
                    Text(
                        if (!q.scored) (if (q.cancelled) "Cancelled by APPSC" else "No official key") else "Answer: ${q.options.getOrElse(q.answer) { "" }}",
                        style = TextStyle(fontSize = 13.sp, color = C.Green),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
    item { Spacer(Modifier.height(24.dp)) }
}
