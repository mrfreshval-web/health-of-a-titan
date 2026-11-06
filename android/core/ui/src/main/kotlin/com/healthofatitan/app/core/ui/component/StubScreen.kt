package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Placeholder screen for slice 0: a headline and the blocks the screen will hold (A11).
 * Cards follow A11 §3.2 spacing: 16 dp edges, 12 dp between cards.
 */
@Composable
fun StubScreen(
    headline: String,
    lead: String,
    blocks: List<String>,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(headline, style = MaterialTheme.typography.headlineMedium)
        Text(lead, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        blocks.forEach { block ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(block, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
