package com.healthofatitan.app.feature.training

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.airbnb.lottie.LottieCompositionFactory
import com.healthofatitan.app.core.ui.component.LottieEntry
import com.healthofatitan.app.core.ui.component.TitanLottie
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Lottie files of Agent 17 (tools/lottie, v2 — the Titan of the mark as a cut-out rig): every
 * top-20 exercise of the brief and every heroic moment is in the manifest, parses with
 * lottie-android, has its markers and the layer names the app recolours, and stays within the
 * size budget. The files live in core:ui (assets/lottie), the player of this module plays them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class TitanLottieTest {

    private val dir: File by lazy {
        listOf("../../core/ui", "core/ui", "android/core/ui").map { File(it, "src/main/assets/lottie") }.first { it.isDirectory }
    }

    private val manifest: Map<String, LottieEntry> by lazy { TitanLottie.parse(File(dir, "manifest.json").readText()) }

    @Test
    fun `every top-20 exercise and heroic moment is in the manifest`() {
        (TOP_20 + HEROES).forEach { id ->
            val e = assertNotNull(manifest[id], "$id is missing in lottie/manifest.json")
            assertEquals("lottie/$id.json", e.file)
            assertTrue(File(dir, "$id.json").isFile, "$id.json is missing")
        }
        assertEquals(20, TOP_20.size)
    }

    @Test
    fun `every file parses with lottie-android and matches the brief`() {
        manifest.values.forEach { e ->
            val file = File(dir.parentFile, e.file)
            val json = file.readText()
            assertTrue(file.length() <= 150 * 1024, "${e.id}: ${file.length() / 1024} KB > 150 KB")
            val result = LottieCompositionFactory.fromJsonStringSync(json, e.id)
            val c = assertNotNull(result.value, "${e.id}: ${result.exception}")
            assertEquals(60f, c.frameRate, e.id)
            assertEquals(1024, c.bounds.width(), e.id)
            // lottie-android keeps endFrame as op − 0.01
            assertEquals(e.frames.toFloat(), c.endFrame - c.startFrame, 0.05f, e.id)
            assertTrue(c.layers.size <= 40, "${e.id}: ${c.layers.size} layers")
            val markers = c.markers.map { it.name }.toSet()
            assertTrue("poster" in markers, "${e.id}: no poster marker")
            assertEquals(e.markers.keys, markers, e.id)
            assertTrue("\"titan_line\"" in json || e.id == "hero-record", "${e.id}: no titan_line group")
            listOf("\"ty\":\"tm\"".takeIf { e.kind != "hero" }).filterNotNull().forEach {
                assertTrue(it !in json, "${e.id}: trim paths are only for heroic moments")
            }
            assertTrue("\"x\":\"" !in json, "${e.id}: expressions are not allowed")
        }
    }

    @Test
    fun `the limbs of both sides share one drawing`() {
        // v2: a limb piece is a precomp asset used by the near and the far side (the size budget)
        listOf("ex-goblet-squat", "ex-pullup", "ex-stretch-quad").forEach { id ->
            val doc = JSONObject(File(dir, "$id.json").readText())
            val layers = doc.getJSONArray("layers")
            val refs = (0 until layers.length()).mapNotNull { layers.getJSONObject(it).optString("refId").ifEmpty { null } }
            val assets = doc.getJSONArray("assets").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") }.toSet() }
            assertTrue(refs.size >= 10, "$id: ${refs.size} piece layers")
            assertTrue(refs.size > refs.toSet().size, "$id: no piece is shared")
            refs.forEach { assertTrue(it in assets, "$id: $it") }
        }
    }

    @Test
    fun `exercise markers carry the key poses of the brief`() {
        val keys = mapOf(
            "ex-goblet-squat" to setOf("stand", "bottom"),
            "ex-cat-cow" to setOf("neutral", "cat", "cow"),
            "ex-pullup" to setOf("hang", "mid", "top"),
            "ex-band-dislocate" to setOf("front", "overhead", "behind"),
            "ex-stretch-pigeon" to setOf("set", "fold", "enter", "hold_loop_start", "hold_loop_end", "exit"),
        )
        keys.forEach { (id, want) -> assertTrue(manifest.getValue(id).markers.keys.containsAll(want), "$id: ${manifest.getValue(id).markers.keys}") }
    }

    @Test
    fun `holds play the entry once and then loop the breathing`() {
        val e = manifest.getValue("ex-stretch-pigeon")
        val start = e.markers.getValue("hold_loop_start")
        val end = e.markers.getValue("hold_loop_end")
        val loopMs = (end - start) * 1000L / LottieEntry.FPS
        val startMs = start * 1000L / LottieEntry.FPS
        assertEquals(e.progress(startMs + 1_000, 0), e.progress(startMs + 1_000 + loopMs, 0), 1e-3f)
        assertTrue(e.progress(startMs / 2, 0) < start.toFloat() / e.frames)
    }

    @Test
    fun `reps follow the cycle of the user's tempo`() {
        val e = manifest.getValue("ex-goblet-squat")
        assertEquals(0.5f, e.progress(2_000, 4_000), 1e-4f)
        assertEquals(e.progress(1_000, 4_000), e.progress(5_000, 4_000), 1e-4f)
        assertTrue(e.poster > 0f)
    }

    @Test
    fun `the light has exactly the three stops of the file`() {
        assertEquals(3, TitanLottie.stops(emptyList()).size)
        assertEquals(3, TitanLottie.stops(listOf(androidx.compose.ui.graphics.Color.White)).size)
        assertEquals(3, TitanLottie.stops(List(5) { androidx.compose.ui.graphics.Color.Red }).size)
    }

    @Test
    fun `the seal has the ring closing and the heroes are named as the brief`() {
        listOf(TitanLottie.HERO_SEAL, TitanLottie.HERO_LEVEL_UP, TitanLottie.HERO_RECORD, TitanLottie.HERO_FIRST_SKILL, TitanLottie.HERO_TEST_PASSED)
            .forEach { assertTrue(it in manifest, it) }
        assertTrue("seal" in manifest.getValue(TitanLottie.HERO_SEAL).markers)
    }

    private companion object {
        val TOP_20 = listOf(
            "ex-breath-long-exhale", "ex-cat-cow", "ex-stretch-chest-doorway", "ex-worlds-greatest", "ex-stretch-pigeon",
            "ex-ankle-knee-wall", "ex-arm-circles", "ex-stretch-hip-flexor", "ex-stretch-lat-wall", "ex-deep-squat-hold",
            "ex-leg-swings", "ex-stretch-child", "ex-stretch-quad", "ex-wrist-prep", "ex-scap-pushup",
            "ex-stretch-calf", "ex-stretch-hamstring", "ex-band-dislocate", "ex-pullup", "ex-goblet-squat",
        )
        val HEROES = listOf("hero-seal", "hero-level-up", "hero-record", "hero-first-skill", "hero-test-passed")
    }
}
