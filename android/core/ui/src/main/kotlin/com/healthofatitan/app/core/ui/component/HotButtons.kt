package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.Titanium
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp

/**
 * The one primary action of a screen (A37 §9.1 «Рассвет», §9.5): a dawn capsule
 * #FFE1AE → #FFC77A → #FFB05E with dark text, 56 dp high and the only thing that glows besides the
 * hero. In the light theme the same pill without the glow. [loading] keeps the size and shows a
 * quiet spinner instead of the label; [trailingArrow] adds «→» («Начать день →»).
 */
@Composable
fun HotPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingArrow: Boolean = false,
) {
    val tokens = HotThemeTokens.colors
    val gradient = Brush.verticalGradient(tokens.dawnFill)
    val content = Titanium.OnDawn
    val active = enabled && !loading
    val glow = tokens.dawnFill[1]
    Surface(
        onClick = onClick,
        enabled = active,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = content,
        modifier = modifier
            .heightIn(min = 56.dp)
            .drawBehind {
                if (tokens.glow && active) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(glow.copy(alpha = 0f), glow.copy(alpha = 0.22f))),
                        topLeft = Offset(-6.dp.toPx(), 2.dp.toPx()),
                        size = size.copy(width = size.width + 12.dp.toPx(), height = size.height + 10.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
                    )
                }
            }
            .semantics {
                role = Role.Button
                if (loading) stateDescription = "Подождите"
            },
    ) {
        Box(
            modifier = Modifier
                .background(gradient, CircleShape)
                .drawWithContent {
                    drawContent()
                    drawLine(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.7f),
                            1f to Color.Transparent,
                        ),
                        start = Offset(size.height / 2, 1.dp.toPx()),
                        end = Offset(size.width - size.height / 2, 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .heightIn(min = 56.dp)
                .fillMaxWidth()
                .then(if (active) Modifier else Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = content)
            } else {
                Text(
                    if (trailingArrow) "$text  →" else text,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp),
                    color = content,
                )
            }
        }
    }
}

/** Secondary action: a glass capsule with a hairline, 48 dp (A37: no second filled button). */
@Composable
fun HotSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        border = BorderStroke(1.dp, HotThemeTokens.colors.rule),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Quiet text action in the dawn accent. Never red: red is only for the crisis card and flags. */
@Composable
fun HotTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
