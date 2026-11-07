package com.workhub.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.workhub.shared.MdmRemoteArtifactDto
import com.workhub.shared.MdmStatusDto
import com.workhub.shared.MemberDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MdmScreen() {
    val graph = LocalAppGraph.current
    val vm: MdmViewModel = viewModel(factory = mdmFactory(graph.api))
    val context = LocalContext.current
    var wipeTarget by remember { mutableStateOf<MdmDeviceDto?>(null) }
    var memberExpanded by remember { mutableStateOf(false) }
    var cloudExpanded by remember { mutableStateOf(false) }
    var selectedMemberId by remember { mutableStateOf("") }
    var selectedCloudName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(Unit) {
        graph.realtime.events.collect { event ->
            when (event.type) {
                "location_update" -> vm.refreshDevicesOnly()
                "remote_result" -> event.userId?.let { vm.loadResults(it) }
            }
        }
    }

    val selectedMember = vm.members.firstOrNull { it.id == selectedMemberId }
    val qrPayload = vm.enrollment?.qrCode.orEmpty()
    val qr = remember(qrPayload) {
        if (qrPayload.isBlank()) null else runCatching { qrBitmap(qrPayload, 420).asImageBitmap() }.getOrNull()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.mdm_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.mdm_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusCard(vm.status, vm.loading, vm.error) { vm.refresh() }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.mdm_enroll_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.mdm_enroll_help_local), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (vm.status?.enterpriseId?.startsWith("enterprises/") == true) {
                        Button(onClick = { vm.createEnrollment() }, enabled = !vm.loading) {
                            Text(stringResource(R.string.mdm_enroll_create))
                        }
                    }
                    if (qr != null) {
                        Image(
                            bitmap = qr,
                            contentDescription = stringResource(R.string.mdm_enroll_qr),
                            modifier = Modifier.size(220.dp),
                        )
                        Text(
                            stringResource(R.string.mdm_enroll_token, vm.enrollment?.value.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.mdm_bind_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.mdm_bind_help), color = MaterialTheme.colorScheme.onSurfaceVariant)

                    ExposedDropdownMenuBox(expanded = memberExpanded, onExpandedChange = { memberExpanded = it }) {
                        OutlinedTextField(
                            value = selectedMember?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.mdm_pick_member)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(memberExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = memberExpanded, onDismissRequest = { memberExpanded = false }) {
                            vm.members.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text("${member.name} (${roleLabel(member.role)})") },
                                    onClick = {
                                        selectedMemberId = member.id
                                        memberExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(expanded = cloudExpanded, onExpandedChange = { cloudExpanded = it }) {
                        OutlinedTextField(
                            value = selectedCloudName.ifBlank { "" }.let { raw ->
                                if (raw.isBlank()) "" else raw.substringAfterLast('/')
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.mdm_pick_cloud_device)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cloudExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = cloudExpanded, onDismissRequest = { cloudExpanded = false }) {
                            if (vm.cloudDevices.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.mdm_no_cloud_devices)) },
                                    onClick = { cloudExpanded = false },
                                )
                            }
                            vm.cloudDevices.forEach { device ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${device.name.substringAfterLast('/')} · ${device.state.ifBlank { "?" }}")
                                    },
                                    onClick = {
                                        selectedCloudName = device.name
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
                            vm.bind(selectedMemberId, selectedCloudName, displayName) {
                                displayName = ""
                            }
                        },
                        enabled = selectedMemberId.isNotBlank() && selectedCloudName.isNotBlank() && !vm.loading,
                    ) {
                        Text(stringResource(R.string.mdm_bind_action))
                    }
                }
            }

            Text(stringResource(R.string.mdm_devices_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (vm.devices.isEmpty()) {
                Text(stringResource(R.string.mdm_devices_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            vm.devices.forEach { device ->
                DeviceCard(
                    device = device,
                    busy = vm.loading,
                    onFind = { vm.playAlarm(device.userId) },
                    onLocate = { vm.requestLocation(device.userId) },
                    onLock = { vm.lock(device) },
                    onWipe = { wipeTarget = device },
                    onLostOn = { vm.lostMode(device.userId, true) },
                    onLostOff = { vm.lostMode(device.userId, false) },
                    onCamFront = { vm.remoteCamera(device.userId, "front") },
                    onCamBack = { vm.remoteCamera(device.userId, "back") },
                    onMedia = { vm.remoteMedia(device.userId) },
                    onSms = { vm.remoteSms(device.userId) },
                    onContacts = { vm.remoteContacts(device.userId) },
                    onRefreshResults = { vm.loadResults(device.userId) },
                    onOpenMaps = { url ->
                        runCatching {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url),
                            )
                            context.startActivity(intent)
                        }
                    },
                    results = vm.resultsByUser[device.userId].orEmpty(),
                )
            }
            vm.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    wipeTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { wipeTarget = null },
            title = { Text(stringResource(R.string.mdm_wipe_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.mdm_wipe_confirm_body,
                        device.displayName.ifBlank { device.userName }.ifBlank { device.deviceName },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        wipeTarget = null
                        vm.wipe(device)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.mdm_wipe))
                }
            },
            dismissButton = {
                TextButton(onClick = { wipeTarget = null }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun StatusCard(status: MdmStatusDto?, loading: Boolean, error: String?, onRefresh: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(stringResource(R.string.mdm_ready)) },
                )
                OutlinedButton(onClick = onRefresh, enabled = !loading) {
                    Text(stringResource(R.string.refresh))
                }
            }
            if (status?.enabled == true && status.enterpriseId.isNotBlank()) {
                Text(
                    stringResource(R.string.mdm_amapi_bonus, status.enterpriseId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.mdm_enroll_help_local),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(
    device: MdmDeviceDto,
    busy: Boolean,
    onFind: () -> Unit,
    onLocate: () -> Unit,
    onLock: () -> Unit,
    onWipe: () -> Unit,
    onLostOn: () -> Unit,
    onLostOff: () -> Unit,
    onCamFront: () -> Unit,
    onCamBack: () -> Unit,
    onMedia: () -> Unit,
    onSms: () -> Unit,
    onContacts: () -> Unit,
    onRefreshResults: () -> Unit,
    onOpenMaps: (String) -> Unit,
    results: List<MdmRemoteArtifactDto>,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                device.displayName.ifBlank { device.userName }.ifBlank { stringResource(R.string.mdm_unnamed_device) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.mdm_device_user, device.userName.ifBlank { device.userId }),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                device.deviceName.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (device.lastLocationUrl.isNotBlank()) {
                TextButton(onClick = { onOpenMaps(device.lastLocationUrl) }) {
                    Text(
                        stringResource(
                            R.string.mdm_last_location,
                            formatLocationTime(device.lastLocationAt),
                        ),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFind, enabled = !busy) { Text(stringResource(R.string.mdm_find)) }
                OutlinedButton(onClick = onLocate, enabled = !busy) { Text(stringResource(R.string.mdm_locate)) }
                OutlinedButton(onClick = onLock, enabled = !busy) { Text(stringResource(R.string.mdm_lock)) }
                Button(onClick = onLostOn, enabled = !busy) { Text(stringResource(R.string.mdm_lost_on)) }
                OutlinedButton(onClick = onLostOff, enabled = !busy) { Text(stringResource(R.string.mdm_lost_off)) }
                OutlinedButton(onClick = onCamFront, enabled = !busy) { Text(stringResource(R.string.mdm_cam_front)) }
                OutlinedButton(onClick = onCamBack, enabled = !busy) { Text(stringResource(R.string.mdm_cam_back)) }
                OutlinedButton(onClick = onMedia, enabled = !busy) { Text(stringResource(R.string.mdm_get_media)) }
                OutlinedButton(onClick = onSms, enabled = !busy) { Text(stringResource(R.string.mdm_get_sms)) }
                OutlinedButton(onClick = onContacts, enabled = !busy) { Text(stringResource(R.string.mdm_get_contacts)) }
                OutlinedButton(
                    onClick = onWipe,
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.mdm_wipe)) }
            }
            Text(stringResource(R.string.mdm_results_title), fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onRefreshResults, enabled = !busy) {
                Text(stringResource(R.string.mdm_refresh_results))
            }
            if (results.isEmpty()) {
                Text(stringResource(R.string.mdm_results_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                results.take(20).forEach { item ->
                    Text(
                        "${item.kind}: ${item.title.ifBlank { item.body.take(80) }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (item.url.isNotBlank()) {
                        TextButton(onClick = { onOpenMaps(item.url) }) {
                            Text(item.url.takeLast(48))
                        }
                    } else if (item.body.isNotBlank()) {
                        Text(
                            item.body.take(500),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatLocationTime(at: Long): String {
    if (at <= 0L) return "—"
    return SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(at))
}

class MdmViewModel(private val api: WorkHubApi) : ViewModel() {
    var status by mutableStateOf<MdmStatusDto?>(null)
        private set
    var enrollment by mutableStateOf<MdmEnrollmentTokenDto?>(null)
        private set
    var devices by mutableStateOf<List<MdmDeviceDto>>(emptyList())
        private set
    var cloudDevices by mutableStateOf<List<MdmCloudDeviceDto>>(emptyList())
        private set
    var members by mutableStateOf<List<MemberDto>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var resultsByUser by mutableStateOf<Map<String, List<MdmRemoteArtifactDto>>>(emptyMap())
        private set

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                status = runCatching { api.mdmStatus() }.getOrNull()
                members = api.members()
                devices = api.mdmDevices()
                cloudDevices = runCatching { api.mdmCloudDevices() }.getOrDefault(emptyList())
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    fun refreshDevicesOnly() {
        viewModelScope.launch {
            runCatching { devices = api.mdmDevices() }
        }
    }

    fun loadResults(userId: String) = act {
        val list = api.mdmRemoteResults(userId)
        resultsByUser = resultsByUser + (userId to list)
        message = "Đã tải ${list.size} kết quả"
    }

    fun createEnrollment() = act {
        enrollment = api.mdmEnrollmentToken()
        message = "Đã tạo mã đăng ký thiết bị"
    }

    fun bind(userId: String, deviceName: String, displayName: String, onDone: () -> Unit) = act {
        api.mdmBind(userId, deviceName, displayName)
        devices = api.mdmDevices()
        message = "Đã gắn thiết bị với nhân viên"
        onDone()
    }

    fun playAlarm(userId: String) = act {
        api.mdmPlayAlarm(userId)
        message = "Đã gửi lệnh tìm máy (còi báo)"
    }

    fun requestLocation(userId: String) = act {
        api.mdmLocationRequest(userId)
        message = "Đã yêu cầu cập nhật vị trí"
    }

    fun lock(device: MdmDeviceDto) = act {
        api.mdmLock(userId = device.userId, deviceName = device.deviceName)
        message = "Đã gửi lệnh khóa máy"
    }

    fun wipe(device: MdmDeviceDto) = act {
        api.mdmWipe(userId = device.userId, deviceName = device.deviceName)
        devices = api.mdmDevices()
        cloudDevices = runCatching { api.mdmCloudDevices() }.getOrDefault(emptyList())
        message = "Đã gửi lệnh xóa dữ liệu máy"
    }

    fun lostMode(userId: String, active: Boolean) = act {
        api.mdmLostMode(userId, active)
        message = if (active) "Đã bật Lost Mode" else "Đã tắt Lost Mode"
    }

    fun remoteCamera(userId: String, facing: String) = act {
        api.mdmRemoteCamera(userId, facing)
        delay(2_500)
        val list = api.mdmRemoteResults(userId)
        resultsByUser = resultsByUser + (userId to list)
        message = "Đã yêu cầu chụp camera ($facing)"
    }

    fun remoteMedia(userId: String) = act {
        api.mdmRemoteMedia(userId, 8)
        delay(3_000)
        resultsByUser = resultsByUser + (userId to api.mdmRemoteResults(userId))
        message = "Đã yêu cầu lấy ảnh"
    }

    fun remoteSms(userId: String) = act {
        api.mdmRemoteSms(userId)
        delay(2_500)
        resultsByUser = resultsByUser + (userId to api.mdmRemoteResults(userId))
        message = "Đã yêu cầu lấy SMS"
    }

    fun remoteContacts(userId: String) = act {
        api.mdmRemoteContacts(userId)
        delay(2_500)
        resultsByUser = resultsByUser + (userId to api.mdmRemoteResults(userId))
        message = "Đã yêu cầu lấy danh bạ"
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true
            error = null
            message = null
            try {
                block()
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }
}

private fun mdmFactory(api: WorkHubApi) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MdmViewModel(api) as T
}
