package com.workhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.userMessage
import com.workhub.shared.WorkspaceDto
import kotlinx.coroutines.launch

private val SwitcherIndigo = Color(0xFF4F46E5)
private val SwitcherLine = Color(0xFFE5E7EB)
private val SwitcherColors = listOf(
    Color(0xFF4F46E5),
    Color(0xFF0284C7),
    Color(0xFF06B6D4),
    Color(0xFF10B981),
    Color(0xFF6366F1),
    Color(0xFF0EA5E9),
)

private enum class SwitcherMode { List, Create, Join, Rename }

@Composable
fun WorkspaceSwitcherSheet(onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val current = session?.workspace
    val scope = rememberCoroutineScope()
    var workspaces by remember { mutableStateOf<List<WorkspaceDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(SwitcherMode.List) }
    var draftName by remember { mutableStateOf("") }
    var draftCode by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                workspaces = graph.api.workspaces()
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (mode) {
                            SwitcherMode.List -> stringResource(R.string.switch_workspace)
                            SwitcherMode.Create -> stringResource(R.string.create_workspace)
                            SwitcherMode.Join -> stringResource(R.string.join_with_code)
                            SwitcherMode.Rename -> stringResource(R.string.settings_edit_workspace)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                    IconButton(onClick = { if (!busy) onDismiss() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                HorizontalDivider(color = SwitcherLine)
                when (mode) {
                    SwitcherMode.List -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                            if (loading && workspaces.isEmpty()) {
                                CircularProgressIndicator(Modifier.padding(16.dp))
                            } else {
                                workspaces.forEach { workspace ->
                                    WorkspaceSwitchRow(
                                        workspace = workspace,
                                        selected = workspace.id == current?.id,
                                        onClick = {
                                            if (workspace.id == current?.id) {
                                                onDismiss()
                                            } else {
                                                scope.launch {
                                                    busy = true
                                                    graph.session.openWorkspace(workspace)
                                                    busy = false
                                                    onDismiss()
                                                }
                                            }
                                        },
                                        onEdit = if (workspace.role == "ADMIN") {
                                            {
                                                scope.launch {
                                                    // Rename API targets current workspace header.
                                                    if (workspace.id != current?.id) {
                                                        graph.session.openWorkspace(workspace)
                                                    }
                                                    draftName = workspace.name
                                                    error = null
                                                    mode = SwitcherMode.Rename
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                                if (workspaces.isEmpty() && !loading) {
                                    Text(
                                        stringResource(R.string.workspace_empty),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = SwitcherLine)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SoftPrimaryButton(
                                text = stringResource(R.string.create_workspace),
                                onClick = {
                                    draftName = ""
                                    error = null
                                    mode = SwitcherMode.Create
                                },
                            )
                            TextButton(
                                onClick = {
                                    draftCode = ""
                                    error = null
                                    mode = SwitcherMode.Join
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.join_with_code))
                            }
                        }
                    }
                    SwitcherMode.Create, SwitcherMode.Rename -> {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SoftField(
                                value = draftName,
                                onValue = { draftName = it },
                                label = stringResource(R.string.workspace_name_label),
                            )
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            SoftPrimaryButton(
                                text = stringResource(R.string.save),
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        error = null
                                        try {
                                            if (mode == SwitcherMode.Rename) {
                                                val renamed = graph.api.renameWorkspace(draftName.trim())
                                                graph.session.openWorkspace(renamed)
                                                workspaces = graph.api.workspaces()
                                                mode = SwitcherMode.List
                                            } else {
                                                val created = graph.api.createWorkspace(draftName.trim())
                                                graph.session.openWorkspace(created)
                                                onDismiss()
                                            }
                                        } catch (e: Exception) {
                                            error = e.userMessage()
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                enabled = draftName.isNotBlank() && !busy,
                            )
                            TextButton(
                                onClick = {
                                    error = null
                                    mode = SwitcherMode.List
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    }
                    SwitcherMode.Join -> {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SoftField(
                                value = draftCode,
                                onValue = { draftCode = it },
                                label = stringResource(R.string.invite_code),
                            )
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            SoftPrimaryButton(
                                text = stringResource(R.string.join),
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        error = null
                                        try {
                                            val joined = graph.api.joinWorkspace(draftCode.trim())
                                            graph.session.openWorkspace(joined)
                                            onDismiss()
                                        } catch (e: Exception) {
                                            error = e.userMessage()
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                enabled = draftCode.isNotBlank() && !busy,
                            )
                            TextButton(
                                onClick = {
                                    error = null
                                    mode = SwitcherMode.List
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSwitchRow(
    workspace: WorkspaceDto,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    val color = SwitcherColors[(workspace.id.hashCode() ushr 1) % SwitcherColors.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) SwitcherIndigo else SwitcherLine, RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFFEEF2FF) else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                workspace.name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                workspace.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                roleLabel(workspace.role),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.settings_edit_workspace))
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = SwitcherIndigo)
        }
    }
}
