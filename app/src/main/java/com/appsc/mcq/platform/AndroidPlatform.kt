package com.appsc.mcq.platform

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.appsc.mcq.data.AssetSource
import com.appsc.mcq.data.KeyValueStore
import com.appsc.mcq.data.ProgressStore
import com.appsc.mcq.data.Repository

// Android implementations of the small platform seams the shared code uses.
// The desktop app has a file with the same package and names (desktop/src/main/kotlin/.../DesktopPlatform.kt).

@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) = BackHandler(enabled, onBack)

private class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("mcq", Context.MODE_PRIVATE)
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun getStringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    override fun putStringSet(key: String, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()
}

fun androidRepository(context: Context) = Repository { path -> context.assets.open(path) }

fun androidStore(context: Context) = ProgressStore(context.filesDir, SharedPrefsStore(context))
