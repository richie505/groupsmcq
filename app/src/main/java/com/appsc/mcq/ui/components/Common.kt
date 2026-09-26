package com.appsc.mcq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.mcq.data.ProgressStore
import com.appsc.mcq.data.Repository
import com.appsc.mcq.ui.theme.C

class AppState(val repo: Repository, val store: ProgressStore)

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState not provided") }

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Ink) }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = C.Ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            actions()
        }
        HorizontalDivider(color = C.Line)
    }
}

@Composable
fun Tag(text: String, bg: Color = C.Chip, fg: Color = C.Muted, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg),
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun PriorityTag(priority: String) {
    when (priority.uppercase()) {
        "HIGH" -> Tag("HIGH", C.HighSoft, C.High)
        "MED" -> Tag("MED", C.MedSoft, C.Med)
        "LIGHT" -> Tag("LIGHT", C.LightSoft, C.Light)
        "" -> {}
        else -> Tag(priority)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = C.Accent) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = if (fraction >= 1f) C.Green else color,
        trackColor = C.Chip,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Muted, letterSpacing = 1.sp),
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, C.Line, RoundedCornerShape(14.dp))
            .background(Color.White)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Accent) }
}

@Composable
fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = C.Muted))
    }
}

@Composable
fun StatBox(value: String, label: String, icon: ImageVector?, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(C.Surface).padding(14.dp)) {
        if (icon != null) {
            Icon(icon, null, tint = C.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
        }
        Text(value, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Ink))
        Text(label, style = TextStyle(fontSize = 12.sp, color = C.Muted))
    }
}

/**
 * A practice entry: title, subtitle, progress (done of total, with accuracy) and a start action.
 * [onWrong] adds a "retry wrong" link when there are wrong answers.
 */
@Composable
fun PracticeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    done: Int,
    correct: Int,
    total: Int,
    onStart: (() -> Unit)?,
    onWrong: (() -> Unit)? = null,
    startLabel: String? = null,
) {
    Card(onClick = onStart) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                    if (total > 0 && done >= total) Icon(Icons.Filled.CheckCircle, null, tint = C.Green)
                    else Icon(icon, null, tint = iconFg)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
                    Text(subtitle, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = C.Muted))
                }
                if (onStart != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Faint)
            }
            if (total > 0) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(done / total.toFloat(), Modifier.weight(1f), color = iconFg)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "$done/$total" + if (done > 0) " · ${correct * 100 / done}%" else "",
                        style = TextStyle(fontSize = 12.sp, color = C.Muted),
                    )
                }
            }
            if (startLabel != null && onStart != null) {
                Text(
                    startLabel,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Accent),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (onWrong != null && done - correct > 0) {
                Text(
                    "Retry ${done - correct} wrong",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.High),
                    modifier = Modifier.padding(top = 10.dp).clickable(onClick = onWrong),
                )
            }
        }
    }
}
