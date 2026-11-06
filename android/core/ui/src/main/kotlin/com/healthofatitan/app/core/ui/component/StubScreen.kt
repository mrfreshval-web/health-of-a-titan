package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.healthofatitan.app.core.ui.theme.HotType

/**
 * Screen of a slice that has no data yet, in «Колоннада» (A36): the lead phrase in the hero card
 * with the gold cornice (Cormorant italic, like the phrase of the day), then the blocks the
 * screen will hold as dashed «no data yet» cards (A36 §2.4). The title lives in [HotTopBar].
 *
 * @param columns 2 lays the blocks out as tiles, like «Карта дня» on the Today mockup.
 */
@Composable
fun StubScreen(
    lead: String,
    blocks: List<String>,
    testTag: String,
    modifier: Modifier = Modifier,
    sectionTitle: String? = null,
    columns: Int = 1,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
            Text(lead, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface)
        }
        if (sectionTitle != null) {
            Spacer(Modifier.height(HotSpacing.cardGap))
            Text(
                sectionTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        blocks.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
            ) {
                row.forEach { block ->
                    HotPendingCard(
                        title = block,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = if (columns > 1) 88.dp else 64.dp),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
