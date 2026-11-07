package com.healthofatitan.app.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.text.G

/**
 * The person's address form for every screen (A61 §4.1, D-055): `MainActivity` provides it from
 * the profile «Я». Neutral by default, so previews, tests and a person who did not choose all get
 * the neutral texts.
 */
val LocalAddressForm = staticCompositionLocalOf { AddressForm.NEUTRAL }

/** This line in the person's address form. */
@Composable
@ReadOnlyComposable
fun G.text(): String = of(LocalAddressForm.current)
