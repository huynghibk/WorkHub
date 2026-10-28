package com.workhub.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.WorkHubApi
import com.workhub.app.data.userMessage
import com.workhub.shared.MemberDto
import com.workhub.shared.OrgTree
import kotlinx.coroutines.launch

private val roleOptions = listOf("ADMIN", "SUPERVISOR", "MEMBER")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOrgScreen() {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val workspace = session?.workspace
    val vm: AdminOrgViewModel = viewModel(factory = adminOrgFactory(graph.api))
    val scope = rememberCoroutineScope()
    var divisionName by remember { mutableStateOf("") }
    var departmentName by remember { mutableStateOf("") }
    var teamName by remember { mutableStateOf("") }
    var divisionId by remember { mutableStateOf("") }
    var departmentId by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refresh() }
    val company = vm.tree.companies.firstOrNull()
    val division = company?.divisions?.firstOrNull { it.id == divisionId }
    val department = division?.departments?.firstOrNull { it.id == departmentId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tab_org)) })
        },
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
                stringResource(R.string.org_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!workspace?.inviteCode.isNullOrBlank()) {
                val code = workspace?.inviteCode.orEmpty()
                val context = LocalContext.current
                var copied by remember { mutableStateOf(false) }
                val qr = remember(code) { qrBitmap(code).asImageBitmap() }
                Text(stringResource(R.string.invite_code), style = MaterialTheme.typography.titleMedium)
                Text(
                    code,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clickable {
                        copyText(context, code)
                        copied = true
                    },
                )
                if (copied) {
                    Text(stringResource(R.string.copied), color = MaterialTheme.colorScheme.primary)
                }
                Image(
                    bitmap = qr,
                    contentDescription = stringResource(R.string.share_qr),
                    modifier = Modifier.size(180.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { shareInviteText(context, code) }) {
                        Text(stringResource(R.string.share_code))
                    }
                    Button(onClick = { shareInviteQr(context, code) }) {
                        Text(stringResource(R.string.share_qr))
                    }
                }
                Text(
                    stringResource(R.string.org_invite_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Text(stringResource(R.string.members), style = MaterialTheme.typography.titleMedium)
            vm.members.forEach { member ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(member.name, style = MaterialTheme.typography.bodyLarge)
                    Text(member.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        roleOptions.forEach { role ->
                            FilterChip(
                                selected = member.role == role,
                                onClick = {
                                    if (member.role == role || workspace == null) return@FilterChip
                                    vm.setRole(member.id, role) {
                                        if (member.id == session?.user?.id) {
                                            scope.launch {
                                                graph.session.openWorkspace(
                                                    workspace.copy(
                                                        role = role,
                                                        inviteCode = if (role == "ADMIN") workspace.inviteCode else "",
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                },
                                label = { Text(roleLabel(role)) },
                            )
                        }
                    }
                }
            }

            if (company != null) {
                OrgBlock(
                    title = stringResource(R.string.division),
                    value = divisionName,
                    onValue = { divisionName = it },
                    action = stringResource(R.string.create_division),
                    onCreate = {
                        vm.createDivision(divisionName) { createdId ->
                            divisionId = createdId
                            departmentId = ""
                            divisionName = ""
                        }
                    },
                ) {
                    company.divisions.forEach { item ->
                        FilterChip(
                            selected = item.id == divisionId,
                            onClick = {
                                divisionId = item.id
                                departmentId = ""
                            },
                            label = { Text(item.name) },
                        )
                    }
                }
            }

            if (division != null) {
                OrgBlock(
                    title = stringResource(R.string.department_of, division.name),
                    value = departmentName,
                    onValue = { departmentName = it },
                    action = stringResource(R.string.create_department),
                    onCreate = {
                        vm.createDepartment(division.id, departmentName) { createdId ->
                            departmentId = createdId
                            departmentName = ""
                        }
                    },
                ) {
                    division.departments.forEach { item ->
                        FilterChip(
                            selected = item.id == departmentId,
                            onClick = { departmentId = item.id },
                            label = { Text(item.name) },
                        )
                    }
                }
            }

            if (department != null) {
                OrgBlock(
                    title = stringResource(R.string.team_of, department.name),
                    value = teamName,
                    onValue = { teamName = it },
                    action = stringResource(R.string.create_team),
                    onCreate = { vm.createTeam(department.id, teamName) { teamName = "" } },
                ) {
                    department.teams.forEach { item ->
                        FilterChip(selected = false, onClick = {}, label = { Text(item.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun OrgBlock(
    title: String,
    value: String,
    onValue: (String) -> Unit,
    action: String,
    onCreate: () -> Unit,
    chips: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(stringResource(R.string.name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = onCreate, enabled = value.isNotBlank()) { Text(action) }
    }
}

class AdminOrgViewModel(private val api: WorkHubApi) : ViewModel() {
    var tree by mutableStateOf(OrgTree())
        private set
    var members by mutableStateOf<List<MemberDto>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        viewModelScope.launch {
            try {
                tree = api.orgTree()
                members = api.members()
                error = null
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun setRole(userId: String, role: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                api.setRole(userId, role)
                members = api.members()
                error = null
                onDone()
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun createDivision(name: String, onDone: (String) -> Unit) = create({ api.createDivision(name).id }, onDone)

    fun createDepartment(divisionId: String, name: String, onDone: (String) -> Unit) =
        create({ api.createDepartment(divisionId, name).id }, onDone)

    fun createTeam(departmentId: String, name: String, onDone: (String) -> Unit) =
        create({ api.createTeam(departmentId, name).id }, onDone)

    private fun create(call: suspend () -> String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val id = call()
                tree = api.orgTree()
                error = null
                onDone(id)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }
}

private fun adminOrgFactory(api: WorkHubApi) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AdminOrgViewModel(api) as T
    }
}
