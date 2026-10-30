package com.workhub.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.workhub.app.BuildConfig
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.UserSettings
import com.workhub.app.data.userMessage
import com.workhub.shared.ProfileUpdate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SheetBg = Color(0xFFF9FAFB)
private val CardBg = Color.White
private val Accent = Color(0xFF6D28D9)
private val Danger = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingsSheet(
    onDismiss: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenOrg: () -> Unit,
    onInvite: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val settings by graph.userPrefs.state.collectAsState(initial = UserSettings())
    val user = session?.user
    val workspace = session?.workspace
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("root") } // root | notifications | settings | profile
    var statusDraft by remember(settings.status) { mutableStateOf(settings.status) }
    var nameDraft by remember(user?.name, user?.id) { mutableStateOf(user?.name.orEmpty()) }
    var usernameDraft by remember(user?.username, user?.id) { mutableStateOf(user?.username.orEmpty()) }
    var avatarDraft by remember(user?.avatarDataUrl, user?.id) { mutableStateOf(user?.avatarDataUrl.orEmpty()) }
    var profileBusy by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var profileSaved by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun applyAvatarUri(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            profileError = null
            runCatching {
                withContext(Dispatchers.IO) { AvatarCodec.encode(context, uri) }
            }.onSuccess {
                avatarDraft = it
                profileSaved = false
                if (page != "profile") page = "profile"
            }.onFailure {
                profileError = context.getString(R.string.settings_avatar_failed)
                if (page != "profile") page = "profile"
            }
        }
    }
    val pickGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> applyAvatarUri(uri) }
    val galleryPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        pickGallery.launch("image/*")
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showCamera = true
        } else {
            profileError = context.getString(R.string.settings_camera_denied)
            if (page != "profile") page = "profile"
        }
    }
    fun openGallery() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            pickGallery.launch("image/*")
        } else {
            galleryPermission.launch(permission)
        }
    }
    fun openCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            showCamera = true
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    if (showCamera) {
        AvatarCameraDialog(
            onCaptured = { uri ->
                showCamera = false
                applyAvatarUri(uri)
            },
            onDismiss = { showCamera = false },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(onClick = onDismiss),
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(SheetBg)
                    .clickable(enabled = false) {},
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    if (page != "root") {
                        TextButton(
                            onClick = {
                                page = when (page) {
                                    "rename_workspace" -> "settings"
                                    else -> "root"
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterStart),
                        ) {
                            Text(stringResource(R.string.back))
                        }
                    }
                    Text(
                        when (page) {
                            "notifications" -> stringResource(R.string.settings_customize_notifications)
                            "settings" -> stringResource(R.string.settings)
                            "profile" -> stringResource(R.string.settings_edit_profile)
                            "rename_workspace" -> stringResource(R.string.settings_edit_workspace)
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                when (page) {
                    "notifications" -> NotificationCustomizePage(
                        settings = settings,
                        onMute = { scope.launch { graph.userPrefs.setMute(it) } },
                        onAssigned = { scope.launch { graph.userPrefs.setNotifyAssigned(it) } },
                        onMentions = { scope.launch { graph.userPrefs.setNotifyMentions(it) } },
                        onChannel = { scope.launch { graph.userPrefs.setNotifyChannel(it) } },
                        onResolved = { scope.launch { graph.userPrefs.setNotifyResolved(it) } },
                        onReplies = { scope.launch { graph.userPrefs.setNotifyReplies(it) } },
                        onReactions = { scope.launch { graph.userPrefs.setNotifyReactions(it) } },
                        onReset = { scope.launch { graph.userPrefs.resetNotificationDefaults() } },
                    )
                    "settings" -> GeneralSettingsPage(
                        settings = settings,
                        onTheme = { scope.launch { graph.userPrefs.setTheme(it) } },
                        onHaptic = { scope.launch { graph.userPrefs.setHaptic(it) } },
                        onSound = { scope.launch { graph.userPrefs.setSound(it) } },
                        onLaunch = { scope.launch { graph.userPrefs.setLaunchPage(it) } },
                        isAdmin = workspace?.role == "ADMIN",
                        onInvite = onInvite,
                        onManageUsers = {
                            onDismiss()
                            onOpenOrg()
                        },
                        onEditWorkspace = { page = "rename_workspace" },
                    )
                    "rename_workspace" -> RenameWorkspacePage(
                        initialName = workspace?.name.orEmpty(),
                        onSaved = { page = "settings" },
                    )
                    "profile" -> EditProfilePage(
                        name = nameDraft,
                        email = user?.email.orEmpty(),
                        username = usernameDraft,
                        avatarDataUrl = avatarDraft,
                        busy = profileBusy,
                        error = profileError,
                        saved = profileSaved,
                        onName = {
                            nameDraft = it
                            profileSaved = false
                        },
                        onUsername = {
                            usernameDraft = it
                            profileSaved = false
                        },
                        onPickGallery = { openGallery() },
                        onTakePhoto = { openCamera() },
                        onClearAvatar = {
                            avatarDraft = ""
                            profileSaved = false
                        },
                        onSave = {
                            scope.launch {
                                profileBusy = true
                                profileError = null
                                profileSaved = false
                                try {
                                    val updated = graph.api.updateProfile(
                                        ProfileUpdate(
                                            name = nameDraft.trim(),
                                            username = usernameDraft.trim(),
                                            avatarDataUrl = avatarDraft,
                                        ),
                                    )
                                    graph.session.updateUser(updated)
                                    nameDraft = updated.name
                                    usernameDraft = updated.username
                                    avatarDraft = updated.avatarDataUrl
                                    profileSaved = true
                                } catch (e: Exception) {
                                    profileError = e.userMessage()
                                } finally {
                                    profileBusy = false
                                }
                            }
                        },
                        onDone = { page = "root" },
                    )
                    else -> RootSettingsPage(
                        userName = user?.name.orEmpty(),
                        username = user?.username.orEmpty(),
                        avatarDataUrl = user?.avatarDataUrl.orEmpty(),
                        email = user?.email.orEmpty(),
                        role = workspace?.role.orEmpty(),
                        workspaceName = workspace?.name.orEmpty(),
                        status = statusDraft,
                        onStatus = {
                            statusDraft = it
                            scope.launch { graph.userPrefs.setStatus(it) }
                        },
                        mute = settings.muteNotifications,
                        onMute = { scope.launch { graph.userPrefs.setMute(it) } },
                        onPlanner = {
                            onDismiss()
                            onOpenPlanner()
                        },
                        onEditProfile = {
                            nameDraft = user?.name.orEmpty()
                            usernameDraft = user?.username.orEmpty()
                            avatarDraft = user?.avatarDataUrl.orEmpty()
                            profileError = null
                            profileSaved = false
                            page = "profile"
                        },
                        onChangeAvatar = {
                            nameDraft = user?.name.orEmpty()
                            usernameDraft = user?.username.orEmpty()
                            avatarDraft = user?.avatarDataUrl.orEmpty()
                            profileError = null
                            profileSaved = false
                            page = "profile"
                            openGallery()
                        },
                        onCustomizeNotifications = { page = "notifications" },
                        onOpenSettings = { page = "settings" },
                        onLogout = { scope.launch { graph.session.clear() } },
                    )
                }
            }
        }
    }
}

@Composable
private fun RootSettingsPage(
    userName: String,
    username: String,
    avatarDataUrl: String,
    email: String,
    role: String,
    workspaceName: String,
    status: String,
    onStatus: (String) -> Unit,
    mute: Boolean,
    onMute: (Boolean) -> Unit,
    onPlanner: () -> Unit,
    onEditProfile: () -> Unit,
    onChangeAvatar: () -> Unit,
    onCustomizeNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                ProfileAvatar(name = userName, avatarDataUrl = avatarDataUrl, size = 56.dp)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Accent)
                        .clickable(onClick = onChangeAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.settings_change_avatar),
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(userName.ifBlank { "—" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (username.isNotBlank()) {
                    Text(
                        "@$username",
                        color = Color(0xFF6B7280),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${workspaceName.ifBlank { stringResource(R.string.workspace) }} · ${roleLabel(role)}",
                    color = Color(0xFF6B7280),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        OutlinedTextField(
            value = status,
            onValueChange = onStatus,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text(stringResource(R.string.settings_set_status)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedBorderColor = Color(0xFFE5E7EB),
                unfocusedBorderColor = Color(0xFFE5E7EB),
            ),
        )

        SettingsCard {
            SettingsNavRow(Icons.Filled.CalendarMonth, stringResource(R.string.settings_planner), onClick = onPlanner)
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsNavRow(Icons.Filled.Edit, stringResource(R.string.settings_edit_profile), onClick = onEditProfile)
        }

        SettingsSection(stringResource(R.string.settings_details)) {
            SettingsValueRow(stringResource(R.string.label_username), username.ifBlank { "—" })
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsValueRow(stringResource(R.string.label_email), email.ifBlank { "—" })
        }

        SettingsSection(stringResource(R.string.settings_priorities)) {
            Text(
                stringResource(R.string.settings_no_priorities),
                color = Color(0xFF9CA3AF),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        SettingsSection(stringResource(R.string.settings_notifications)) {
            SettingsToggleRow(
                icon = Icons.Filled.NotificationsOff,
                title = stringResource(R.string.settings_mute_notifications),
                checked = mute,
                onChecked = onMute,
            )
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsNavRow(Icons.Filled.Tune, stringResource(R.string.settings_customize), onClick = onCustomizeNotifications)
        }

        SettingsSection(stringResource(R.string.settings_general)) {
            SettingsNavRow(Icons.Filled.Settings, stringResource(R.string.settings), onClick = onOpenSettings)
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsNavRow(
                Icons.AutoMirrored.Filled.HelpOutline,
                stringResource(R.string.settings_help),
                onClick = {},
            )
        }

        SettingsCard {
            SettingsNavRow(
                Icons.AutoMirrored.Filled.Logout,
                stringResource(R.string.logout),
                tint = Danger,
                onClick = onLogout,
            )
        }

        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            color = Color(0xFF9CA3AF),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NotificationCustomizePage(
    settings: UserSettings,
    onMute: (Boolean) -> Unit,
    onAssigned: (Boolean) -> Unit,
    onMentions: (Boolean) -> Unit,
    onChannel: (Boolean) -> Unit,
    onResolved: (Boolean) -> Unit,
    onReplies: (Boolean) -> Unit,
    onReactions: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.settings_notify_mobile_about),
            color = Color(0xFF6B7280),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        SettingsSection(stringResource(R.string.settings_mentions_assignments)) {
            SettingsToggleRow(null, stringResource(R.string.settings_mute_notifications), settings.muteNotifications, onMute)
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsToggleRow(null, stringResource(R.string.settings_comments_assigned_me), settings.notifyAssignedComments, onAssigned)
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsToggleRow(null, stringResource(R.string.settings_comments_mentioned), settings.notifyMentions, onMentions)
        }
        SettingsSection(stringResource(R.string.settings_activity)) {
            SettingsToggleRow(null, stringResource(R.string.settings_comments_channel), settings.notifyChannelComments, onChannel)
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsToggleRow(null, stringResource(R.string.settings_resolved_comments), settings.notifyResolved, onResolved)
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsToggleRow(null, stringResource(R.string.settings_thread_replies), settings.notifyThreadReplies, onReplies)
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsToggleRow(null, stringResource(R.string.settings_reactions), settings.notifyReactions, onReactions)
        }
        TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_reset_defaults), color = Danger)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun GeneralSettingsPage(
    settings: UserSettings,
    onTheme: (String) -> Unit,
    onHaptic: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onLaunch: (String) -> Unit,
    isAdmin: Boolean,
    onInvite: () -> Unit,
    onManageUsers: () -> Unit,
    onEditWorkspace: () -> Unit,
) {
    val language = Locale.getDefault().displayLanguage
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSection(stringResource(R.string.settings_appearance)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeChoice("LIGHT", stringResource(R.string.settings_theme_light), Icons.Filled.LightMode, settings.themeMode == "LIGHT", Modifier.weight(1f)) { onTheme("LIGHT") }
                ThemeChoice("DARK", stringResource(R.string.settings_theme_dark), Icons.Filled.DarkMode, settings.themeMode == "DARK", Modifier.weight(1f)) { onTheme("DARK") }
                ThemeChoice("AUTO", stringResource(R.string.settings_theme_auto), Icons.Filled.BrightnessAuto, settings.themeMode == "AUTO", Modifier.weight(1f)) { onTheme("AUTO") }
            }
        }
        SettingsSection(stringResource(R.string.settings_preferences)) {
            SettingsNavRow(Icons.Filled.PhonelinkRing, stringResource(R.string.settings_launch_page), trailing = settings.launchPage) {
                onLaunch(if (settings.launchPage == "home") "tasks" else if (settings.launchPage == "tasks") "chat" else "home")
            }
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsToggleRow(Icons.Filled.Vibration, stringResource(R.string.settings_haptic), settings.hapticFeedback, onHaptic)
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsToggleRow(Icons.Filled.VolumeUp, stringResource(R.string.settings_sound), settings.soundEnabled, onSound)
            HorizontalDivider(color = Color(0xFFF3F4F6))
            SettingsValueRow(stringResource(R.string.settings_language), language, icon = Icons.Filled.Language)
        }
        SettingsSection(stringResource(R.string.workspace)) {
            SettingsNavRow(Icons.Filled.PersonAdd, stringResource(R.string.settings_invite_users), onClick = onInvite)
            if (isAdmin) {
                HorizontalDivider(color = Color(0xFFF3F4F6))
                SettingsNavRow(Icons.Filled.Groups, stringResource(R.string.settings_manage_users), onClick = onManageUsers)
            }
            if (isAdmin) {
                HorizontalDivider(color = Color(0xFFF3F4F6))
                SettingsNavRow(Icons.Filled.GroupAdd, stringResource(R.string.settings_edit_workspace), onClick = onEditWorkspace)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RenameWorkspacePage(
    initialName: String,
    onSaved: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    var name by remember(initialName) { mutableStateOf(initialName) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SoftField(
            value = name,
            onValue = { name = it },
            label = stringResource(R.string.workspace_name_label),
        )
        error?.let { Text(it, color = Danger) }
        SoftPrimaryButton(
            text = stringResource(R.string.save),
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        val renamed = withContext(Dispatchers.IO) {
                            graph.api.renameWorkspace(name.trim())
                        }
                        graph.session.openWorkspace(renamed)
                        onSaved()
                    } catch (e: Exception) {
                        error = e.userMessage()
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = name.isNotBlank() && !busy && name.trim() != initialName,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EditProfilePage(
    name: String,
    email: String,
    username: String,
    avatarDataUrl: String,
    busy: Boolean,
    error: String?,
    saved: Boolean,
    onName: (String) -> Unit,
    onUsername: (String) -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onClearAvatar: () -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ProfileAvatar(name = name, avatarDataUrl = avatarDataUrl, size = 88.dp, corner = 20.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .clickable(enabled = !busy, onClick = onTakePhoto),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = stringResource(R.string.settings_take_photo),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onPickGallery,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.settings_pick_gallery))
            }
            TextButton(
                onClick = onTakePhoto,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.settings_take_photo))
            }
        }
        if (avatarDataUrl.isNotBlank()) {
            TextButton(onClick = onClearAvatar, enabled = !busy) {
                Text(stringResource(R.string.settings_remove_avatar), color = Danger)
            }
        }
        SoftField(name, onName, stringResource(R.string.display_name))
        SoftField(username, onUsername, stringResource(R.string.label_username))
        SettingsCard {
            SettingsValueRow(stringResource(R.string.label_email), email.ifBlank { "—" })
        }
        Text(
            stringResource(R.string.settings_profile_local_hint),
            color = Color(0xFF9CA3AF),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(it, color = Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
        }
        if (saved) {
            Text(
                stringResource(R.string.settings_profile_saved),
                color = Color(0xFF059669),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SoftPrimaryButton(
            text = stringResource(R.string.save),
            onClick = onSave,
            enabled = !busy && name.isNotBlank() && username.isNotBlank(),
        )
        SoftPrimaryButton(text = stringResource(R.string.close), onClick = onDone, enabled = !busy)
    }
}

@Composable
private fun ThemeChoice(
    key: String,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, if (selected) Accent else Color(0xFFE5E7EB), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = key, tint = if (selected) Accent else Color(0xFF6B7280))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Accent else Color(0xFF374151))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF9CA3AF),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SettingsCard(content = content)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg),
        content = { content() },
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    tint: Color = Color(0xFF111827),
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(title, modifier = Modifier.weight(1f), color = tint, style = MaterialTheme.typography.bodyLarge)
        if (trailing != null) {
            Text(trailing, color = Color(0xFF9CA3AF), style = MaterialTheme.typography.bodyMedium)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color(0xFFD1D5DB))
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector?,
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color(0xFF111827), modifier = Modifier.size(22.dp))
        }
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
        )
    }
}

@Composable
private fun SettingsValueRow(label: String, value: String, icon: ImageVector? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color(0xFF111827), modifier = Modifier.size(22.dp))
        }
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = Color(0xFF6B7280), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
