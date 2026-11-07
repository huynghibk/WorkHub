package com.workhub.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.WorkHubApi
import com.workhub.app.data.userMessage
import com.workhub.app.device.LocalDeviceAdmin
import com.workhub.app.device.MdmGuardService
import com.workhub.app.device.MdmPrefs
import com.workhub.shared.MdmDeviceDto
import kotlinx.coroutines.launch

/**
 * Đăng ký máy không cần Google / Android Device Policy.
 * Bật Device Admin của WorkHub + đăng ký deviceId lên server + service nền.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceEnrollScreen(
    loggedIn: Boolean,
    onBack: (() -> Unit)? = null,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val vm: DeviceEnrollViewModel = viewModel(factory = deviceEnrollFactory(graph.api))
    var adminActive by remember { mutableStateOf(LocalDeviceAdmin.isActive(context)) }
    var displayName by remember { mutableStateOf(LocalDeviceAdmin.deviceLabel()) }
    var inviteHint by remember { mutableStateOf("") }

    val enableAdmin = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        adminActive = LocalDeviceAdmin.isActive(context)
        if (adminActive && loggedIn) {
            vm.registerLocal(context, LocalDeviceAdmin.localDeviceId(context), displayName)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* trạng thái quyền cập nhật khi resume */ }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                adminActive = LocalDeviceAdmin.isActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.enroll_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.enroll_intro_local),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.enroll_disclosure_lost),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.enroll_steps_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.enroll_local_step_1), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.enroll_local_step_2), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.enroll_local_step_3), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(
                        selected = adminActive,
                        onClick = {},
                        label = {
                            Text(
                                if (adminActive) stringResource(R.string.enroll_admin_on)
                                else stringResource(R.string.enroll_admin_off),
                            )
                        },
                    )
                }
            }

            if (!loggedIn) {
                Text(
                    stringResource(R.string.enroll_need_login),
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    value = inviteHint,
                    onValueChange = { inviteHint = it },
                    label = { Text(stringResource(R.string.enroll_login_hint_field)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    supportingText = { Text(stringResource(R.string.enroll_need_login_help)) },
                )
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.mdm_display_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = loggedIn,
            )

            Button(
                onClick = {
                    enableAdmin.launch(LocalDeviceAdmin.requestEnableIntent(context))
                },
                enabled = loggedIn && !adminActive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.enroll_enable_admin))
            }

            OutlinedButton(
                onClick = {
                    val perms = buildList {
                        add(Manifest.permission.CAMERA)
                        add(Manifest.permission.READ_CONTACTS)
                        add(Manifest.permission.READ_SMS)
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) {
                            add(Manifest.permission.READ_MEDIA_IMAGES)
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        if (Build.VERSION.SDK_INT >= 29) {
                            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }.toTypedArray()
                    if (perms.isNotEmpty()) permissionLauncher.launch(perms)
                },
                enabled = loggedIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.enroll_grant_perms))
            }

            OutlinedButton(
                onClick = {
                    LocalDeviceAdmin.requestIgnoreBatteryIntent(context)?.let { enableAdmin.launch(it) }
                },
                enabled = loggedIn && !LocalDeviceAdmin.isIgnoringBatteryOptimizations(context),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.enroll_battery_exempt))
            }

            OutlinedButton(
                onClick = {
                    vm.registerLocal(context, LocalDeviceAdmin.localDeviceId(context), displayName)
                },
                enabled = loggedIn && adminActive && !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.enroll_register_server))
            }

            Text(
                stringResource(R.string.enroll_device_id, LocalDeviceAdmin.localDeviceId(context)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.enroll_owner_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (LocalDeviceAdmin.isDeviceOwner(context)) {
                            stringResource(R.string.enroll_owner_active)
                        } else {
                            stringResource(R.string.enroll_owner_help)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!LocalDeviceAdmin.isDeviceOwner(context)) {
                        Text(
                            LocalDeviceAdmin.deviceOwnerAdbCommand(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (vm.mine.isNotEmpty()) {
                Text(stringResource(R.string.enroll_my_devices), fontWeight = FontWeight.SemiBold)
                vm.mine.forEach { device ->
                    Text(
                        "• ${device.displayName.ifBlank { device.deviceName }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

class DeviceEnrollViewModel(private val api: WorkHubApi) : ViewModel() {
    var mine by mutableStateOf<List<MdmDeviceDto>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun registerLocal(context: Context, deviceId: String, displayName: String) {
        viewModelScope.launch {
            busy = true
            error = null
            message = null
            try {
                api.mdmRegisterLocal(deviceId, displayName)
                MdmPrefs.setEnrolled(context, true)
                LocalDeviceAdmin.applyOwnerProtections(context)
                MdmGuardService.startIfNeeded(context)
                mine = runCatching { api.myMdmDevices() }.getOrDefault(emptyList())
                message = "Đã đăng ký máy — dịch vụ nền đang chạy"
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }
}

private fun deviceEnrollFactory(api: WorkHubApi) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DeviceEnrollViewModel(api) as T
}
