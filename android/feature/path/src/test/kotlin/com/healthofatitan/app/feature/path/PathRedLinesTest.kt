package com.healthofatitan.app.feature.path

import androidx.compose.ui.graphics.Color
import com.healthofatitan.app.core.ui.theme.PathColors
import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.domain.today.StopWords
import com.healthofatitan.app.feature.path.logic.PathGiven
import com.healthofatitan.app.feature.path.logic.PathV3Copy
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The red lines of «Путь» checked in code: the stop dictionary of A39 and P-19 over every text,
 * no red anywhere on the path (A58-VP §1.5; only «Помощь» may be red), and P-21 — no text of a
 * goal leaves the phone: the feature has no network, analytics or log calls at all.
 */
class PathRedLinesTest {

    private val texts = PathCopy.all() + PathGiven.words() + PathV3Copy.all()

    @Test
    fun `no stop word and no word of P-19 in any text of the path`() {
        val hits = texts.flatMap { t -> (StopWords.hits(t) + PathCopy.FORBIDDEN.filter { t.lowercase().contains(it) }).map { "$it ← $t" } }
        assertEquals(emptyList(), hits)
    }

    @Test
    fun `no draft text of pauses, refusals or the crossroads goes without the review mark`() {
        assertTrue(PathCopy.REVIEW.contains("10, 3, 7"))
        val sheets = File("src/main/kotlin/com/healthofatitan/app/feature/path/PathSheets.kt").readText()
        assertTrue(sheets.split("ReviewNote()").size - 1 >= 3, "pause, release and the crossroads carry the review mark")
    }

    private fun hue(c: Color): Pair<Double, Double> {
        val r = c.red.toDouble()
        val g = c.green.toDouble()
        val b = c.blue.toDouble()
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        if (d < 1e-6) return 0.0 to 0.0
        val h = when (mx) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }.let { if (it < 0) it + 360 else it }
        return h to d / mx
    }

    @Test
    fun `no red on the path in either theme`() {
        listOf(PathColors.Dark, PathColors.Light).forEach { theme ->
            theme.all.forEach { c ->
                val (h, s) = hue(c)
                val red = s > 0.2 && (h >= 345.0 || h <= 15.0)
                assertTrue(!red, "red-ish colour $c (hue $h)")
            }
        }
        val lever = hue(PathColors.Dark.lever).first
        assertTrue(lever in 30.0..45.0, "the lever is warm amber, hue ≈ 38°: $lever")
    }

    @Test
    fun `P-21 the path has no network, analytics or logs`() {
        val banned = listOf("core.network", "okhttp", "retrofit", "Analytics", "android.util.Log", "Firebase", "HttpURLConnection", "println(")
        val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        val hits = sources.flatMap { f -> banned.filter { f.readText().contains(it) }.map { "${f.name}: $it" } }
        assertEquals(emptyList(), hits)
        val gradle = File("build.gradle.kts").readText()
        assertTrue(":core:network" !in gradle && ":integration:healthconnect" !in gradle)
    }

    @Test
    fun `the path speaks Geist, Cormorant only for why, haiku and the horizon name`() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.readText() }.joinToString("\n")
        assertTrue("Manrope" !in sources)
        val cormorant = Regex("HotType\\.(voice|haiku|haikuCard|horizonName|horizonNameLarge)|CormorantItalic").findAll(sources).map { it.value }.toSet()
        assertTrue("CormorantItalic" !in cormorant, "Cormorant comes only through HotType's three styles")
    }
}
