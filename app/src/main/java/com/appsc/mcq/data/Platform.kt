package com.appsc.mcq.data

import java.io.InputStream

/** Read-only bundled files (Android assets, or desktop classpath resources). */
fun interface AssetSource {
    fun open(path: String): InputStream
}

/** Small persistent settings (Android SharedPreferences, or a desktop properties file). */
interface KeyValueStore {
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, value: Set<String>)
}
