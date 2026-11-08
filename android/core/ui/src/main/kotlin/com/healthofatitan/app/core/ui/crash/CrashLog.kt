package com.healthofatitan.app.core.ui.crash

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * A report of the last failure, kept only on this phone (08.11: «при попытке внести цель —
 * вылетает», and no way to see why). The app writes the stack trace of an uncaught exception, or of
 * one it caught and survived, to a private file; the next start offers to copy it. Nothing is sent
 * anywhere. Only class names and lines of code go in: no text the person typed, no health data.
 */
object CrashLog {
    private const val FILE = "crash_last.txt"
    private const val MAX_CHARS = 12_000

    @Volatile
    private var dir: File? = null

    @Volatile
    private var version: String = "?"

    /** Installs the handler; the previous one still runs (the system dialog, the process ends). */
    fun install(context: Context, versionName: String) {
        dir = context.filesDir
        version = versionName
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write("crash", thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** A failure the app caught and survived: kept so it can still be reported. */
    fun record(where: String, error: Throwable) {
        runCatching { write("caught: $where", Thread.currentThread().name, error) }
    }

    /** The last report, or null. */
    fun last(): String? = file()?.takeIf { it.exists() }?.readText()

    fun clear() {
        file()?.delete()
    }

    /** The report: the exception chain with its frames, the messages left out. */
    fun format(kind: String, thread: String, error: Throwable, version: String, at: Instant): String {
        val out = StringWriter()
        PrintWriter(out).use { w ->
            w.println("HoT $version · $kind · $thread · $at")
            var e: Throwable? = error
            var depth = 0
            while (e != null && depth < 6) {
                w.println((if (depth == 0) "" else "Caused by: ") + e.javaClass.name)
                e.stackTrace.take(40).forEach { w.println("    at $it") }
                e = e.cause
                depth++
            }
        }
        return out.toString().take(MAX_CHARS)
    }

    private fun write(kind: String, thread: String, error: Throwable) {
        val f = file() ?: return
        f.writeText(format(kind, thread, error, version, Instant.now()))
    }

    private fun file(): File? = dir?.let { File(it, FILE) }
}
