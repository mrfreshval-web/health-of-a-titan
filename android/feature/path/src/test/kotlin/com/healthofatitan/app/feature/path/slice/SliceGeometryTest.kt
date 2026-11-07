package com.healthofatitan.app.feature.path.slice

import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Element
import com.healthofatitan.app.domain.training.Rig
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A58-VP §1.2, §2.2, §2.3: the numbers the drawing, TalkBack and touch share. */
class SliceGeometryTest {

    private val g = SliceGeometry

    @Test
    fun handsTouchInnerArc() {
        val j = Rig.joints(g.HOLD, front = true)
        val near = g.dayPoint(j.handN)
        val far = g.dayPoint(j.handF)
        assertEquals(217.1, near.x, 0.2)
        assertEquals(141.5, near.y, 0.2)
        assertEquals(170.9, far.x, 0.2)
        listOf(near, far).forEach { assertEquals(123.0, g.distance(it, g.ARC_CENTER), 1.5) }
    }

    @Test
    fun `the lottie view of paths py is 156 square at 116, 129_4`() {
        assertEquals(156.0, g.LOTTIE_SIZE, 1e-9)
        assertEquals(116.0, g.LOTTIE_LEFT, 1e-9)
        assertEquals(129.4, g.LOTTIE_TOP, 1e-9)
        assertEquals(g.CX, g.LOTTIE_LEFT + g.LOTTIE_SIZE / 2, 1e-9)
        assertEquals(1.0 to 0.96, g.nodHead(10.0).let { (l, s) -> Math.round(l * 100) / 100.0 to Math.round(s * 100) / 100.0 })
    }

    @Test
    fun `the day titan stands on the horizon`() {
        val j = Rig.joints(g.HOLD, front = true)
        val feet = maxOf(g.dayPoint(j.ankleN).y, g.dayPoint(j.ankleF).y)
        assertEquals(g.HORIZON_Y, g.DAY_TOP + Rig.FLOOR_Y * g.DAY_SCALE, 0.01)
        assertTrue(feet <= g.HORIZON_Y + 0.5)
    }

    @Test
    fun `the titan of the horizon walks by the calendar only`() {
        assertEquals(292.0, g.horizonX(0), 1e-9)
        assertEquals(270.0, g.horizonX(4), 1e-9)
        assertEquals(231.5, g.horizonX(11), 1e-9)
        assertEquals(46.0, g.horizonHeight(0), 1e-9)
        assertEquals(68.0, g.horizonHeight(11), 1e-9)
        assertEquals(g.CX, g.horizonX(12), 1e-9)
        (0..11).forEach { w -> assertTrue(g.horizonX(w + 1) < g.horizonX(w)) }
        // feet on the horizon
        val j = Rig.joints(g.HZ_STAND, front = true)
        assertEquals(g.HORIZON_Y, g.horizonPoint(com.healthofatitan.app.domain.training.Pt(50.0, Rig.FLOOR_Y), 5).y, 1e-9)
        assertTrue(g.horizonPoint(j.ankleN, 7).y <= g.HORIZON_Y + 0.5)
    }

    @Test
    fun `gaps of week 0 and week 11 hold`() {
        val j = Rig.joints(g.HZ_STAND, front = true)
        val week0 = j.all.map { g.horizonPoint(it, 0) }
        val inner = g.ARC_RADII.first() - g.ARC_STROKE / 2
        assertTrue(week0.all { inner - g.distance(it, g.ARC_CENTER) >= 5.0 })
        val week11 = j.all.minOf { g.horizonPoint(it, 11).x }
        assertTrue(week11 - 207.5 >= 13.0, "gap at week 11: ${week11 - 207.5}")
    }

    @Test
    fun `fires stand at least 6 rig units apart, never at the waist`() {
        val j = Rig.joints(g.HZ_STAND, front = true)
        val fires = ComponentKey.entries.filterNot(g::haloOnly).map { it to g.anchor(it, j) }
        for (a in fires.indices) for (b in a + 1 until fires.size) {
            val d = g.distance(fires[a].second, fires[b].second)
            assertTrue(d >= 6.0, "${fires[a].first} – ${fires[b].first}: $d")
        }
        assertEquals(j.kneeF, g.anchor(ComponentKey.BODY, j))
    }

    @Test
    fun `tiers have touch zones of at least 48 dp on both widths`() {
        Element.entries.forEach { e ->
            val h = g.tierBounds(e).height
            assertTrue(h * (360.0 - 24) / g.W >= 48.0, "$e: $h")
        }
        assertEquals(Element.SKY, g.tierAt(100.0))
        assertEquals(Element.EARTH, g.tierAt(300.0))
        assertEquals(Element.WATER, g.tierAt(380.0))
    }

    @Test
    fun `every part has its own node and five segments`() {
        assertEquals(14, (g.ARCS + g.SLABS + g.CURRENTS).toSet().size)
        assertEquals(ComponentKey.of(Element.EARTH), g.SLABS)
        assertEquals(ComponentKey.of(Element.WATER), g.CURRENTS)
        ComponentKey.entries.forEach { k ->
            val s = g.segments(k)
            assertEquals(5, s.size)
            assertEquals(1.0, s.last().second, 1e-9)
            assertTrue(g.bounds(k).width > 0)
        }
        assertEquals(386.4, g.length(ComponentKey.ATTENTION), 0.1)
    }

    @Test
    fun `the pillar puts sky above earth above water`() {
        val stops = g.pillarStops(listOf(ComponentKey.SLEEP, ComponentKey.CARDIO, ComponentKey.ATTENTION))
        assertTrue(stops.getValue(ComponentKey.ATTENTION) > stops.getValue(ComponentKey.CARDIO))
        assertTrue(stops.getValue(ComponentKey.CARDIO) > stops.getValue(ComponentKey.SLEEP))
    }
}
