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
        // v2: a limb piece is a precomp asset used by the near and the far side (the size budget);
        // v3: in the front and 3/4 views one side is the other's drawing mirrored in a wrapper precomp
        listOf("ex-pullup", "ex-band-dislocate", "ex-arm-circles", "ex-stretch-chest-doorway").forEach { id ->
            val doc = JSONObject(File(dir, "$id.json").readText())
            val layers = doc.getJSONArray("layers")
            val refs = (0 until layers.length()).mapNotNull { layers.getJSONObject(it).optString("refId").ifEmpty { null } }
            val assetList = doc.getJSONArray("assets").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            val assets = assetList.associateBy { it.getString("id") }
            // a wrapper's single layer refers to the drawing it mirrors
            fun drawing(ref: String): String {
                val inner = assets.getValue(ref).getJSONArray("layers").getJSONObject(0)
                return inner.optString("refId").ifEmpty { null }?.let { drawing(it) } ?: ref
            }
            assertTrue(refs.size >= 10, "$id: ${refs.size} piece layers")
            refs.forEach { assertTrue(it in assets, "$id: $it") }
            val drawings = refs.map { drawing(it) }
            assertTrue(drawings.size > drawings.toSet().size, "$id: no piece is shared")
        }
    }

    @Test
    fun `far limbs of the side view dim their line and keep the glass opaque`() {
        // v4 (Agent 12): a veiled far leg stacked two veils into a dark disc at the knee
        listOf("ex-goblet-squat", "ex-stretch-hip-flexor", "ex-stretch-calf").forEach { id ->
            val doc = JSONObject(File(dir, "$id.json").readText())
            val assets = doc.getJSONArray("assets").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            val far = assets.filter { it.getString("id").endsWith("_far") }
            assertTrue(far.size >= 5, "$id: ${far.size} far pieces")
            val layers = doc.getJSONArray("layers")
            (0 until layers.length()).map { layers.getJSONObject(it) }.filter { it.optString("refId").endsWith("_far") }.forEach {
                assertEquals(100.0, it.getJSONObject("ks").getJSONObject("o").getDouble("k"), 1e-6, "$id: ${it.getString("nm")} is veiled")
            }
            far.forEach { a ->
                val items = mutableListOf<JSONObject>()
                fun walk(o: Any?) {
                    when (o) {
                        is JSONObject -> { if (o.has("ty")) items += o; o.keys().forEach { walk(o.get(it)) } }
                        is org.json.JSONArray -> (0 until o.length()).forEach { walk(o.get(it)) }
                    }
                }
                walk(a.getJSONArray("layers"))
                val line = items.filter { it.optString("ty") == "gf" }.map { it.getJSONObject("o").getDouble("k") }
                val glass = items.filter { it.optString("ty") == "fl" }.map { it.getJSONObject("o").getDouble("k") }
                assertTrue(line.isNotEmpty() && line.all { it < 100.0 }, "$id ${a.getString("id")}: line $line")
                assertTrue(glass.isNotEmpty() && glass.all { it == 100.0 }, "$id ${a.getString("id")}: glass $glass")
            }
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
    fun `the Titan of the day on the Path has its five files and the markers of the package`() {
        // docs/design/path-v1/visual-production.md §3.2: 7 poses in 5 files, no floor, poster first
        val want = mapOf(
            "path-hold" to setOf(
                "poster", "hold_loop_start", "hold_loop_end", "look_sky", "look_earth", "look_water",
                "to_one_hand", "one_hand_loop_start", "one_hand_loop_end", "to_two_hands",
            ),
            "path-palms" to setOf("poster", "offer", "release"),
            "path-sky" to setOf("poster"),
            "path-earth" to setOf("poster"),
            "path-water" to setOf("poster"),
        )
        want.forEach { (id, markers) ->
            val e = assertNotNull(manifest[id], "$id is missing in lottie/manifest.json")
            assertEquals("path", e.kind, id)
            assertEquals(markers, e.markers.keys, id)
            val json = File(dir, "$id.json").readText()
            assertTrue("\"nm\":\"floor\"" !in json, "$id: the Path has no floor")
            assertTrue(File(dir, "$id.json").length() <= 150 * 1024, id)
        }
        // §4.0 timings at 60 fps: look 400 ms, one hand 800 ms, offer 600 ms, the loops 6 / 4 / 1 / 2.4 s
        val hold = manifest.getValue("path-hold").markers
        assertEquals(360, hold.getValue("hold_loop_end") - hold.getValue("hold_loop_start"))
        assertEquals(48, hold.getValue("one_hand_loop_start") - hold.getValue("to_one_hand"))
        assertEquals(360, hold.getValue("one_hand_loop_end") - hold.getValue("one_hand_loop_start"))
        assertEquals(0, hold.getValue("poster"))
        assertEquals(36, manifest.getValue("path-palms").markers.getValue("release") - manifest.getValue("path-palms").markers.getValue("offer"))
        assertEquals(240, manifest.getValue("path-sky").frames)
        assertEquals(60, manifest.getValue("path-earth").frames)
        assertEquals(144, manifest.getValue("path-water").frames)
        // the breath loops from the start in the player of the training too
        val e = manifest.getValue("path-hold")
        assertEquals(e.progress(1_000, 0), e.progress(7_000, 0), 1e-3f)
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
