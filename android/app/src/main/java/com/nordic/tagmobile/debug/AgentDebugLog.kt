package com.nordic.tagmobile.debug

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Session debug NDJSON writer for Cursor debug mode (session a42f64). */
object AgentDebugLog {
    private const val SESSION = "a42f64"
    private var file: File? = null
    private var count = 0

    fun init(context: Context) {
        if (file != null) return
        // Prefer app-specific external dir (always writable on Android 11).
        // Also try ear_app if the folder already exists and is writable.
        val candidates = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, "debug") },
            File("/storage/emulated/0/ear_app"),
            File(context.filesDir, "debug"),
        )
        for (dir in candidates) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, "debug-a42f64.log")
                f.appendText("")
                file = f
                break
            } catch (_: Exception) {
            }
        }
    }

    fun log(hypothesisId: String, location: String, message: String, data: Map<String, Any?> = emptyMap()) {
        val f = file ?: return
        if (count > 200) return
        count++
        try {
            val obj = JSONObject()
                .put("sessionId", SESSION)
                .put("runId", "scan-pre")
                .put("hypothesisId", hypothesisId)
                .put("location", location)
                .put("message", message)
                .put("timestamp", System.currentTimeMillis())
            val d = JSONObject()
            data.forEach { (k, v) -> d.put(k, v?.toString() ?: "null") }
            obj.put("data", d)
            f.appendText(obj.toString() + "\n")
        } catch (_: Exception) {
        }
    }

    fun path(): String = file?.absolutePath ?: "(no debug file)"
}
