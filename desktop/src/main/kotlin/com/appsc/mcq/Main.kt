package com.appsc.mcq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.appsc.mcq.platform.DesktopHost
import com.appsc.mcq.platform.desktopRepository
import com.appsc.mcq.platform.desktopStore
import com.appsc.mcq.ui.AppContent
import com.appsc.mcq.ui.components.AppState
import com.appsc.mcq.ui.theme.McqTheme

fun main() {
    val app = AppState(desktopRepository(), desktopStore())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "APPSC MCQ 90",
            icon = painterResource("icon.png"),
            state = rememberWindowState(size = DpSize(900.dp, 860.dp), position = WindowPosition(Alignment.Center)),
        ) {
            window.minimumSize = java.awt.Dimension(420, 560)
            McqTheme {
                // The screens are designed for reading width; on a wide window keep them a comfortable column.
                Box(Modifier.fillMaxSize().background(Color(0xFFF1F3F6)), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 760.dp).fillMaxHeight().background(Color.White)) {
                        DesktopHost { AppContent(app) }
                    }
                }
            }
        }
    }
}
