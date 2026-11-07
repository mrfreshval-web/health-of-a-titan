package com.healthofatitan.app.feature.me.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.database.ProfileSettingsStore
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.profile.ProfileSettings
import com.healthofatitan.app.core.model.profile.SexForCalc
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

const val PROFILE_ME_SCREEN_TAG = "screen_profile_me"
const val PROFILE_BIRTH_YEAR_TAG = "profile_birth_year"

fun addressTag(form: AddressForm): String = "profile_address_${form.code}"

fun sexTag(sex: SexForCalc): String = "profile_sex_${sex.code}"

/**
 * «Профиль» (A61 §5.1, D-055): the card «Ты» of the settings. In this slice the three facts typed
 * by hand: the address form, the year of birth and the sex for calculations. No account, no
 * e-mail, no login (D-055 п. 1); everything stays on the phone.
 *
 * @param stateful false renders the screen from default state without a ViewModel (navigation
 *   tests run without Hilt).
 */
fun NavGraphBuilder.profileScreen(stateful: Boolean = true) {
    composable(route = HotRoute.PROFILE) {
        if (stateful) {
            ProfileRoute()
        } else {
            var local by remember { mutableStateOf(ProfileSettings()) }
            ProfileScreen(
                settings = local,
                onAddress = { local = local.copy(addressForm = it) },
                onBirthYear = { local = local.copy(birthYear = it) },
                onSex = { local = local.copy(sexForCalc = it) },
            )
        }
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(private val store: ProfileSettingsStore) : ViewModel() {
    val settings: StateFlow<ProfileSettings?> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAddress(form: AddressForm) {
        viewModelScope.launch { store.setAddressForm(form) }
    }

    fun setBirthYear(year: Int?) {
        viewModelScope.launch { store.setBirthYear(year) }
    }

    fun setSex(sex: SexForCalc) {
        viewModelScope.launch { store.setSexForCalc(sex) }
    }
}

@Composable
private fun ProfileRoute(viewModel: ProfileViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Nothing is drawn until the stored profile is read: the year field starts from it.
    val loaded = settings ?: return
    ProfileScreen(loaded, viewModel::setAddress, viewModel::setBirthYear, viewModel::setSex)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    settings: ProfileSettings,
    onAddress: (AddressForm) -> Unit,
    onBirthYear: (Int?) -> Unit,
    onSex: (SexForCalc) -> Unit,
    modifier: Modifier = Modifier,
    currentYear: Int = LocalDate.now().year,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(PROFILE_ME_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        Section(ProfileTexts.SECTION_YOU)
        HotCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(ProfileTexts.ADDRESS_TITLE, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADDRESS_ORDER.forEach { form ->
                        Choice(ProfileTexts.address(form), settings.addressForm == form, { onAddress(form) }, addressTag(form))
                    }
                }
                Text(ProfileTexts.ADDRESS_NOTE, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            }
        }

        Section(ProfileTexts.SECTION_CALC)
        HotCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                var year by rememberSaveable { mutableStateOf(settings.birthYear?.toString().orEmpty()) }
                val parsed = year.toIntOrNull()
                val wrong = year.length == 4 && !ProfileSettings.validBirthYear(parsed, currentYear)
                OutlinedTextField(
                    value = year,
                    onValueChange = { typed ->
                        val digits = typed.filter(Char::isDigit).take(4)
                        year = digits
                        val value = digits.toIntOrNull()
                        when {
                            digits.isEmpty() -> onBirthYear(null)
                            ProfileSettings.validBirthYear(value, currentYear) -> onBirthYear(value)
                        }
                    },
                    label = { Text(ProfileTexts.BIRTH_YEAR) },
                    isError = wrong,
                    supportingText = { Text(if (wrong) ProfileTexts.birthYearWrong(currentYear) else ProfileTexts.BIRTH_YEAR_NOTE) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag(PROFILE_BIRTH_YEAR_TAG),
                )
                Text(ProfileTexts.SEX_TITLE, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SEX_ORDER.forEach { sex ->
                        Choice(ProfileTexts.sex(sex), settings.sexForCalc == sex, { onSex(sex) }, sexTag(sex))
                    }
                }
                Text(ProfileTexts.SEX_NOTE, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            }
        }
        HotOverline(ProfileTexts.LOCAL_ONLY)
    }
}

/** A61 §5.1: «он · она · не важно» — «не важно» is an equal choice, not a fallback (Agent 10). */
private val ADDRESS_ORDER = listOf(AddressForm.MASCULINE, AddressForm.FEMININE, AddressForm.NEUTRAL)
private val SEX_ORDER = listOf(SexForCalc.FEMALE, SexForCalc.MALE, SexForCalc.UNSPECIFIED)

/** A hairline capsule choice, 48 dp high, read as a radio button. */
@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    val accent = MaterialTheme.colorScheme.primary
    val chosen = selected
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) accent else HotThemeTokens.colors.rule),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .semantics {
                this.selected = chosen
                role = Role.RadioButton
            }
            .testTag(tag),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
}
