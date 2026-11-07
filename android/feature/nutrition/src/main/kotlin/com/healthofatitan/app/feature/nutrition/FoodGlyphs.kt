package com.healthofatitan.app.feature.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pictograms of food (A41 §12, mockups `docs/design/nutrition/src/build.js`): 16 allergens, four
 * hand portions and a few marks, monoline on a 24 grid in the «Титаниум» grammar (A37 §6).
 * Path data are copied from the mockup source so the app and the mockups draw the same marks.
 */
class FoodGlyph internal constructor(internal val parts: List<Part>) {
    internal class Part(val d: String, val fill: Boolean = false, val dx: Float = 0f, val alpha: Float = 1f, val width: Float? = null)
}

private fun s(d: String, dx: Float = 0f, alpha: Float = 1f, width: Float? = null) = FoodGlyph.Part(d, dx = dx, alpha = alpha, width = width)
private fun f(d: String) = FoodGlyph.Part(d, fill = true)
private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

object FoodGlyphs {
    private const val EAR =
        "M12 21V6.5M12 6.5c-1.6-1-1.6-3 0-4 1.6 1 1.6 3 0 4ZM12 10.5c-1.8-.2-3-1.6-3-3.3 1.8.1 3 1.5 3 3.3Zm0 0c1.8-.2 3-1.6 3-3.3-1.8.1-3 1.5-3 3.3ZM12 14.5c-1.8-.2-3-1.6-3-3.3 1.8.1 3 1.5 3 3.3Zm0 0c1.8-.2 3-1.6 3-3.3-1.8.1-3 1.5-3 3.3Z"

