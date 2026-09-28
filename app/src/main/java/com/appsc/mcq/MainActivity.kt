package com.appsc.mcq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.appsc.mcq.platform.androidRepository
import com.appsc.mcq.platform.androidStore
import com.appsc.mcq.ui.AppContent
import com.appsc.mcq.ui.components.AppState
import com.appsc.mcq.ui.theme.McqTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = AppState(androidRepository(applicationContext), androidStore(applicationContext))
        setContent { McqTheme { AppContent(app) } }
    }
}
