package com.healthofatitan.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.core.ui.theme.HotType

/**
 * The privacy rationale Health Connect opens from its permission screen (A18 И3b.5 ①, required for
 * `READ_STEPS`). What is read, why, and that it stays on the phone. Texts: Agent 7 review (R-11).
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HotTheme { HealthRationale(onClose = ::finish) } }
    }
}

const val HEALTH_RATIONALE_TAG = "health_rationale"

object HealthRationaleTexts {
    const val OVERLINE = "Health Connect"
    const val TITLE = "Шаги телефона"
    val LINES = listOf(
        "HoT читает только шаги, которые посчитал сам телефон. Больше ничего.",
        "Зачем: сверить прогулку с планом дня и показать кольцо шагов в шапке.",
        "Данные WHOOP из Health Connect не читаем — они приходят напрямую, без дублей.",
        "Шаги остаются на телефоне и не сохраняются в базе HoT.",
        "Отключить можно в любой момент: Настройки › Health Connect › HoT.",
    )
    const val CLOSE = "Понятно"
}

@Composable
fun HealthRationale(onClose: () -> Unit) {
    HotBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag(HEALTH_RATIONALE_TAG),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HotOverline(HealthRationaleTexts.OVERLINE)
            Text(HealthRationaleTexts.TITLE, style = HotType.headline, modifier = Modifier.semantics { heading() })
            HealthRationaleTexts.LINES.forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HotPrimaryButton(HealthRationaleTexts.CLOSE, onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}