    /** By allergen code of `content/profile/constraints.yaml`. */
    val allergens: Map<String, FoodGlyph> = mapOf(
        "gluten" to FoodGlyph(listOf(s(EAR))),
        "wheat" to FoodGlyph(listOf(s(EAR, dx = -3.5f), s("M15.5 21V9.5M15.5 9.5c1.6-.9 3-.2 3.4 1.2M15.5 13.5c1.6-.9 3-.2 3.4 1.2", alpha = 0.8f))),
        "crustacean" to FoodGlyph(
            listOf(
                s("M18.5 7.5c-3.5-3-9.5-2-11.5 2.5-1.6 3.7.5 7.8 4.5 8.5"),
                s("M11.5 18.5c-1.2-2.3-.2-4.6 2-5.4M8 13.5l-3 1.2M8.5 10.5 5.5 9M18.5 7.5 21 6M18.5 7.5l1 3"),
                f(circle(16f, 8.2f, 0.8f)),
            ),
        ),
        "mollusc" to FoodGlyph(listOf(s("M4 15c0-5 3.6-9 8-9s8 4 8 9H4Z"), s("M12 6v9M8.2 7.4 10 15M15.8 7.4 14 15M5.4 10.4 7 15M18.6 10.4 17 15M9 15l-1 3h8l-1-3"))),
        "egg" to FoodGlyph(listOf(s("M12 3.5c3.4 0 6 5.2 6 9.3 0 3.6-2.7 6.2-6 6.2s-6-2.6-6-6.2c0-4.1 2.6-9.3 6-9.3Z"))),
        "fish" to FoodGlyph(listOf(s("M3 12c3-4.6 9.4-6 14-2l3.5-3v10L17 14c-4.6 4-11 2.6-14-2Z"), f(circle(7.5f, 11.2f, 0.8f)))),
        "peanut" to FoodGlyph(
            listOf(
                s("M12 3c2.6 0 4 2 3.6 4.2-.3 1.5-1.4 2.2-1.4 3.8s1.4 2.3 1.4 4.6C15.6 18.8 14 21 12 21s-3.6-2.2-3.6-5.4c0-2.3 1.4-3 1.4-4.6S8.7 8.7 8.4 7.2C8 5 9.4 3 12 3Z"),
                s("M10.8 6.4h.01M13.2 7.4h.01M11 15.6h.01M13 17.4h.01", width = 2f),
            ),
        ),
        "tree_nut" to FoodGlyph(listOf(s("M5.5 10c0-3 2.9-5 6.5-5s6.5 2 6.5 5H5.5Z"), s("M12 5V3M6.5 10c.5 6 3 10 5.5 11 2.5-1 5-5 5.5-11"))),
        "soy" to FoodGlyph(
            listOf(
                s("M5 17.5C5 11 10.5 5 18.5 5c.5 7.5-5 13-11 13.5H5v-1Z"),
                s(circle(9.5f, 14f, 1.6f)),
                s(circle(12.6f, 11f, 1.6f)),
                s(circle(15.6f, 8f, 1.6f)),
            ),
        ),
        "milk" to FoodGlyph(listOf(s("M9 3h6v3l2 3.2V21H7V9.2L9 6V3Z"), s("M7 12.5h10M9 6h6"))),
        "sesame" to FoodGlyph(listOf(s("M8 4.5c1.6 1.2 1.9 3.8 0 5.2-1.9-1.4-1.6-4 0-5.2ZM16 6.5c1.6 1.2 1.9 3.8 0 5.2-1.9-1.4-1.6-4 0-5.2ZM11 12.5c1.6 1.2 1.9 3.8 0 5.2-1.9-1.4-1.6-4 0-5.2Z"))),
        "celery" to FoodGlyph(
            listOf(
                s("M9 21 10.5 9M12.5 21V9M16 21l-1.5-12"),
                s("M10.5 9c-3-.4-4.6-2.6-4-5.2 2.4.2 3.9 2.2 4 5.2ZM12.5 9c-.6-2.6.2-4.8 2.4-6 1.2 2.3.4 4.8-2.4 6ZM14.5 9c1-2.2 3-3 5-2.4-.5 2.2-2.4 3-5 2.4Z"),
            ),
        ),
        "mustard" to FoodGlyph(listOf(s("M9 8h6v2.5c1.7.7 2.5 2 2.5 4V21h-11v-6.5c0-2 .8-3.3 2.5-4V8Z"), s("M10.5 8V5.5L12 3l1.5 2.5V8M6.5 15h11"))),
        "sulphite" to FoodGlyph(listOf(s("M7.5 3h9c.2 4.5-1.3 8-4.5 8S7.3 7.5 7.5 3Z"), s("M12 11v8M8.5 21h7M7.7 6.5h8.6"))),
        "lupin" to FoodGlyph(
            listOf(
                s("M12 21V9"),
                s(circle(12f, 4.6f, 1.6f)),
                s(circle(9.6f, 8f, 1.6f)),
                s(circle(14.4f, 8f, 1.6f)),
                s(circle(9.6f, 11.6f, 1.6f)),
                s(circle(14.4f, 11.6f, 1.6f)),
                s("M12 16.5c-2-1.8-4.6-1.6-6 0M12 16.5c2-1.8 4.6-1.6 6 0"),
            ),
        ),
        "coconut" to FoodGlyph(listOf(s(circle(12f, 12f, 8f)), f(circle(9.8f, 9.6f, 1f)), f(circle(14.2f, 9.6f, 1f)), f(circle(12f, 13f, 1f)))),
    )

    /** Hand portions (Precision Nutrition, A41 §5.1) by plate role. */
    val palm = FoodGlyph(
        listOf(s("M7 21v-8.5L5.5 8.5c-.4-1.2 1.2-1.8 1.8-.8L9 11V4.5c0-1.2 1.8-1.2 1.8 0V10V3.6c0-1.2 1.8-1.2 1.8 0V10V4.4c0-1.2 1.8-1.2 1.8 0V11V6.6c0-1.2 1.8-1.2 1.8 0v8.2c0 3.4-1.8 6.2-5 6.2H7")),
    )
    val cup = FoodGlyph(
        listOf(
            s("M3.5 10.5c1 5.6 4.4 8.5 8.5 8.5s7.5-2.9 8.5-8.5"),
            s("M3.5 10.5c2.6 1.3 5.4 1.9 8.5 1.9s5.9-.6 8.5-1.9"),
            s(circle(9f, 9f, 1.1f)),
            s(circle(12f, 8.2f, 1.1f)),
            s(circle(15f, 9f, 1.1f)),
            s(circle(10.6f, 6.6f, 1.1f)),
            s(circle(13.4f, 6.6f, 1.1f)),
        ),
    )
    val fist = FoodGlyph(
        listOf(
            s("M6 9.5c0-1.4 1.1-2.5 2.5-2.5h7c1.4 0 2.5 1.1 2.5 2.5v6c0 3-2.4 5.5-5.5 5.5h-1C8.4 21 6 18.6 6 15.5v-6Z"),
            s("M9.5 7v3.5M12.5 7v3.5M15.5 7v3.5M6 12.5h4.5c1 0 1.5.8 1.5 1.6"),
        ),
    )
    val thumb = FoodGlyph(
        listOf(
            s("M10 21V11.5l2.5-6c.5-1.3 2.5-1 2.4.5L14.5 10h4c1.1 0 1.9 1 1.6 2.1l-1.8 6.6c-.3 1.3-1.4 2.3-2.8 2.3H10Z"),
            s("M4.5 11.5H10V21H4.5z"),
        ),
    )

