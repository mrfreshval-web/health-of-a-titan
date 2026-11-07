package com.healthofatitan.app.feature.checkin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.TitanGeometry
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.checkin.BodyArea
import com.healthofatitan.app.domain.checkin.Pain
import com.healthofatitan.app.domain.checkin.PainRoute
import com.healthofatitan.app.domain.checkin.PainRules
import com.healthofatitan.app.domain.checkin.TitanLook
import kotlin.math.min

const val BODY_MAP_TAG = "checkin_body_map"
const val PAIN_TAG = "checkin_pain"
const val PAIN_CHEST_TAG = "checkin_pain_chest"

/** The emergency number shown with SAF-01 (A07 §4.5: built-in US numbers, as on the SOS screen). */
const val EMERGENCY_NUMBER = "911"

/**
 * «Где?» (И2.15, sc5): the Titan as a silhouette — tap a zone and it lights amber (never red);
 * the same zones as chips for TalkBack. «Это боль» is separate from «забито»: the chest goes to
 * SAF-01 (the emergency line on screen) in one tap, a joint spares that zone (TR-09 / F6).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyMap(
    selected: Set<BodyArea>,
    onToggle: (BodyArea) -> Unit,
    onPain: (Pain) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var painOpen by rememberSaveable { mutableStateOf(false) }
    var pain by rememberSaveable { mutableStateOf<Pain?>(null) }
    val ringFit = 0.86f
    Column(modifier.fillMaxWidth().testTag(BODY_MAP_TAG)) {
        HotOverline(CheckinTexts.WHERE_HINT)
        Spacer(Modifier.height(8.dp))
        LiveTitan(
            look = TitanLook(light = 0.7f, amber = selected),
            ringFit = ringFit,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = if (compact) 200.dp else 260.dp)
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val scale = min(w, h) * ringFit / (2f * TitanGeometry.RING_R)
                        val dx = w / 2f - TitanGeometry.RING_CX * scale
                        val dy = h / 2f - TitanGeometry.RING_CY * scale
                        val hit = AREA_ART.entries.minByOrNull { (_, pts) ->
                            pts.minOf { p -> (Offset(dx + p.x * scale, dy + p.y * scale) - tap).getDistance() }
                        }
                        if (hit != null) onToggle(hit.key)
                    }
                },
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyArea.entries.forEach { a ->
                CheckinChip(CheckinTexts.bodyArea(a), a in selected, onClick = { onToggle(a) })
            }
        }
        Spacer(Modifier.height(4.dp))
        HotTextButton(CheckinTexts.IT_HURTS, onClick = { painOpen = !painOpen }, modifier = Modifier.testTag(PAIN_TAG))
        AnimatedVisibility(painOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(CheckinTexts.IT_HURTS_HINT, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckinChip(CheckinTexts.PAIN_CHEST, pain == Pain.CHEST, onClick = { pain = Pain.CHEST; onPain(Pain.CHEST) }, modifier = Modifier.testTag(PAIN_CHEST_TAG))
                    CheckinChip(CheckinTexts.PAIN_JOINT, pain == Pain.JOINT, onClick = { pain = Pain.JOINT; onPain(Pain.JOINT) })
                }
                pain?.let { PainAnswer(it) }
            }
        }
    }
}

/** What «Это боль» says: SAF-01 with the call button, or the joint spared. No diagnosis. */
@Composable
fun PainAnswer(pain: Pain) {
    val context = LocalContext.current
    when (PainRules.route(pain)) {
        PainRoute.EMERGENCY -> Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(CheckinTexts.painChest(EMERGENCY_NUMBER), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                HotTextButton("${CheckinTexts.CALL} $EMERGENCY_NUMBER", onClick = { context.dial(EMERGENCY_NUMBER) })
            }
        }
        PainRoute.SPARE_ZONE -> Text(CheckinTexts.PAIN_JOINT_LINE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun Context.dial(number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
