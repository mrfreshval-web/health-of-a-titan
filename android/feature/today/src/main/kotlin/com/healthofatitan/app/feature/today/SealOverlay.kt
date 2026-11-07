package com.healthofatitan.app.feature.today

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.TitanSealMoment
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.engine.DayTexts
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.integration.whoop.WhoopGlance
import com.healthofatitan.app.integration.whoop.day.DayView
import java.time.ZoneId

const val SEAL_OVERLAY_TAG = "today_seal"

/**
 * «Печать WHOOP» (A37 §10 фишка 7, s5): when WHOOP confirms today's training item by itself the
 * ring closes over the Titan with a double tick — once per item, never again for the same one.
 * What was seen is a per-device convenience (SharedPreferences), not health data.
 */
@Composable
fun SealOverlay(plan: DayView, glance: WhoopGlance?) {
    val context = LocalContext.current
    val seen = remember(context) { SealSeen(context) }
    val item = plan.items.firstOrNull { it.kind == ItemKind.TRAINING && it.status == ItemStatus.DONE_WHOOP } ?: return
    val key = "${plan.dayKey}:${item.id}"
    var open by remember(key) { mutableStateOf(!seen.has(key)) }
    if (!open) return
    val close = {
        seen.add(key)
        open = false
    }
    val workout = glance?.workout?.takeIf { it.start.atZone(ZoneId.systemDefault()).toLocalDate() == plan.dayKey }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HotBackground(aura = HotThemeTokens.colors.modeRovno, auraCenterY = 0.38f, modifier = Modifier.fillMaxSize().testTag(SEAL_OVERLAY_TAG)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = HotSpacing.edge, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.fillMaxWidth().widthIn(max = 340.dp).aspectRatio(1f)) {
                    TitanSealMoment(key = key, modifier = Modifier.fillMaxSize(), spoken = TitanTexts.SEAL_SPOKEN)
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "✓ WHOOP",
                    style = MaterialTheme.typography.labelLarge,
                    color = HotThemeTokens.colors.modeRovno,
                    modifier = Modifier
                        .border(1.dp, HotThemeTokens.colors.modeRovno.copy(alpha = 0.5f), CircleShape)
                        .background(HotThemeTokens.colors.modeRovno.copy(alpha = 0.10f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(item.title, style = HotType.headline, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                val facts = listOfNotNull(
                    workout?.start?.atZone(ZoneId.systemDefault())?.let { DayTexts.time(it.hour * 60 + it.minute) },
                    workout?.minutes?.takeIf { it > 0 }?.let { "$it ${TitanTexts.MINUTES}" },
                )
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HotOverline(facts.joinToString(" · "))
                }
                Spacer(Modifier.height(36.dp))
                HotPrimaryButton(TitanTexts.SEAL_DONE, onClick = close, modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp))
            }
        }
    }
}

private class SealSeen(context: Context) {
    private val prefs = context.getSharedPreferences("titan_seal", Context.MODE_PRIVATE)

    fun has(key: String): Boolean = prefs.getBoolean(key, false)

    fun add(key: String) {
        prefs.edit().putBoolean(key, true).apply()
    }
}