    fun hand(role: String): FoodGlyph = when (role) {
        "protein" -> palm
        "starch", "fruit" -> cup
        "veg" -> fist
        else -> thumb
    }

    val check = FoodGlyph(listOf(s("m5 12.5 4.3 4.3L19 7.2")))
    val seal = FoodGlyph(listOf(s(circle(12f, 12f, 8.6f)), s("m8.2 12.2 2.6 2.6 5-5.2")))
    val swap = FoodGlyph(listOf(s("M5 9h13l-3-3M19 15H6l3 3")))
    val plate = FoodGlyph(listOf(s(circle(12f, 12f, 8.5f)), s(circle(12f, 12f, 4.5f))))
    val half = FoodGlyph(listOf(s(circle(12f, 12f, 8.5f)), f("M12 3.5a8.5 8.5 0 0 0 0 17Z")))
    val sun = FoodGlyph(listOf(s(circle(12f, 12f, 4f)), s("M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4")))
    val moon = FoodGlyph(listOf(s("M19.5 14.6A7.6 7.6 0 0 1 9.4 4.5a7.6 7.6 0 1 0 10.1 10.1Z")))
    val lock = FoodGlyph(listOf(s("M5.5 10.5h13v9.5h-13zM8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5")))
    val shield = FoodGlyph(listOf(s("M12 3.5 19 6v5.5c0 4.4-3 7.7-7 9-4-1.3-7-4.6-7-9V6l7-2.5Z"), s("m8.6 12 2.4 2.4 4.4-4.6")))
    val trash = FoodGlyph(listOf(s("M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13")))
    val clock = FoodGlyph(listOf(s(circle(12f, 12f, 8.5f)), s("M12 7.5V12l3 2")))
    val heart = FoodGlyph(listOf(s("M12 19.2S4 14.6 4 9.2A4 4 0 0 1 12 7.6a4 4 0 0 1 8 1.6c0 5.4-8 10-8 10Z"), s("M7.2 12h2.6l1.2-2 2 4 1.2-2h2.6")))
    val road = FoodGlyph(listOf(s("M8 21 10 3M16 21 14 3M12 6v2M12 11v2M12 16v2")))
    val bolt = FoodGlyph(listOf(s("M13 3 5.5 13.2h5.6L10 21l7.5-10.2h-5.6L13 3Z")))
}

/**
 * Draws a [FoodGlyph] in [color]. [crossed] adds the «нет в блюде» stroke of the allergen shield.
 * The glyph is decorative unless [description] is given (A36: never a pictogram without a word).
 */
@Composable
fun FoodIcon(
    glyph: FoodGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Float = 1.5f,
    crossed: Color? = null,
    description: String? = null,
) {
    val paths = remember(glyph) { glyph.parts.map { it to PathParser().parsePathString(it.d).toPath() } }
    Canvas(
        modifier
            .size(size)
            .clearAndSetSemantics { if (description != null) contentDescription = description },
    ) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            paths.forEach { (part, path) ->
                translate(left = part.dx) {
                    if (part.fill) {
                        drawPath(path, color.copy(alpha = color.alpha * part.alpha), style = Fill)
                    } else {
                        drawPath(
                            path,
                            color.copy(alpha = color.alpha * part.alpha),
                            style = Stroke(width = part.width ?: strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
            if (crossed != null) {
                drawLine(crossed, Offset(3f, 21f), Offset(21f, 3f), strokeWidth = strokeWidth + 0.4f, cap = StrokeCap.Round)
            }
        }
    }
}
