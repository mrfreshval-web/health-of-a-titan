package com.healthofatitan.app.domain.engine

/**
 * A regex whose `\w` and `\b` match any letter, Cyrillic included. The JVM needs the `(?U)` flag
 * for that; Android's regex (ICU) is Unicode-aware by itself and rejects the flag with a
 * PatternSyntaxException, which crashed the app on the first letter of any free text (08.11:
 * «вылетает, как только начинаю печатать цель»). The flag is added only where it compiles.
 */
internal object UnicodeRegex {
    val flag: String = flag { Regex(it) }

    fun of(pattern: String): Regex = Regex(flag + pattern)

    /** `(?U)` when [compile] accepts it, otherwise nothing. */
    fun flag(compile: (String) -> Unit): String = if (runCatching { compile("(?U)\\w") }.isSuccess) "(?U)" else ""
}
