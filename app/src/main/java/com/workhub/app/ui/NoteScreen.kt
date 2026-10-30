package com.workhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.workhub.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.WorkHubApi
import com.workhub.app.data.userMessage
import com.workhub.shared.NoteDto
import com.workhub.shared.NoteWrite
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen() {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val vm: NoteViewModel = viewModel(factory = noteFactory(graph.api))
    var editor by remember { mutableStateOf<NoteDto?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_notes)) },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_note))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (vm.loading && vm.notes.isEmpty()) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else if (vm.notes.isEmpty()) {
                Text(stringResource(R.string.notes_empty), modifier = Modifier.padding(16.dp))
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(vm.notes.size, key = { vm.notes[it].id }) { index ->
                        val note = vm.notes[index]
                        Card(onClick = { editor = note }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (note.body.isNotBlank()) {
                                    Text(note.body, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    buildString {
                                        append(note.authorName)
                                        append(" \u00B7 ")
                                        append(formatTime(note.updatedAt))
                                        note.dueAt?.let {
                                            append(" \u00B7 ")
                                            append(formatDay(it))
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editor != null) {
        NoteEditor(
            initial = editor,
            canSave = editor == null || canManageContent(session?.workspace?.role) || editor?.authorId == session?.user?.id,
            saving = vm.saving,
            saveError = vm.saveError,
            onDismiss = {
                if (!vm.saving) {
                    creating = false
                    editor = null
                    vm.clearSaveError()
                }
            },
            onSave = { write ->
                vm.save(editor, write) {
                    creating = false
                    editor = null
                }
            },
            onDelete = editor?.let { note ->
                if (canManageContent(session?.workspace?.role) || note.authorId == session?.user?.id) {
                    { vm.delete(note.id) { creating = false; editor = null } }
                } else {
                    null
                }
            },
        )
    }
}

@Composable
private fun NoteEditor(
    initial: NoteDto?,
    canSave: Boolean,
    saving: Boolean,
    saveError: String?,
    onDismiss: () -> Unit,
    onSave: (NoteWrite) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var body by remember(initial?.id) { mutableStateOf(initial?.body.orEmpty()) }
    var dueAt by remember(initial?.id) { mutableStateOf(initial?.dueAt) }
    var locationUrl by remember(initial?.id) { mutableStateOf(initial?.locationUrl) }
    var formError by remember { mutableStateOf<String?>(null) }
    val titleError = stringResource(R.string.task_title_required)
    EditorDialog(
        title = if (initial == null) {
            stringResource(R.string.note_new)
        } else if (canSave) {
            stringResource(R.string.note_edit)
        } else {
            stringResource(R.string.note_view)
        },
        onDismiss = onDismiss,
        canSave = canSave,
        saving = saving,
        error = saveError ?: formError,
        onSave = {
            if (title.isBlank()) {
                formError = titleError
                return@EditorDialog
            }
            formError = null
            onSave(NoteWrite(title.trim(), body.trim(), dueAt, locationUrl))
        },
        onDelete = onDelete,
    ) {
        OutlinedField(title, { title = it }, stringResource(R.string.field_title))
        OutlinedField(body, { body = it }, stringResource(R.string.field_body), singleLine = false)
        DateField(stringResource(R.string.note_due), dueAt, { dueAt = it }, enabled = canSave && !saving)
        if (canSave) {
            LocationAttachActions(
                enabled = !saving,
                locationUrl = locationUrl,
                onLocationChange = { locationUrl = it },
            )
        } else if (!locationUrl.isNullOrBlank()) {
            AttachedLocationBar(locationUrl = locationUrl)
        }
    }
}

@Composable
internal fun EditorDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    canSave: Boolean = true,
    saving: Boolean = false,
    error: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 520.dp)
                .heightIn(max = maxHeight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HideKeyboardButton(always = true)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE5E7EB)),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE5E7EB)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    }
                    Row {
                        if (onDelete != null) {
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                        }
                        if (canSave) {
                            TextButton(onClick = onSave, enabled = !saving) {
                                if (saving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class NoteViewModel(private val api: WorkHubApi) : ViewModel() {
    var notes by mutableStateOf<List<NoteDto>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun clearSaveError() {
        saveError = null
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                notes = api.notes()
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    fun save(existing: NoteDto?, write: NoteWrite, onDone: () -> Unit) {
        viewModelScope.launch {
            saving = true
            saveError = null
            try {
                val saved = if (existing == null) {
                    api.createNote(write)
                } else {
                    api.updateNote(existing.id, write)
                }
                // Keep UI in sync even if a follow-up list refresh fails (Render wake race).
                notes = if (existing == null) {
                    listOf(saved) + notes.filter { it.id != saved.id }
                } else {
                    notes.map { if (it.id == saved.id) saved else it }
                }
                onDone()
                runCatching { notes = api.notes() }
            } catch (e: Exception) {
                saveError = e.userMessage()
            } finally {
                saving = false
            }
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            saving = true
            saveError = null
            try {
                api.deleteNote(id)
                notes = notes.filter { it.id != id }
                onDone()
                runCatching { notes = api.notes() }
            } catch (e: Exception) {
                saveError = e.userMessage()
            } finally {
                saving = false
            }
        }
    }
}

private fun noteFactory(api: WorkHubApi) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return NoteViewModel(api) as T
    }
}
