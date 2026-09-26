package com.appsc.mcq.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.mcq.data.Origin
import com.appsc.mcq.ui.components.Card
import com.appsc.mcq.ui.components.Loading
import com.appsc.mcq.ui.components.LocalApp
import com.appsc.mcq.ui.components.PracticeCard
import com.appsc.mcq.ui.components.ProgressLine
import com.appsc.mcq.ui.components.SectionHeader
import com.appsc.mcq.ui.components.TopBar
import com.appsc.mcq.ui.theme.C

/** Unique question ids of a subject from one source. */
@Composable
private fun rememberBookIds(book: Int, origin: Origin): List<String>? {
    val app = LocalApp.current
    val ids by produceState<List<String>?>(null, book, origin) {
        val b = app.repo.bank(origin, book)
        value = (b.rows.values.flatten() + b.units.values.flatten()).map { it.id }.distinct()
    }
    return ids
}

@Composable
fun SubjectsScreen(nav: Nav) {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize()) {
        TopBar("Subjects")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "The six books of the Combined Notes. Every section has its PYQs and its notes MCQs; each question appears once even when it is filed under two sections.",
                    style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = C.Muted),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            items(app.repo.catalog, key = { it.id }) { b ->
                val pyq = rememberBookIds(b.id, Origin.PYQ)
                val notes = rememberBookIds(b.id, Origin.NOTES)
                Card(onClick = { nav.book(b.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(C.AccentSoft),
                                contentAlignment = Alignment.Center,
                            ) { Text("${b.id}", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Accent)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.short, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
                                Text(
                                    if (pyq == null || notes == null) "…" else "${pyq.size} PYQs · ${notes.size} notes MCQs · ${b.rows.size} sections",
                                    style = TextStyle(fontSize = 13.sp, color = C.Muted),
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Faint)
                        }
                        if (pyq != null && notes != null) {
                            val ids = pyq + notes
                            val (done, correct) = app.store.stats(ids)
                            if (ids.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ProgressLine(done / ids.size.toFloat(), Modifier.weight(1f))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "$done/${ids.size}" + if (done > 0) " · ${correct * 100 / done}%" else "",
                                        style = TextStyle(fontSize = 12.sp, color = C.Muted),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun BookScreen(id: Int, nav: Nav) {
    val app = LocalApp.current
    val b = app.repo.catalog[id - 1]
    val unitCounts by produceState<Map<Int, Int>?>(null, id) {
        value = app.repo.bank(Origin.PYQ, id).units.mapValues { it.value.size }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(b.short, onBack = nav::back)
        LazyColumn(Modifier.fillMaxSize()) {
            b.units.forEachIndexed { ui, u ->
                item(key = "u$ui") { SectionHeader("${u.code} · ${u.title}") }
                val general = unitCounts?.get(ui) ?: 0
                if (general > 0) {
                    item(key = "g$ui") {
                        val ids by produceState<List<String>?>(null, id, ui) { value = app.repo.unitQuestions(id, ui).map { it.id } }
                        val (done, correct) = ids?.let { app.store.stats(it) } ?: (0 to 0)
                        PracticeCard(
                            title = "General PYQs of this topic",
                            subtitle = "$general questions not tied to one section",
                            icon = Icons.Filled.History,
                            iconBg = C.ExamBg,
                            iconFg = C.ExamInk,
                            done = done,
                            correct = correct,
                            total = ids?.size ?: 0,
                            onStart = { nav.quiz("u", id, ui) },
                            onWrong = { nav.quiz("u", id, ui, "wrong") },
                        )
                    }
                }
                items(b.rows.filter { it.unitIndex == ui }, key = { "r${it.index}" }) { r ->
                    RowItem(id, r.index, r.title, "", nav)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun RowScreen(book: Int, row: Int, nav: Nav) {
    val app = LocalApp.current
    val info = app.repo.rowInfo(book, row)
    val ids by produceState<Pair<List<String>, List<String>>?>(null, book, row) {
        value = app.repo.rowQuestions(Origin.PYQ, book, row).map { it.id } to app.repo.rowQuestions(Origin.NOTES, book, row).map { it.id }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Section", onBack = nav::back)
        val c = ids
        if (c == null) {
            Loading()
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                    Text(
                        (info?.codes?.joinToString(" · ") ?: "") + (info?.tag?.let { if (it.isNotBlank()) " · $it" else "" } ?: ""),
                        style = TextStyle(fontSize = 12.sp, color = C.Muted),
                    )
                    Text(
                        info?.title ?: "",
                        style = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, color = C.Ink),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            item {
                val (done, correct) = app.store.stats(c.first)
                PracticeCard(
                    title = "PYQs",
                    subtitle = "${c.first.size} previous-year questions · APPSC first",
                    icon = Icons.Filled.History,
                    iconBg = C.ExamBg,
                    iconFg = C.ExamInk,
                    done = done,
                    correct = correct,
                    total = c.first.size,
                    onStart = if (c.first.isEmpty()) null else { { nav.quiz("rp", book, row) } },
                    onWrong = { nav.quiz("rp", book, row, "wrong") },
                    startLabel = if (c.first.isEmpty()) null else if (done >= c.first.size) "Practise all again" else "Next 25",
                )
            }
            item {
                val (done, correct) = app.store.stats(c.second)
                PracticeCard(
                    title = "Notes MCQs",
                    subtitle = if (c.second.isEmpty()) "Not generated yet for this section" else "${c.second.size} questions from the Combined Notes, APPSC-style",
                    icon = Icons.Outlined.AutoStories,
                    iconBg = C.AccentSoft,
                    iconFg = C.Accent,
                    done = done,
                    correct = correct,
                    total = c.second.size,
                    onStart = if (c.second.isEmpty()) null else { { nav.quiz("rn", book, row) } },
                    onWrong = { nav.quiz("rn", book, row, "wrong") },
                    startLabel = if (c.second.isEmpty()) null else if (done >= c.second.size) "Practise all again" else "Next 25",
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
