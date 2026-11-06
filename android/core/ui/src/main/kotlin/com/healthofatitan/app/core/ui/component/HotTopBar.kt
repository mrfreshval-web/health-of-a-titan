package com.healthofatitan.app.core.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.healthofatitan.app.core.ui.R

/** TalkBack label for the SOS button (A11 §1.3). */
const val SOS_BUTTON_DESCRIPTION = "SOS, срочная поддержка"
const val SOS_BUTTON_TAG = "sos_button"

/**
 * Top bar of every tab screen (A11 §1.3): title on the left, SOS always on the right in a
 * neutral FilledTonalIconButton. One tap opens the offline SOS screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotTopBar(
    title: String,
    onSosClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text(title) },
        actions = { SosButton(onClick = onSosClick) },
        modifier = modifier,
    )
}

@Composable
fun SosButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier.testTag(SOS_BUTTON_TAG)) {
        Icon(painter = painterResource(R.drawable.core_ui_ic_sos), contentDescription = SOS_BUTTON_DESCRIPTION)
    }
}
