package com.healthofatitan.app.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.healthofatitan.app.core.ui.crash.CrashLog

const val CRASH_DIALOG_TAG = "crash_report"

/** Texts of the report of the last failure (neutral form; draft for Agents 16 and 10). */
internal object CrashTexts {
    const val TITLE = "HoT закрылся с ошибкой"
    const val BODY = "Прошлый раз приложение закрылось с ошибкой. Отчёт — только строки кода, без твоих записей. " +
        "Его можно скопировать и прислать разработчику, чтобы это исправить."
    const val COPY = "Скопировать отчёт"
    const val CLOSE = "Закрыть"
    const val LABEL = "Отчёт HoT"
}

/** Shown once on the start after a failure; the report is removed when the dialog closes. */
@Composable
internal fun CrashReportDialog(report: String? = remember { CrashLog.last() }) {
    var open by remember { mutableStateOf(report != null) }
    if (!open || report == null) return
    val context = LocalContext.current
    val close = {
        CrashLog.clear()
        open = false
    }
    AlertDialog(
        modifier = Modifier.testTag(CRASH_DIALOG_TAG),
        onDismissRequest = close,
        title = { Text(CrashTexts.TITLE) },
        text = { Text(CrashTexts.BODY) },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(CrashTexts.LABEL, report))
                }
                close()
            }) { Text(CrashTexts.COPY) }
        },
        dismissButton = { TextButton(onClick = close) { Text(CrashTexts.CLOSE) } },
    )
}
