package com.workhub.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.R
import com.workhub.app.data.AppGraph
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.userMessage
import com.workhub.shared.WorkspaceDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val avatarColors = listOf(
    Color(0xFF4F46E5),
    Color(0xFF0284C7),
    Color(0xFF06B6D4),
    Color(0xFF10B981),
    Color(0xFF6366F1),
    Color(0xFF0EA5E9),
)

private enum class HomeStep { Picker, Create, Invite }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceHomeScreen() {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val session by graph.session.state.collectAsState(initial = null)
    val vm: WorkspaceHomeViewModel = viewModel(factory = workspaceHomeFactory(graph))
    val scope = rememberCoroutineScope()
    val userName = session?.user?.name.orEmpty().ifBlank { "WorkHub" }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(HomeStep.Picker) }
    var pending by remember { mutableStateOf<WorkspaceDto?>(null) }
    var showInviteIntro by remember { mutableStateOf(false) }
    var preferJoin by remember { mutableStateOf(false) }
    val hasWorkspace = vm.workspaces.isNotEmpty()
    LaunchedEffect(Unit) { vm.refresh() }
    // Only auto-open create when the user truly has no workspaces (not on network errors).
    LaunchedEffect(userName, hasWorkspace, vm.loading, vm.error) {
        if (!vm.loading && !hasWorkspace && vm.error == null && step == HomeStep.Picker) {
            step = HomeStep.Create
            if (name.isBlank()) name = context.getString(R.string.default_workspace_name, userName)
        }
    }

    SoftBackdrop {
        when {
            step == HomeStep.Invite && pending != null -> {
                InviteTeammatesScreen(
                    inviteCode = pending!!.inviteCode,
                    onBack = { step = HomeStep.Create },
                    onDone = {
                        val workspace = pending ?: return@InviteTeammatesScreen
                        pending = null
                        showInviteIntro = false
                        vm.open(workspace)
                    },
                )
                if (showInviteIntro) {
                    InviteIntroSheet(
                        onInvite = { showInviteIntro = false },
                        onSkip = {
                            val workspace = pending ?: return@InviteIntroSheet
                            pending = null
                            showInviteIntro = false
                            vm.open(workspace)
                        },
                    )
                }
            }
            step == HomeStep.Create || !hasWorkspace -> {
                CreateWorkspaceScreen(
                    name = name,
                    onName = { name = it },
                    code = code,
                    onCode = { code = it },
                    busy = vm.busy,
                    error = vm.error,
                    startWithJoin = preferJoin,
                    onCreate = {
                        vm.create(name) { created ->
                            pending = created
                            showInviteIntro = true
                            step = HomeStep.Invite
                        }
                    },
                    onJoin = {
                        vm.join(code) {
                            code = ""
                            preferJoin = false
                            step = HomeStep.Picker
                        }
                    },
                    onLogout = { scope.launch { graph.session.clear() } },
                    showBack = hasWorkspace,
                    onBack = {
                        preferJoin = false
                        step = HomeStep.Picker
                    },
                )
            }
            else -> {
                WorkspacePicker(
                    workspaces = vm.workspaces,
                    loading = vm.loading,
                    error = vm.error,
                    onOpen = vm::open,
                    onCreate = {
                        name = context.getString(R.string.default_workspace_name, userName)
                        preferJoin = false
                        step = HomeStep.Create
                    },
                    onJoin = {
                        preferJoin = true
                        step = HomeStep.Create
                    },
                    onLogout = { scope.launch { graph.session.clear() } },
                )
            }
        }
    }
}

