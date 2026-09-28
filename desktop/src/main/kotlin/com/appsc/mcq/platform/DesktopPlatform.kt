package com.appsc.mcq.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.appsc.mcq.data.AssetSource
import com.appsc.mcq.data.KeyValueStore
import com.appsc.mcq.data.ProgressStore
import com.appsc.mcq.data.Repository
import java.io.File
import java.io.FileNotFoundException
import java.util.Properties

// Desktop implementations of the platform seams (the Android ones are in app/.../platform/AndroidPlatform.kt).

/** Windows has no system back button; screens use their on-screen back arrows (and Esc, see Main.kt). */
@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) {}

/** Where progress is kept: %APPDATA%\APPSC MCQ 90 on Windows (survives app updates), ~/.appsc-mcq-90 elsewhere. */
fun desktopDataDir(): File {
    val base = System.getenv("APPDATA")?.let { File(it, "APPSC MCQ 90") } ?: File(System.getProperty("user.home"), ".appsc-mcq-90")
    return base.apply { mkdirs() }
}

/** Settings in a small properties file next to the answer log. */
private class PropertiesStore(private val file: File) : KeyValueStore {
    private val props = Properties().apply { runCatching { if (file.exists()) file.inputStream().use { load(it) } } }

    @Synchronized private fun save() = runCatching { file.outputStream().use { props.store(it, "APPSC MCQ 90 settings") } }

    override fun getInt(key: String, default: Int) = props.getProperty(key)?.toIntOrNull() ?: default
    @Synchronized override fun putInt(key: String, value: Int) { props.setProperty(key, value.toString()); save() }
    override fun getStringSet(key: String): Set<String> =
        props.getProperty(key)?.split('\u0001')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    @Synchronized override fun putStringSet(key: String, value: Set<String>) { props.setProperty(key, value.joinToString("\u0001")); save() }
}

/** The question bank is packed into the app as classpath resources (the same files as the Android assets). */
fun desktopRepository() = Repository { path ->
    Repository::class.java.getResourceAsStream("/$path") ?: throw FileNotFoundException(path)
}

fun desktopStore(dir: File = desktopDataDir()) = ProgressStore(dir, PropertiesStore(File(dir, "settings.properties")))

/**
 * Android gives every screen a lifecycle and a view-model store (the navigation library needs both);
 * on the desktop the app provides its own, always "resumed" while the window is open.
 */
@Composable
fun DesktopHost(content: @Composable () -> Unit) {
    val owners = remember {
        object : LifecycleOwner, ViewModelStoreOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
            override val viewModelStore = ViewModelStore()
        }
    }
    CompositionLocalProvider(LocalLifecycleOwner provides owners, LocalViewModelStoreOwner provides owners, content = content)
}
