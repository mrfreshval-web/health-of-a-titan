package com.healthofatitan.app.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.theme.HotThemeTokens

/**
 * Source chip «иконка + слово» (A36 §5, mockup 09: WHOOP, Будильник, Календарь, Ты): a hairline
 * capsule, gold content. Words only, never a third-party logo.
 */
@Composable
fun HotSourceChip(text: String, @DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, HotThemeTokens.colors.rule),
        modifier = modifier,
    ) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Settings row of mockup 09: title, optional chip and caption under it, value and chevron on the
 * right, hairline below. The whole row is one 64 dp+ touch target.
 */
@Composable
fun HotListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    value: String? = null,
    chip: (@Composable () -> Unit)? = null,
    divider: Boolean = true,
) {
    Column(modifier) {
        Surface(onClick = onClick, color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = MaterialTheme.typography.titleMedium.fontWeight))
                    if (chip != null || caption != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            chip?.invoke()
                            if (chip != null && caption != null) Spacer(Modifier.width(8.dp))
                            if (caption != null) {
                                Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (value != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Icon(
                    painterResource(R.drawable.core_ui_ic_chevron),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp),
                )
            }
        }
        if (divider) HorizontalDivider(thickness = 1.dp, color = HotThemeTokens.colors.hairline)
    }
}
