package com.healthofatitan.app.feature.me.profile

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.core.account.AccountProfile
import com.healthofatitan.app.core.account.AccountProfileStore
import com.healthofatitan.app.core.account.AvatarImage
import com.healthofatitan.app.core.database.AboutMentorConsentStore
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotMark
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.text.text
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisGuard
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.engine.CrisisSource
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

const val PROFILE_AVATAR_TAG = "profile_avatar"
const val PROFILE_PHOTO_GALLERY_TAG = "profile_photo_gallery"
const val PROFILE_PHOTO_CAMERA_TAG = "profile_photo_camera"
const val PROFILE_PHOTO_DELETE_TAG = "profile_photo_delete"
const val PROFILE_NAME_YES_TAG = "profile_name_yes"
const val PROFILE_NAME_FIELD_TAG = "profile_name_field"
const val PROFILE_ABOUT_FIELD_TAG = "profile_about_field"
const val PROFILE_ABOUT_SAVE_TAG = "profile_about_save"
const val PROFILE_ABOUT_MENTOR_TAG = "profile_about_mentor"

/** What the account part of «Профиль» shows (A65 §2.4). */
data class AccountProfileUi(
    val profile: AccountProfile = AccountProfile(),
    val avatar: ByteArray? = null,
    val about: String = "",
    val aboutMentor: Boolean = false,
)

class AccountProfileActions(
    val onConfirmName: () -> Unit = {},
    val onName: (String) -> Unit = {},
    /** The picked or taken photo; the screen decodes nothing itself. */
    val onPhoto: (Uri) -> Unit = {},
    val onDeletePhoto: () -> Unit = {},
    /** «Сохранить» of «О себе» (CrisisGuard runs in the ViewModel). */
    val onAbout: (String) -> Unit = {},
    val onAboutMentor: (Boolean) -> Unit = {},
)

/**
 * The account part of «Профиль» (A65 §2.4, slice А4). Everything stays on the phone, sealed with
 * Tink; the avatar never goes to an LLM; «О себе» goes to Винс only with the consent
 * `about_mentor` (the mentor reads the flag in its own slice).
 */
@HiltViewModel
class AccountProfileViewModel @Inject constructor(
    private val store: AccountProfileStore,
    private val consent: AboutMentorConsentStore,
    private val dailyLoop: DailyLoopRepository,
) : ViewModel() {
    val profile: StateFlow<AccountProfile> = store.profile
    val avatar: StateFlow<ByteArray?> = store.avatar
    val about: StateFlow<String> = store.about
    val aboutMentor: StateFlow<Boolean> = consent.granted.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    var photoFailed by mutableStateOf(false)
        private set

    fun confirmName() = io { store.confirmName() }

    fun setName(name: String) = io { store.setName(name) }

    fun deletePhoto() = io { store.deleteAvatar() }

    fun setAboutMentor(granted: Boolean) {
        viewModelScope.launch { runCatching { consent.set(granted) } }
    }

    /** Crop, scale to 512 and re-encode (EXIF gone), then seal. The source file is not kept. */
    fun setPhoto(context: Context, uri: Uri) {
        val resolver = context.applicationContext.contentResolver
        viewModelScope.launch {
            val webp = withContext(Dispatchers.IO) { AvatarImage.square(ImageDecoder.createSource(resolver, uri)) }
            photoFailed = webp == null
            if (webp != null) withContext(Dispatchers.IO) { store.setOwnAvatar(webp) }
            if (uri.authority?.endsWith(AVATAR_AUTHORITY_SUFFIX) == true) runCatching { resolver.delete(uri, null, null) }
        }
    }

    /**
     * «О себе» is saved whatever it says: CrisisGuard runs on the words (A07 §4), only the level is
     * logged, never the text. Returns the assessment so the screen opens help at level ≥ 2.
     */
    suspend fun saveAbout(text: String): CrisisAssessment {
        val assessment = CrisisGuard.assess(text)
        withContext(Dispatchers.IO) { store.setAbout(text) }
        if (assessment.level != CrisisLevel.NONE) runCatching { dailyLoop.logSafety(assessment, CrisisSource.JOURNAL) }
        return assessment
    }

    private fun io(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runCatching(block) }
    }

    companion object {
        const val AVATAR_AUTHORITY_SUFFIX = ".avatar"
    }
}

/** Its own class: two `FileProvider` entries of one class would collide in the merged manifest. */
class AvatarFileProvider : FileProvider()

