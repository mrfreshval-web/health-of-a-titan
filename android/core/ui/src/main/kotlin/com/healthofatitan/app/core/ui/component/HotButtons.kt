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
import com.healthofatitan.app.core.ui.theme.Kolonnada

/**
 * The one primary action of a screen (A36 §6, §11 rule 1): a gold capsule with the vertical
 * gradient #E6C77E → #CFA845 and a 1 dp light rim on top; 56 dp high. In the light theme it is
 * flat «тёмное золото». [loading] keeps the size and shows a quiet spinner instead of the label.
 */
@Composable
fun HotPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val dark = HotThemeTokens.colors.isDark
    val gradient = if (dark) {
        Brush.verticalGradient(listOf(Kolonnada.GoldButtonTop, Kolonnada.GoldButtonBottom))
    } else {
        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary))
    }
    val content = MaterialTheme.colorScheme.onPrimary
    val active = enabled && !loading
    Surface(
        onClick = onClick,
        enabled = active,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = content,
        modifier = modifier
            .heightIn(min = 56.dp)
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
                    if (dark) {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.5f to Kolonnada.GoldHi.copy(alpha = 0.8f),
                                1f to Color.Transparent,
                            ),
                            start = Offset(size.height / 2, 1.dp.toPx()),
                            end = Offset(size.width - size.height / 2, 1.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                .heightIn(min = 56.dp)
                .fillMaxWidth()
                .then(if (active) Modifier else Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = content)
            } else {
                Text(text, style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}

/** Secondary action: an ivory-hairline capsule, 48 dp (A36 §6: no second filled button). */
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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

/** Quiet text action in gold (A36 mockup 09 «Поправить»). Never red: red is only for SOS. */
@Composable
fun HotTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
