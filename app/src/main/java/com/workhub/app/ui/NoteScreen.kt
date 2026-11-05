package com.workhub.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.workhub.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.OfflineStore
import com.workhub.app.data.userMessage
import com.workhub.shared.NoteDto
import com.workhub.shared.NoteWrite
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteScreen() {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val vm: NoteViewModel = viewModel(factory = noteFactory(graph.offline))
    val notes by vm.notes.collectAsStateWithLifecycle()
    val syncError by graph.offline.syncError.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<NoteDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    val pendingCreate by graph.pendingQuickCreate.collectAsState()
    LaunchedEffect(pendingCreate) {
        if (pendingCreate == "note") {
            graph.pendingQuickCreate.value = null
            creating = true
        }
    }

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
            FloatingActionButton(
                onClick = { creating = true },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_note))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            (vm.error ?: syncError)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (vm.loading && notes.isEmpty()) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else if (notes.isEmpty()) {
                EmptyHint(
                    title = stringResource(R.string.tab_notes),
                    body = stringResource(R.string.empty_notes_cta),
                    actionLabel = stringResource(R.string.add_note),
                    onAction = { creating = true },
                    icon = notesEmptyIcon(),
                )
            } else {
                val canManage = canManageContent(session?.workspace?.role)
                val myId = session?.user?.id
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        val canDelete = canManage || note.authorId == myId
                        SwipeRow(
                            enableComplete = false,
                            enableDelete = canDelete,
                            onComplete = {},
                            onDelete = { vm.delete(note.id) {} },
                            modifier = Modifier.animateItem(),
                        ) {
                            NoteCard(note, onClick = { editor = note })
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
private fun NoteCard(note: NoteDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = noteWash()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.dp, Color.Transparent),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(NoteRail),
            )
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 14.dp).weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    InitialsAvatar(note.authorName)
                }
            }
        }
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
                onInsertText = { chunk ->
                    body = if (body.isBlank()) chunk else body.trimEnd() + "\n" + chunk
                },
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

class NoteViewModel(private val offline: OfflineStore) : ViewModel() {
    val notes: StateFlow<List<NoteDto>> =
        offline.notes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
                offline.sync()
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
                offline.saveNote(existing, write)
                onDone()
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
                offline.deleteNote(id)
                onDone()
            } catch (e: Exception) {
                saveError = e.userMessage()
            } finally {
                saving = false
            }
        }
    }
}

private fun noteFactory(offline: OfflineStore) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return NoteViewModel(offline) as T
    }
}
