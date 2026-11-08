package com.healthofatitan.app.domain.engine

import org.junit.Test
import java.util.regex.PatternSyntaxException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnicodeRegexTest {
    @Test
    fun `the flag is kept where it compiles`() {
        assertEquals("(?U)", UnicodeRegex.flag { Regex(it) })
        assertTrue(UnicodeRegex.of("\\bпривет\\w*").containsMatchIn("скажи приветик"))
    }

    @Test
    fun `an engine that rejects the flag (Android ICU) gets the pattern without it`() {
        assertEquals("", UnicodeRegex.flag { throw PatternSyntaxException("U", it, 2) })
    }
}
