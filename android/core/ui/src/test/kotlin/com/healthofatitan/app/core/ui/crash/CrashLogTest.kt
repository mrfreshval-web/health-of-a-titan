package com.healthofatitan.app.core.ui.crash

import org.junit.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashLogTest {
    @Test
    fun `the report keeps classes and frames, never the messages`() {
        val error = IllegalStateException("Колено ныло после бега", IllegalArgumentException("секрет"))
        val report = CrashLog.format("crash", "main", error, "0.0.558", Instant.EPOCH)
        assertTrue(report.startsWith("HoT 0.0.558 · crash · main"))
        assertTrue("java.lang.IllegalStateException" in report)
        assertTrue("Caused by: java.lang.IllegalArgumentException" in report)
        assertTrue("    at " in report)
        assertFalse("Колено" in report || "секрет" in report, "the person's words never go into the report")
    }
}
