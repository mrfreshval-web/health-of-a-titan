package com.healthofatitan.app.feature.checkin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.TitanGeometry
import com.healthofatitan.app.core.ui.component.TitanLight
import com.healthofatitan.app.core.ui.component.TitanScene
import com.healthofatitan.app.core.ui.component.rememberReduceMotion
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.checkin.BodyArea
import com.healthofatitan.app.domain.checkin.TitanLook
import kotlin.math.min

/** Where each zone of the silhouette sits on the Titan, in the 2048 art units of mark v2. */
internal val AREA_ART: Map<BodyArea, List<Offset>> = mapOf(
    BodyArea.NECK to listOf(Offset(1024f, 650f)),
    BodyArea.SHOULDERS to listOf(Offset(900f, 720f), Offset(1148f, 720f)),
    BodyArea.CHEST to listOf(Offset(1024f, 820f)),
    BodyArea.ARMS to listOf(Offset(760f, 735f), Offset(1288f, 735f)),
    BodyArea.BACK to listOf(Offset(1024f, 900f)),
    BodyArea.LOWER_BACK to listOf(Offset(1024f, 1030f)),
    BodyArea.HIPS to listOf(Offset(970f, 1240f), Offset(1078f, 1240f)),
    BodyArea.KNEES to listOf(Offset(960f, 1450f), Offset(1088f, 1450f)),
    BodyArea.CALVES to listOf(Offset(950f, 1620f), Offset(1098f, 1620f)),
)

/**
 * The live Titan of the deck (И2.14′, A44 §3, w1): light fills it with Заряд, the weather hangs
 * over the head, amber lies on the sore zones, the figure softens with «туман» and sinks or rises
 * with «Тяга». Every change animates in ≤ 150 ms (reduced motion: at once). No red anywhere.
 */
@Composable
fun LiveTitan(look: TitanLook, modifier: Modifier = Modifier, spoken: String? = null, ringFit: Float = 0.86f) {
    val reduce = rememberReduceMotion()
    val spec = tween<Float>(if (reduce) 0 else 150)
    val light by animateFloatAsState(look.light, spec, label = "light")
    val blur by animateFloatAsState(look.blur, spec, label = "blur")
    val lift by animateFloatAsState(look.lift, spec, label = "lift")
    val tokens = HotThemeTokens.colors
    val dawn = Titanium.Dawn
    val titanLight = TitanLight(
        line = if (tokens.isDark) listOf(Color.White, lerp(Color.White, dawn, 0.25f + 0.5f * light), lerp(Titanium.Steel, dawn, light)) else tokens.titanLight,
        column = tokens.columnLight,
        knockout = tokens.knockout,
        glow = if (tokens.glow) dawn else null,
        glowStrength = 0.15f + 0.85f * light,
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { if (spoken != null) contentDescription = spoken },
    ) {
        val side = min(maxWidth.value, maxHeight.value)
        TitanScene(
            modifier = Modifier.fillMaxSize(),
            light = titanLight,
            ring = { light },
            ringColor = dawn,
            ringTrack = tokens.hairline,
            ringStroke = 9f,
            ringFit = ringFit,
            titanBlur = { blur * 26f },
            titanLift = { -lift * 70f },
            titanAlpha = { 0.55f + 0.45f * light },
        )
        // amber on the sore zones (a soft light, never a red mark)
        if (look.amber.isNotEmpty() || look.sick) {
            Canvas(Modifier.fillMaxSize()) {
                val scale = min(size.width, size.height) * ringFit / (2f * TitanGeometry.RING_R)
                val dx = size.width / 2f - TitanGeometry.RING_CX * scale
                val dy = size.height / 2f - TitanGeometry.RING_CY * scale - lift * 70f * scale
                fun at(o: Offset) = Offset(dx + o.x * scale, dy + o.y * scale)
                val r = 95f * scale
                look.amber.forEach { area ->
                    AREA_ART[area].orEmpty().forEach { o ->
                        drawCircle(Brush.radialGradient(listOf(dawn.copy(alpha = 0.55f), Color.Transparent), at(o), r), r, at(o))
                    }
                }
                if (look.sick) {
                    val head = at(Offset(1024f, 560f))
                    drawCircle(Brush.radialGradient(listOf(Titanium.LightIris.last().copy(alpha = 0.35f), Color.Transparent), head, r * 2.2f), r * 2.2f, head)
                }
            }
        }
        // the weather over the head
        look.sky?.let { sky ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.offset(y = (side * (0.5f - ringFit / 2f * 0.98f) + side * 0.02f - lift * side * 0.03f).dp)) {
                    SkyGlyph(sky, MaterialThemeInk(), size = (side * 0.16f).dp)
                }
            }
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

@Composable
private fun MaterialThemeInk(): Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface

/** «Голова» (w1): the Titan alone, soft in «туман», sharp in «ясно». */
@Composable
fun HeadTitan(blur: Float, modifier: Modifier = Modifier) {
    TitanScene(
        modifier = modifier.size(96.dp),
        columnAlpha = { 0f },
        titanBlur = { blur * 40f },
        titanAlpha = { 1f - 0.35f * blur },
        ringFit = 1.35f,
    )
}
