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
import com.appsc.mcq.ui.components.Loading
import com.appsc.mcq.ui.components.LocalApp
import com.appsc.mcq.ui.components.PracticeCard
import com.appsc.mcq.ui.components.ProgressLine
import com.appsc.mcq.ui.components.SectionHeader
import com.appsc.mcq.ui.components.TopBar
import com.appsc.mcq.ui.theme.C

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
                val pyq = remember(b.id) { app.repo.index.book(Origin.PYQ, b.id) }
                val notes = remember(b.id) { app.repo.index.book(Origin.NOTES, b.id) }
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
                                    "${pyq.size} PYQs · ${notes.size} notes MCQs · ${b.rows.size} sections",
                                    style = TextStyle(fontSize = 13.sp, color = C.Muted),
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Faint)
                        }
                        run {
                            val (dp, cp) = app.store.stats(pyq)
                            val (dn, cn) = app.store.stats(notes)
                            val done = dp + dn
                            val correct = cp + cn
                            val total = pyq.size + notes.size
                            if (total > 0) {
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ProgressLine(done / total.toFloat(), Modifier.weight(1f))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "$done/$total" + if (done > 0) " · ${correct * 100 / done}%" else "",
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
    val b = app.repo.catalog.getOrNull(id - 1)
    Column(Modifier.fillMaxSize()) {
        TopBar(b?.short ?: "Subject", onBack = nav::back)
        if (b == null) {
            Loading()
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            b.units.forEachIndexed { ui, u ->
                item(key = "u$ui") { SectionHeader("${u.code} · ${u.title}") }
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
    val pyqIds = app.repo.index.row(Origin.PYQ, book, row)
    val notesIds = app.repo.index.row(Origin.NOTES, book, row)
    Column(Modifier.fillMaxSize()) {
        TopBar("Section", onBack = nav::back)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                    Text(
                        listOfNotNull(info?.codes?.joinToString(" · "), info?.tag).filter { it.isNotBlank() }.joinToString(" · "),
                        style = TextStyle(fontSize = 12.sp, color = C.Muted),
                    )
                    Text(
                        info?.title ?: "",
                        style = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, color = C.Ink),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            item { SourceCard(Origin.PYQ, pyqIds, "rp", book, row, nav) }
            item { SourceCard(Origin.NOTES, notesIds, "rn", book, row, nav) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SourceCard(origin: Origin, ids: List<String>, kind: String, book: Int, row: Int, nav: Nav) {
    val (done, correct, wrong) = LocalApp.current.store.stats(ids)
    val pyq = origin == Origin.PYQ
    PracticeCard(
        title = if (pyq) "PYQs" else "Notes MCQs",
        subtitle = when {
            ids.isEmpty() -> if (pyq) "No PYQs filed under this section" else "No notes MCQs for this section"
            pyq -> "${ids.size} previous-year questions"
            else -> "${ids.size} APPSC-style questions from the Combined Notes"
        },
        icon = if (pyq) Icons.Filled.History else Icons.Outlined.AutoStories,
        iconBg = if (pyq) C.ExamBg else C.AccentSoft,
        iconFg = if (pyq) C.ExamInk else C.Accent,
        done = done,
        correct = correct,
        wrong = wrong,
        total = ids.size,
        onStart = if (ids.isEmpty()) null else { { nav.quiz(kind, book, row, if (done >= ids.size) "all" else "new") } },
        onWrong = { nav.quiz(kind, book, row, "wrong") },
        startLabel = when {
            ids.isEmpty() -> null
            done == 0 -> "Start · all ${ids.size}"
            done < ids.size -> "Continue · ${ids.size - done} left"
            else -> "Practise all again"
        },
    )
}
