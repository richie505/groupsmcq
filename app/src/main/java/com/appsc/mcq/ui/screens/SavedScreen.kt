package com.appsc.mcq.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.mcq.data.Question
import com.appsc.mcq.ui.components.Loading
import com.appsc.mcq.ui.components.LocalApp
import com.appsc.mcq.ui.components.Message
import com.appsc.mcq.ui.components.TopBar
import com.appsc.mcq.ui.theme.C

private val FILTERS = listOf("All", "History", "Polity", "Economy", "Geography", "Sci-Tech & Env", "Current Affairs")

/**
 * Bookmarked questions for revision: read-only, each with its answer and explanation.
 * Tapping ★ removes a question; it stays on screen (greyed) until you leave, so a slip can be undone.
 */
@Composable
fun SavedScreen(nav: Nav) {
    val app = LocalApp.current
    // Freeze the list for this visit, so un-starring does not make items jump away mid-read.
    val ids = remember { app.store.savedIds() }
    val questions by produceState<List<Question>?>(null, ids) { value = app.repo.questions(ids) }
    var filter by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Saved for revision", onBack = nav::back)
        val qs = questions
        when {
            ids.isEmpty() -> Message("Nothing saved yet. Tap ☆ on any question to keep it here for revision.")
            qs == null -> Loading()
            else -> {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FILTERS.forEachIndexed { i, f ->
                        val n = if (i == 0) qs.size else qs.count { it.book == i }
                        if (i == 0 || n > 0) {
                            Text(
                                "$f ($n)",
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (filter == i) Color.White else C.Ink),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (filter == i) C.Accent else C.Chip)
                                    .clickable { filter = i }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = C.Line)
                val shown = if (filter == 0) qs else qs.filter { it.book == filter }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { q -> SavedItem(q) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SavedItem(q: Question) {
    val store = LocalApp.current.store
    var open by rememberSaveable(q.id) { mutableStateOf(false) }
    val kept = store.isSaved(q.id)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (kept) Color.White else C.Surface)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
    ) {
        QuestionHeader(q)
        Text(q.stem, style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = if (kept) C.Ink else C.Faint))
        Spacer(Modifier.height(6.dp))
        q.options.forEachIndexed { i, o ->
            val key = i == q.answer
            Text(
                "(${i + 1}) $o",
                style = TextStyle(
                    fontSize = 14.sp, lineHeight = 20.sp,
                    color = if (key) C.Green else C.Body,
                    fontWeight = if (key) FontWeight.SemiBold else FontWeight.Normal,
                ),
                modifier = Modifier.padding(top = 2.dp, end = 12.dp),
            )
        }
        val more = q.explanation.isNotBlank() || q.technique.isNotBlank() || q.notes.isNotEmpty()
        if (more) {
            Text(
                if (open) "Hide explanation" else "Show explanation",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Accent),
                modifier = Modifier.padding(top = 8.dp).clickable { open = !open },
            )
            if (open) {
                Column(
                    Modifier.padding(top = 6.dp, end = 12.dp).clip(RoundedCornerShape(10.dp)).background(C.Surface).padding(12.dp),
                ) {
                    if (q.explanation.isNotBlank()) Text(q.explanation, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Body))
                    if (q.technique.isNotBlank()) {
                        Text("TECHNIQUE", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Accent), modifier = Modifier.padding(top = 8.dp))
                        Text(q.technique, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Body))
                    }
                    q.notes.forEach { Text("• $it", style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = C.Body), modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
    HorizontalDivider(color = C.Line)
}