/** A cache file for the camera, shared through the FileProvider `${applicationId}.avatar`. */
internal fun cameraTarget(context: Context): Uri {
    val dir = File(context.cacheDir, "avatar").apply { mkdirs() }
    val file = File(dir, "camera.jpg")
    return FileProvider.getUriForFile(context, context.packageName + AccountProfileViewModel.AVATAR_AUTHORITY_SUFFIX, file)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.AccountProfileSection(
    ui: AccountProfileUi,
    actions: AccountProfileActions,
    photoFailed: Boolean = false,
    aboutSaved: Boolean = false,
) {
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(actions.onPhoto) }
    var pendingCamera by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = pendingCamera
        pendingCamera = null
        if (ok && target != null) actions.onPhoto(target)
    }
    val p = ui.profile

    HotCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(ui.avatar)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        p.name ?: AccountProfileTexts.NAME_EMPTY.text(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    p.googleEmailMask?.let {
                        Text(AccountProfileTexts.googleLine(it).text(), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HotTextButton(
                    AccountProfileTexts.PHOTO_GALLERY.text(),
                    { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    Modifier.testTag(PROFILE_PHOTO_GALLERY_TAG),
                )
                HotTextButton(
                    AccountProfileTexts.PHOTO_CAMERA.text(),
                    {
                        runCatching { cameraTarget(context) }.getOrNull()?.let { target ->
                            pendingCamera = target
                            camera.launch(target)
                        }
                    },
                    Modifier.testTag(PROFILE_PHOTO_CAMERA_TAG),
                )
                if (ui.avatar != null) {
                    HotTextButton(AccountProfileTexts.PHOTO_DELETE.text(), actions.onDeletePhoto, Modifier.testTag(PROFILE_PHOTO_DELETE_TAG))
                }
            }
            if (photoFailed) Text(AccountProfileTexts.PHOTO_FAILED.text(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(AccountProfileTexts.PHOTO_NOTE.text(), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            NameEditor(p, actions)
        }
    }

    HotCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(AccountProfileTexts.BODY.text(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (p.heightCm == null && p.maxPulse == null) {
                Text(AccountProfileTexts.BODY_NONE.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            p.heightCm?.let { Text(AccountProfileTexts.height(it).text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
            p.maxPulse?.let { Text(AccountProfileTexts.maxPulse(it).text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
        }
    }

    HotCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(AccountProfileTexts.ABOUT.text(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            var draft by rememberSaveable(ui.about) { mutableStateOf(ui.about) }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(AccountProfile.ABOUT_MAX) },
                placeholder = { Text(AccountProfileTexts.ABOUT_HINT.text()) },
                supportingText = { Text(AccountProfileTexts.aboutCount(draft.length, AccountProfile.ABOUT_MAX).text()) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag(PROFILE_ABOUT_FIELD_TAG),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HotSecondaryButton(
                    AccountProfileTexts.ABOUT_SAVE.text(),
                    { actions.onAbout(draft) },
                    Modifier.testTag(PROFILE_ABOUT_SAVE_TAG),
                    enabled = draft.trim() != ui.about,
                )
                if (ui.about.isNotEmpty()) {
                    HotTextButton(AccountProfileTexts.ABOUT_CLEAR.text(), {
                        draft = ""
                        actions.onAbout("")
                    })
                }
            }
            if (aboutSaved) Text(AccountProfileTexts.ABOUT_SAVED.text(), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            Text(AccountProfileTexts.ABOUT_NOTE.text(), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    AccountProfileTexts.ABOUT_MENTOR.text(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = ui.aboutMentor, onCheckedChange = actions.onAboutMentor, modifier = Modifier.testTag(PROFILE_ABOUT_MENTOR_TAG))
            }
            Text(AccountProfileTexts.ABOUT_MENTOR_NOTE.text(), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameEditor(p: AccountProfile, actions: AccountProfileActions) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val name = p.name
    if (editing || name == null) {
        var typed by rememberSaveable(p.name) { mutableStateOf(p.name.orEmpty()) }
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it.take(AccountProfile.NAME_MAX) },
            label = { Text(AccountProfileTexts.NAME.text()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(PROFILE_NAME_FIELD_TAG),
        )
        HotTextButton(AccountProfileTexts.NAME_SAVE.text(), {
            actions.onName(typed)
            editing = false
        }, enabled = typed.trim() != p.name.orEmpty())
    } else if (p.asksName) {
        Text(AccountProfileTexts.nameAsk(name).text(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HotSecondaryButton(AccountProfileTexts.NAME_YES.text(), actions.onConfirmName, Modifier.testTag(PROFILE_NAME_YES_TAG))
            HotTextButton(AccountProfileTexts.NAME_EDIT.text(), { editing = true })
        }
    } else {
        HotTextButton(AccountProfileTexts.NAME_EDIT.text(), { editing = true })
    }
}

/** The own or Google photo in a circle; without one, the Titan sign (A65 §2.4). */
@Composable
private fun Avatar(bytes: ByteArray?) {
    val image = remember(bytes) { bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() } }
    val description = AccountProfileTexts.AVATAR_DESCRIPTION.text()
    Box(
        Modifier.size(64.dp).clip(CircleShape).semantics { contentDescription = description }.testTag(PROFILE_AVATAR_TAG),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
        } else {
            HotMark(size = 40.dp)
        }
    }
}