@Composable
private fun CreateWorkspaceScreen(
    name: String,
    onName: (String) -> Unit,
    code: String,
    onCode: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onLogout: () -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
    startWithJoin: Boolean = false,
) {
    var joinOpen by remember(startWithJoin) { mutableStateOf(startWithJoin) }
    Column(Modifier.fillMaxSize()) {
        BrandHero(height = 250.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.create_workspace),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.create_workspace_help),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.workspace_name_label), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SoftField(
                    value = name,
                    onValue = onName,
                    label = stringResource(R.string.workspace_name_label),
                    trailing = {
                        if (name.isNotBlank()) {
                            IconButton(onClick = { onName("") }) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        }
                    },
                )
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(28.dp))
            SoftPrimaryButton(
                text = stringResource(R.string.create_workspace),
                onClick = onCreate,
                enabled = name.isNotBlank() && !busy,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { joinOpen = !joinOpen }) {
                Text(stringResource(R.string.continue_join))
            }
            if (joinOpen) {
                SoftField(
                    value = code,
                    onValue = onCode,
                    label = stringResource(R.string.invite_code),
                )
                Spacer(Modifier.height(12.dp))
                SoftPrimaryButton(
                    text = stringResource(R.string.join),
                    onClick = onJoin,
                    enabled = code.isNotBlank() && !busy,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspacePicker(
    workspaces: List<WorkspaceDto>,
    loading: Boolean,
    error: String?,
    onOpen: (WorkspaceDto) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pick_workspace), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = stringResource(R.string.logout))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Surface(color = Color.Transparent) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SoftPrimaryButton(
                        text = stringResource(R.string.create),
                        onClick = onCreate,
                        modifier = Modifier.weight(1f),
                    )
                    SoftPrimaryButton(
                        text = stringResource(R.string.join),
                        onClick = onJoin,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (loading && workspaces.isEmpty()) {
                CircularProgressIndicator()
            } else {
                workspaces.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { workspace ->
                            WorkspaceAvatar(workspace, Modifier.weight(1f)) { onOpen(workspace) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                BrandHero(height = 240.dp, compact = true)
            }
        }
    }
}

@Composable
private fun InviteIntroSheet(onInvite: () -> Unit, onSkip: () -> Unit) {
    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(top = 80.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFEEF2FF), Color(0xFFF9FAFB)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                            listOf(Color(0xFF4F46E5), Color(0xFF06B6D4), Color(0xFF10B981)).forEachIndexed { index, color ->
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(listOf("A", "B", "C")[index], color = Color.White, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.invite_better_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.invite_better_sub),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    SoftPrimaryButton(
                        text = stringResource(R.string.invite_teammate),
                        onClick = onInvite,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.skip)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteTeammatesScreen(
    inviteCode: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                loading = true
                message = null
                contacts = withContext(Dispatchers.IO) { loadDeviceContacts(context) }
                loaded = true
                loading = false
                if (contacts.isEmpty()) message = context.getString(R.string.contacts_empty)
            }
        } else {
            message = context.getString(R.string.contacts_permission)
        }
    }

    fun importContacts() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            scope.launch {
                loading = true
                message = null
                contacts = withContext(Dispatchers.IO) { loadDeviceContacts(context) }
                loaded = true
                loading = false
                if (contacts.isEmpty()) message = context.getString(R.string.contacts_empty)
            }
        } else {
            permission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val filtered = contacts.filter {
        val text = query.trim()
        text.isBlank() ||
            it.name.contains(text, true) ||
            it.email.orEmpty().contains(text, true) ||
            it.phone.orEmpty().contains(text, true)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invite_teammates), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = onDone) { Text(stringResource(R.string.skip)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            SoftPrimaryButton(
                text = if (selected.isEmpty()) {
                    stringResource(R.string.invite_teammates)
                } else {
                    stringResource(R.string.invite_selected, selected.size)
                },
                onClick = {
                    val picks = contacts.filter { it.id in selected }
                    if (picks.isEmpty()) {
                        message = context.getString(R.string.invite_none)
                    } else {
                        shareInviteToContacts(context, inviteCode, picks)
                        onDone()
                    }
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.padding(16.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SoftField(
                    value = query,
                    onValue = { query = it },
                    label = stringResource(R.string.invite_by_email),
                    placeholder = stringResource(R.string.invite_by_email),
                    leading = { Icon(Icons.Filled.Search, contentDescription = null) },
                )
            }
            item {
                Surface(
                    shape = SoftCard,
                    color = Color.White.copy(alpha = 0.9f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                            listOf(Color(0xFF4F46E5), Color(0xFF06B6D4), Color(0xFF10B981)).forEachIndexed { index, color ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(2.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(listOf("A", "B", "C")[index], color = Color.White, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.import_contacts), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.import_contacts_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { importContacts() },
                            shape = SoftPill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(stringResource(R.string.import_action))
                        }
                    }
                }
            }
            if (loading) {
                item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (loaded) {
                items(filtered, key = { it.id }) { contact ->
                    val checked = contact.id in selected
                    Surface(
                        shape = SoftCard,
                        color = Color.White.copy(alpha = 0.92f),
                        onClick = {
                            selected = if (checked) selected - contact.id else selected + contact.id
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(avatarColors[contact.id.hashCode().ushr(1) % avatarColors.size]),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(contact.name.take(1).uppercase(), color = Color.White)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(
                                    contact.email ?: contact.phone.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selected = if (it) selected + contact.id else selected - contact.id
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceAvatar(workspace: WorkspaceDto, modifier: Modifier, onClick: () -> Unit) {
    val color = avatarColors[(workspace.id.hashCode() ushr 1) % avatarColors.size]
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                workspace.name.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(workspace.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Text(roleLabel(workspace.role), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

class WorkspaceHomeViewModel(private val graph: AppGraph) : ViewModel() {
    var workspaces by mutableStateOf<List<WorkspaceDto>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        viewModelScope.launch {
            loading = true
            try {
                workspaces = graph.api.workspaces()
                error = null
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    fun open(workspace: WorkspaceDto) {
        viewModelScope.launch { graph.session.openWorkspace(workspace) }
    }

    fun create(name: String, onCreated: (WorkspaceDto) -> Unit) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                val created = graph.api.createWorkspace(name)
                workspaces = graph.api.workspaces()
                onCreated(created)
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    fun join(code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                val joined = graph.api.joinWorkspace(code)
                graph.session.openWorkspace(joined)
                onDone()
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }
}

private fun workspaceHomeFactory(graph: AppGraph) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return WorkspaceHomeViewModel(graph) as T
    }
}
