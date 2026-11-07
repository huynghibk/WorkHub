package com.workhub.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.WorkHubApi
import com.workhub.app.data.userMessage
import com.workhub.shared.MdmCloudDeviceDto
import com.workhub.shared.MdmDeviceDto
import com.workhub.shared.MdmEnrollmentTokenDto
import kotlinx.coroutines.launch

/**
 * @param loggedIn true = dùng JWT (/me/mdm/…); false = màn login, cần mã mời.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceEnrollScreen(
    loggedIn: Boolean,
    onBack: (() -> Unit)? = null,
) {
    val graph = LocalAppGraph.current
    val vm: DeviceEnrollViewModel = viewModel(factory = deviceEnrollFactory(graph.api, loggedIn))
    val context = LocalContext.current
    var inviteCode by remember { mutableStateOf("") }
    var cloudExpanded by remember { mutableStateOf(false) }
    var selectedCloud by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    LaunchedEffect(loggedIn) {
        if (loggedIn) vm.refreshBound()
    }

    val qrPayload = vm.token?.qrCode.orEmpty()
    val qr = remember(qrPayload) {
        if (qrPayload.isBlank()) null else runCatching { qrBitmap(qrPayload, 420).asImageBitmap() }.getOrNull()
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
                stringResource(R.string.enroll_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.enroll_steps_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.enroll_step_1), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=com.google.android.apps.work.clouddpc"),
                                    ),
                                )
                            }.onFailure {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "https://play.google.com/store/apps/details?id=com.google.android.apps.work.clouddpc",
                                        ),
                                    ),
                                )
                            }
                        },
                    ) {
                        Text(stringResource(R.string.enroll_open_dpc))
                    }
                    Text(stringResource(R.string.enroll_step_2), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.enroll_step_3), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!loggedIn) {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase() },
                    label = { Text(stringResource(R.string.enroll_invite_code)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Button(
                onClick = {
                    if (loggedIn) vm.createToken()
                    else vm.createTokenByInvite(inviteCode)
                },
                enabled = !vm.busy && (loggedIn || inviteCode.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.enroll_create_code))
            }

            if (qr != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.enroll_qr_title), fontWeight = FontWeight.SemiBold)
                        Image(
                            bitmap = qr,
                            contentDescription = stringResource(R.string.mdm_enroll_qr),
                            modifier = Modifier.size(220.dp),
                        )
                        Text(
                            stringResource(R.string.mdm_enroll_token, vm.token?.value.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (loggedIn) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.enroll_bind_title), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.enroll_bind_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExposedDropdownMenuBox(expanded = cloudExpanded, onExpandedChange = { cloudExpanded = it }) {
                            OutlinedTextField(
                                value = selectedCloud.substringAfterLast('/'),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.mdm_pick_cloud_device)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cloudExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = cloudExpanded, onDismissRequest = { cloudExpanded = false }) {
                                if (vm.cloud.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.mdm_no_cloud_devices)) },
                                        onClick = { cloudExpanded = false },
                                    )
                                }
                                vm.cloud.forEach { device ->
                                    DropdownMenuItem(
                                        text = { Text(device.name.substringAfterLast('/')) },
                                        onClick = {
                                            selectedCloud = device.name
                                            cloudExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text(stringResource(R.string.mdm_display_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                vm.selfBind(selectedCloud, displayName) {
                                    displayName = ""
                                    selectedCloud = ""
                                }
                            },
                            enabled = selectedCloud.isNotBlank() && !vm.busy,
                        ) {
                            Text(stringResource(R.string.enroll_bind_action))
                        }
                        if (vm.mine.isNotEmpty()) {
                            Text(stringResource(R.string.enroll_my_devices), fontWeight = FontWeight.SemiBold)
                            vm.mine.forEach { device ->
                                Text(
                                    "• ${device.displayName.ifBlank { device.deviceName.substringAfterLast('/') }}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

class DeviceEnrollViewModel(
    private val api: WorkHubApi,
    private val loggedIn: Boolean,
) : ViewModel() {
    var token by mutableStateOf<MdmEnrollmentTokenDto?>(null)
        private set
    var cloud by mutableStateOf<List<MdmCloudDeviceDto>>(emptyList())
        private set
    var mine by mutableStateOf<List<MdmDeviceDto>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun refreshBound() {
        if (!loggedIn) return
        viewModelScope.launch {
            runCatching {
                cloud = api.myMdmCloudDevices()
                mine = api.myMdmDevices()
            }
        }
    }

    fun createToken() = act {
        token = api.myMdmEnrollmentToken()
        message = "Đã tạo mã đăng ký"
        refreshBound()
    }

    fun createTokenByInvite(inviteCode: String) = act {
        token = api.enrollTokenByInvite(inviteCode)
        message = "Đã tạo mã đăng ký"
    }

    fun selfBind(deviceName: String, displayName: String, onDone: () -> Unit) = act {
        api.mdmSelfBind(deviceName, displayName)
        mine = api.myMdmDevices()
        cloud = api.myMdmCloudDevices()
        message = "Đã gắn máy với tài khoản của bạn"
        onDone()
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            error = null
            message = null
            try {
                block()
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }
}

private fun deviceEnrollFactory(api: WorkHubApi, loggedIn: Boolean) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DeviceEnrollViewModel(api, loggedIn) as T
}
