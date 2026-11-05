package com.workhub.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Shortcut
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.outlined.Schedule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.R
import com.workhub.app.data.ChatStore
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.WorkHubApi
import com.workhub.app.data.userMessage
import com.workhub.shared.ChannelDto
import com.workhub.shared.MemberDto
import com.workhub.shared.MessageDto
import com.workhub.shared.NoteDto
import com.workhub.shared.SearchHit
import com.workhub.shared.TaskDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onOpenMaps: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val chromeVisible by graph.chatChromeVisible.collectAsState()
    val myId = session?.user?.id
    val vm: ChatViewModel = viewModel(factory = chatFactory(graph))
    val allChannels by vm.channels.collectAsStateWithLifecycle()
    val chatMessages by vm.messages.collectAsStateWithLifecycle()
    val sendingIds by vm.pendingIds.collectAsStateWithLifecycle()
    val failedIds by vm.failedIds.collectAsStateWithLifecycle()
    val tasks by graph.offline.tasks.collectAsStateWithLifecycle(emptyList())
    val notes by graph.offline.notes.collectAsStateWithLifecycle(emptyList())
    val taskById = remember(tasks) { tasks.associateBy { it.id } }
    val noteById = remember(notes) { notes.associateBy { it.id } }
    var failedMessage by remember { mutableStateOf<MessageDto?>(null) }
    val sendError by graph.chat.sendError.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var draft by remember { mutableStateOf("") }
    var attachedLocation by remember { mutableStateOf<String?>(null) }
    var shareMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        graph.chatChromeVisible.value = false
        graph.attachLink.collect { link ->
            attachedLocation = normalizeMapsShareLink(link) ?: link
        }
    }
    LaunchedEffect(Unit) {
        graph.pendingChatChannelId.collect { channelId ->
            if (channelId == null) return@collect
            graph.pendingChatChannelId.value = null
            val match = vm.channels.value.firstOrNull { it.id == channelId }
            if (match != null) {
                vm.open(match)
            } else {
                vm.openById(channelId)
            }
            if (drawerState.isOpen) drawerState.close()
        }
    }
    DisposableEffect(Unit) {
        onDispose { graph.chatChromeVisible.value = false }
    }
    // After returning from WeChat / background: refresh messages + rely on RealtimeHub restart.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.refreshQuiet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var newChat by remember { mutableStateOf(false) }
    var newChatPicks by remember { mutableStateOf(setOf<String>()) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    val mentionQuery = draft.substringAfterLast(' ').let { token ->
        if (token.startsWith("@")) token.drop(1) else null
    }
    val channels = allChannels.filter { it.kind.equals("CHANNEL", ignoreCase = true) || it.kind.isBlank() }
    val directs = allChannels.filter { it.kind.equals("DIRECT", ignoreCase = true) || it.kind.equals("DM", ignoreCase = true) }
    val groups = allChannels.filter { it.kind.equals("GROUP", ignoreCase = true) }
    val otherChannels = allChannels.filter { ch ->
        channels.none { it.id == ch.id } && directs.none { it.id == ch.id } && groups.none { it.id == ch.id }
    }

    DisposableEffect(vm.channel?.id) {
        AppVisibility.channelId = vm.channel?.id
        onDispose { AppVisibility.channelId = null }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.lastIndex)
    }

    fun openChannel(channel: ChannelDto) {
        vm.open(channel)
        scope.launch { drawerState.close() }
    }

    // Close drawer first; otherwise go home (devices without edge gesture still need a path).
    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            vm.thread != null -> vm.closeThread()
            vm.channel != null -> vm.closeConversation()
            else -> onNavigateHome()
        }
    }
    val latest by vm.latest.collectAsStateWithLifecycle()
    if (vm.channel == null) {
        ChatInbox(
            channels = allChannels,
            latest = latest,
            onOpen = vm::open,
            onNew = {
                newChatPicks = emptySet()
                newChat = true
            },
        )
    } else ModalNavigationDrawer(
        drawerState = drawerState,
        // Open via menu button only so left-edge swipe can go Home; swipe still closes when open.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                ChatSidebar(
                    channels = channels,
                    directs = directs,
                    groups = groups,
                    others = otherChannels,
                    selectedId = vm.channel?.id,
                    onClose = { scope.launch { drawerState.close() } },
                    onSelect = ::openChannel,
                    onNewChat = {
                        newChatPicks = emptySet()
                        newChat = true
                    },
                )
            }
        },
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val threadTitle = stringResource(R.string.chat_thread)
                        val chatTitle = stringResource(R.string.tab_chat)
                        Text(
                            if (vm.thread != null) threadTitle else (vm.channel?.name ?: chatTitle),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (vm.thread != null) {
                                vm.thread?.body.orEmpty()
                            } else if (vm.live) {
                                stringResource(R.string.chat_live)
                            } else {
                                stringResource(R.string.chat_reconnecting)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = { vm.closeConversation() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { graph.chatChromeVisible.value = !chromeVisible },
                    ) {
                        Icon(
                            if (chromeVisible) Icons.Filled.ViewDay else Icons.Filled.ViewAgenda,
                            contentDescription = stringResource(
                                if (chromeVisible) R.string.chat_hide_chrome else R.string.chat_show_chrome,
                            ),
                        )
                    }
                    TextButton(onClick = { searching = true }) { Text(stringResource(R.string.search)) }
                    if (vm.thread != null) {
                        TextButton(onClick = vm::closeThread) { Text(stringResource(R.string.channel)) }
                    }
                    IconButton(onClick = {
                        newChatPicks = emptySet()
                        newChat = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_chat))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            vm.banner?.let {
                Text(
                    stringResource(if (it == "note") R.string.note_created else R.string.task_created),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (vm.loading && chatMessages.isEmpty()) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            }
            val markRevision by graph.marks.changes.collectAsStateWithLifecycle()
            val maxShift = with(LocalDensity.current) { 96.dp.toPx() }
            var timeDragging by remember { mutableStateOf(false) }
            var timeShift by remember { mutableFloatStateOf(0f) }
            val timeReveal by animateFloatAsState(
                targetValue = if (timeDragging) timeShift else 0f,
                animationSpec = if (timeDragging) snap() else tween(180),
                label = "timeReveal",
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(maxShift) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Initial)
                            var dragged = 0f
                            var horizontal = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                dragged += delta.x
                                if (!horizontal && abs(dragged) > slop && abs(delta.x) > abs(delta.y)) {
                                    horizontal = true
                                    timeDragging = true
                                }
                                if (horizontal) {
                                    change.consume()
                                    timeShift = (timeShift + delta.x).coerceIn(-maxShift, maxShift)
                                }
                            }
                            timeDragging = false
                            timeShift = 0f
                        }
                    },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(chatMessages, key = { _, it -> it.id }) { index, message ->
                    val previous = chatMessages.getOrNull(index - 1)
                    MessageBubble(
                        message = message,
                        mine = message.senderId == myId,
                        myId = myId,
                        showName = message.senderId != myId && previous?.senderId != message.senderId,
                        sending = message.id in sendingIds,
                        onFailedTap = if (message.id in failedIds) ({ failedMessage = message }) else null,
                        onThread = if (vm.thread == null) ({ vm.openThread(message) }) else null,
                        onMakeTask = if (message.body.isNotBlank() && message.taskId == null && message.noteId == null) {
                            { vm.makeTask(message) }
                        } else {
                            null
                        },
                        onMakeNote = if (message.body.isNotBlank() && message.taskId == null && message.noteId == null) {
                            { vm.makeNote(message) }
                        } else {
                            null
                        },
                        timeReveal = timeReveal,
                        timeMax = maxShift,
                        reactions = message.reactions,
                        pinned = markRevision >= 0 && graph.marks.pinned(message.id),
                        starred = graph.marks.starred(message.id),
                        channels = allChannels.filter { it.id != message.channelId },
                        onReact = { emoji -> myId?.let { vm.react(message, emoji, it) } },
                        onPin = { graph.marks.togglePin(message.id) },
                        onStar = { graph.marks.toggleStar(message.id) },
                        onForward = { vm.forward(message, it) },
                        task = message.taskId?.let(taskById::get),
                        note = message.noteId?.let(noteById::get),
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            failedMessage?.let { message ->
                ModalBottomSheet(
                    onDismissRequest = { failedMessage = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.chat_failed),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        sendError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        SoftPrimaryButton(
                            text = stringResource(R.string.chat_retry),
                            onClick = { vm.retry(message); failedMessage = null },
                        )
                        SoftPrimaryButton(
                            text = stringResource(R.string.chat_copy_text),
                            onClick = {
                                copyTextToClipboard(context, message.body)
                                failedMessage = null
                            },
                        )
                        TextButton(
                            onClick = { vm.discard(message); failedMessage = null },
                            modifier = Modifier.fillMaxWidth().height(TouchTarget),
                        ) {
                            Text(stringResource(R.string.chat_delete_message), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (mentionQuery != null) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    vm.members.filter { mentionQuery.isEmpty() || it.name.contains(mentionQuery, true) }.take(8).forEach { member ->
                        TextButton(onClick = {
                            val cut = draft.lastIndexOf('@')
                            draft = draft.substring(0, cut) + "@${member.name} "
                        }) { Text("@${member.name}") }
                    }
                }
            }
            AttachedLocationBar(
                locationUrl = attachedLocation,
                onClear = { attachedLocation = null },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = { shareMenu = true }, enabled = vm.channel != null) {
                        Icon(Icons.Filled.Place, contentDescription = stringResource(R.string.attach_location))
                    }
                    DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_location_chat)) },
                            onClick = {
                                shareMenu = false
                                onOpenMaps()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_maps)) },
                            onClick = {
                                shareMenu = false
                                onOpenMaps()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_via_zalo)) },
                            onClick = {
                                shareMenu = false
                                val url = currentMapsUrl(context)
                                if (url != null) {
                                    shareLocationToChatApp(context, AppPackages.ZALO, url, "https://zalo.me/")
                                } else {
                                    onOpenMaps()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_via_messenger)) },
                            onClick = {
                                shareMenu = false
                                val url = currentMapsUrl(context)
                                if (url != null) {
                                    shareLocationToChatApp(context, AppPackages.MESSENGER, url, "https://www.messenger.com/")
                                } else {
                                    onOpenMaps()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_via_wechat)) },
                            onClick = {
                                shareMenu = false
                                val url = currentMapsUrl(context)
                                if (url != null) {
                                    shareLocationToChatApp(context, AppPackages.WECHAT, url, "https://www.wechat.com/")
                                } else {
                                    onOpenMaps()
                                }
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (vm.thread == null) {
                                stringResource(R.string.chat_hint)
                            } else {
                                stringResource(R.string.chat_reply_hint)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    trailingIcon = if (draft.isNotEmpty()) {
                        { TextFieldClearCopyActions(value = draft, onClear = { draft = "" }) }
                    } else {
                        null
                    },
                )
                IconButton(
                    onClick = {
                        val text = draft
                        val location = attachedLocation
                        draft = ""
                        attachedLocation = null
                        vm.send(text, location)
                    },
                    enabled = (draft.isNotBlank() || !attachedLocation.isNullOrBlank()) && vm.channel != null,
                ) {
                    Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.send))
                }
            }
        }
    }
    } // ModalNavigationDrawer

    if (searching) {
        EditorDialog(
            title = stringResource(R.string.search_workspace),
            onDismiss = { searching = false },
            onSave = { vm.search(query) { hits = it } },
            onDelete = null,
        ) {
            OutlinedField(query, { query = it }, stringResource(R.string.keyword))
            hits.forEach { hit ->
                TextButton(onClick = {
                    searching = false
                    if (hit.kind == "message" && hit.channelId != null) {
                        allChannels.firstOrNull { it.id == hit.channelId }?.let(vm::open)
                    }
                }) { Text("${hit.title}: ${hit.snippet}") }
            }
        }
    }

    if (newChat) {
        NewChatDialog(
            members = vm.members.filter { it.id != myId },
            picked = newChatPicks,
            onToggle = { id ->
                newChatPicks = if (id in newChatPicks) newChatPicks - id else newChatPicks + id
            },
            onOpen = { id ->
                newChat = false
                newChatPicks = emptySet()
                vm.openDirect(id)
                scope.launch { drawerState.close() }
            },
            onGroup = {
                val ids = newChatPicks.toList()
                newChat = false
                newChatPicks = emptySet()
                vm.openGroup("", ids)
                scope.launch { drawerState.close() }
            },
            onFind = { name, report ->
                vm.openByUsername(name, onReady = {
                    newChat = false
                    newChatPicks = emptySet()
                    scope.launch { drawerState.close() }
                }, onError = report)
            },
            onDismiss = { newChat = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInbox(
    channels: List<ChannelDto>,
    latest: Map<String, MessageDto>,
    onOpen: (ChannelDto) -> Unit,
    onNew: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val shown = channels
        .filter { filter.isBlank() || it.name.contains(filter, ignoreCase = true) }
        .sortedByDescending { latest[it.id]?.createdAt ?: 0L }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_chat)) },
                actions = {
                    IconButton(onClick = onNew) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_chat))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search)) },
            )
            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.chat_inbox_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { channel ->
                    InboxRow(channel, latest[channel.id], onClick = { onOpen(channel) })
                }
            }
        }
    }
}

@Composable
private fun InboxRow(channel: ChannelDto, last: MessageDto?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                channel.name.trim().take(1).uppercase().ifBlank { "#" },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                last?.body?.replace('\n', ' ')?.ifBlank { null } ?: stringResource(R.string.chat_inbox_empty_line),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (last != null) {
            Text(
                formatTime(last.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NewChatDialog(
    members: List<MemberDto>,
    picked: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onGroup: () -> Unit,
    onFind: (String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var findError by remember { mutableStateOf<String?>(null) }
    val shown = members.filter {
        query.isBlank() || it.name.contains(query, true) || it.username.contains(query, true)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.new_chat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.new_chat_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        findError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.new_chat_username)) },
                )
                if (query.isNotBlank()) {
                    TextButton(onClick = { onFind(query.trim()) { findError = it } }) {
                        Text(stringResource(R.string.new_chat_find))
                    }
                }
                findError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.weight(1f).clickable { onOpen(member.id) }.padding(vertical = 8.dp),
                            ) {
                                Text(member.name, style = MaterialTheme.typography.bodyLarge)
                                if (member.username.isNotBlank()) {
                                    Text(
                                        "@${member.username}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            androidx.compose.material3.Checkbox(
                                checked = member.id in picked,
                                onCheckedChange = { onToggle(member.id) },
                            )
                        }
                    }
                }
                if (picked.size >= 2) {
                    SoftPrimaryButton(text = stringResource(R.string.new_group), onClick = onGroup)
                }
            }
        }
    }
}

@Composable
private fun ChatSidebar(
    channels: List<ChannelDto>,
    directs: List<ChannelDto>,
    groups: List<ChannelDto>,
    others: List<ChannelDto>,
    selectedId: String?,
    onClose: () -> Unit,
    onSelect: (ChannelDto) -> Unit,
    onNewChat: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxHeight()
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.tab_chat),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_close_sidebar))
            }
        }
        TextButton(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.new_chat), modifier = Modifier.padding(start = 6.dp))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            if (channels.isNotEmpty() || others.isNotEmpty()) {
                item {
                    SidebarSectionTitle(stringResource(R.string.chat_channels))
                }
                items(channels + others, key = { it.id }) { channel ->
                    SidebarChannelRow(channel, selected = channel.id == selectedId, onClick = { onSelect(channel) })
                }
            }
            if (directs.isNotEmpty()) {
                item { SidebarSectionTitle(stringResource(R.string.chat_directs)) }
                items(directs, key = { "d-${it.id}" }) { channel ->
                    SidebarChannelRow(channel, selected = channel.id == selectedId, onClick = { onSelect(channel) })
                }
            }
            if (groups.isNotEmpty()) {
                item { SidebarSectionTitle(stringResource(R.string.chat_groups)) }
                items(groups, key = { "g-${it.id}" }) { channel ->
                    SidebarChannelRow(channel, selected = channel.id == selectedId, onClick = { onSelect(channel) })
                }
            }
            if (channels.isEmpty() && directs.isEmpty() && groups.isEmpty() && others.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.chat_hint),
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFF6B7280),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun SidebarChannelRow(
    channel: ChannelDto,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFEEF2FF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFF4F46E5) else Color(0xFFE5E7EB)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                channel.name.trim().take(1).uppercase().ifBlank { "#" },
                color = if (selected) Color.White else Color(0xFF374151),
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            Text(
                when {
                    channel.kind.equals("DIRECT", true) || channel.kind.equals("DM", true) -> stringResource(R.string.direct_message)
                    channel.kind.equals("GROUP", true) -> stringResource(R.string.new_group)
                    else -> stringResource(R.string.chat_channels)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SendingClock() {
    Icon(
        Icons.Outlined.Schedule,
        contentDescription = stringResource(R.string.chat_sending),
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageDto,
    mine: Boolean,
    myId: String?,
    showName: Boolean,
    sending: Boolean,
    onFailedTap: (() -> Unit)?,
    onThread: (() -> Unit)?,
    onMakeTask: (() -> Unit)?,
    onMakeNote: (() -> Unit)?,
    timeReveal: Float,
    timeMax: Float,
    reactions: List<com.workhub.shared.ReactionDto>,
    pinned: Boolean,
    starred: Boolean,
    channels: List<ChannelDto>,
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onStar: () -> Unit,
    onForward: (String) -> Unit,
    task: TaskDto? = null,
    note: NoteDto? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val failed = onFailedTap != null
    var menu by remember { mutableStateOf(false) }
    var emojiBar by remember { mutableStateOf(false) }
    var forwarding by remember { mutableStateOf(false) }
    val mapsUrl = message.locationUrl?.takeIf { it.isNotBlank() }
        ?: Regex("""https?://(?:www\.)?(?:maps\.google\.com|google\.com/maps)[^\s]*""")
            .find(message.body)?.value
    val sharedTask = message.taskId != null
    val sharedNote = message.noteId != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onFailedTap?.invoke() },
                onLongClick = { menu = true },
            ),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                formatTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(if (timeReveal >= 0f) Alignment.CenterStart else Alignment.CenterEnd)
                    .graphicsLayer { alpha = if (timeMax <= 0f) 0f else (abs(timeReveal) / timeMax).coerceIn(0f, 1f) },
            )
            Column(
                modifier = Modifier
                    .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                    .graphicsLayer { translationX = timeReveal },
                horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            ) {
                if (showName) {
                    Text(
                        message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (sharedTask || sharedNote) {
                        EntityRailCard(
                            task = sharedTask,
                            title = task?.title ?: note?.title ?: message.body,
                            subtitle = task?.let { statusLabel(it.status) } ?: note?.body?.takeIf { it.isNotBlank() },
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                    } else if (message.body.isNotBlank()) {
                        val bubbleColor = when {
                            failed -> MaterialTheme.colorScheme.errorContainer
                            mine -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val textColor = when {
                            failed -> MaterialTheme.colorScheme.onErrorContainer
                            mine -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Box(
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .then(
                                    if (failed) {
                                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(18.dp))
                                    } else if (!mine) {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .background(color = bubbleColor, shape = RoundedCornerShape(18.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(message.body, color = textColor)
                        }
                    }
                    if (!mine) {
                        ReactButton(onClick = { emojiBar = true })
                    }
                    if (sending) SendingClock()
                    if (failed) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = stringResource(R.string.chat_failed),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (mapsUrl != null) {
                    Text(
                        stringResource(R.string.location_attached_label),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))) },
                    )
                }
                val reactionMarks = reactions.groupBy { it.emoji }
                if (reactionMarks.isNotEmpty() || pinned || starred) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        reactionMarks.forEach { (emoji, people) ->
                            Text(
                                if (people.size > 1) "$emoji ${people.size}" else emoji,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (pinned) {
                            Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.chat_pin), modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (starred) {
                            Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.chat_star), modifier = Modifier.size(12.dp), tint = Color(0xFFF59E0B))
                        }
                    }
                }
                if (message.replyCount > 0 && onThread != null) {
                    Text(
                        stringResource(R.string.thread_count, message.replyCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable(onClick = onThread),
                    )
                }
            }
        }
        if (emojiBar || menu) {
            MessageActions(
                full = menu,
                forwarding = forwarding && menu,
                channels = channels,
                selected = reactions.firstOrNull { it.userId == myId }?.emoji,
                onDismiss = { menu = false; emojiBar = false; forwarding = false },
                onReact = { emoji ->
                    onReact(emoji)
                    menu = false
                    emojiBar = false
                },
                onReply = onThread?.let { action -> { menu = false; action() } },
                onForward = { forwarding = true },
                onForwardTo = { id ->
                    menu = false
                    forwarding = false
                    onForward(id)
                },
                onShare = {
                    menu = false
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message.body)
                    }
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.chat_share)))
                },
                onCopy = {
                    menu = false
                    copyTextToClipboard(context, message.body)
                },
                onTask = onMakeTask?.let { action -> { menu = false; action() } },
                onNote = onMakeNote?.let { action -> { menu = false; action() } },
                onPin = { menu = false; onPin() },
                onStar = { menu = false; onStar() },
                pinned = pinned,
                starred = starred,
            )
        }
    }
}

private val ChatEmojis = listOf("❤️", "👍", "😂", "😮", "😢", "😠")

@Composable
private fun ReactButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.AddReaction,
            contentDescription = stringResource(R.string.chat_react),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageActions(
    full: Boolean,
    forwarding: Boolean,
    channels: List<ChannelDto>,
    selected: String?,
    pinned: Boolean,
    starred: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: (() -> Unit)?,
    onForward: () -> Unit,
    onForwardTo: (String) -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onTask: (() -> Unit)?,
    onNote: (() -> Unit)?,
    onPin: () -> Unit,
    onStar: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatEmojis.forEach { emoji ->
                        Text(
                            emoji,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (emoji == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { onReact(emoji) }
                                .padding(6.dp),
                        )
                    }
                }
            }
            if (full) Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
            ) {
                if (forwarding) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.chat_forward_to), style = MaterialTheme.typography.titleMedium)
                        if (channels.isEmpty()) {
                            Text(stringResource(R.string.chat_forward), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        channels.take(8).forEach { channel ->
                            Text(
                                channel.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onForwardTo(channel.id) }
                                    .padding(vertical = 10.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                } else {
                    val actions = listOfNotNull(
                        onReply?.let { BubbleAction(stringResource(R.string.chat_reply), Icons.Outlined.Reply, it) },
                        BubbleAction(stringResource(R.string.chat_forward), Icons.Outlined.Shortcut, onForward),
                        BubbleAction(stringResource(R.string.chat_share), Icons.Filled.Share, onShare),
                        BubbleAction(stringResource(R.string.chat_copy_text), Icons.Filled.ContentCopy, onCopy),
                        onTask?.let { BubbleAction(stringResource(R.string.make_task), Icons.Outlined.TaskAlt, it) },
                        onNote?.let { BubbleAction(stringResource(R.string.quick_create_note), Icons.Outlined.EditNote, it) },
                        BubbleAction(stringResource(R.string.chat_pin), Icons.Filled.PushPin, onPin, pinned),
                        BubbleAction(stringResource(R.string.chat_star), if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder, onStar, starred),
                    )
                    Column(Modifier.padding(8.dp)) {
                        actions.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { action ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(onClick = action.onClick)
                                            .padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            action.icon,
                                            contentDescription = action.label,
                                            tint = if (action.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            action.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = 2,
                                        )
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class BubbleAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val active: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val api: WorkHubApi,
    private val realtime: com.workhub.app.data.RealtimeHub,
    private val chat: ChatStore,
    private val offline: com.workhub.app.data.OfflineStore,
) : ViewModel() {
    /** Eager: the view model itself looks up channels by id. */
    val channels: StateFlow<List<ChannelDto>> =
        chat.channels.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var channel by mutableStateOf<ChannelDto?>(null)
        private set
    var thread by mutableStateOf<MessageDto?>(null)
        private set
    private val selection = MutableStateFlow<Pair<String, String?>?>(null)
    val messages: StateFlow<List<MessageDto>> = selection
        .flatMapLatest { picked -> if (picked == null) flowOf(emptyList()) else chat.messages(picked.first, picked.second) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingIds: StateFlow<Set<String>> =
        chat.pendingIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val failedIds: StateFlow<Set<String>> =
        chat.failedIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val latest: StateFlow<Map<String, MessageDto>> =
        chat.latest.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    var members by mutableStateOf<List<MemberDto>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var live by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var banner by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
        viewModelScope.launch {
            realtime.connected.collect { connected ->
                // Pull what was missed while the socket was down.
                if (connected && !live) refreshQuiet()
                live = connected
            }
        }
    }

    private fun select(next: ChannelDto?, root: MessageDto?) {
        channel = next
        thread = root
        selection.value = next?.let { it.id to root?.id }
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val list = chat.refreshChannels()
                members = api.members()
                val selected = channel?.let { current -> list.firstOrNull { it.id == current.id } }
                select(selected, thread?.takeIf { selected != null && selected.id == channel?.id })
                selected?.let { chat.refreshMessages(it.id, thread?.id) }
            } catch (e: Exception) {
                // Offline with a cached history is normal: keep showing it.
                if (channels.value.isEmpty()) error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    /** Resume without full-screen loading spinner. */
    fun refreshQuiet() {
        val selected = channel
        val root = thread?.id
        viewModelScope.launch {
            runCatching { chat.refreshChannels() }
            if (selected != null) runCatching { chat.refreshMessages(selected.id, root) }
        }
    }

    fun closeConversation() {
        select(null, null)
    }

    fun open(next: ChannelDto) {
        select(next, null)
        refreshQuiet()
    }

    fun openById(channelId: String) {
        viewModelScope.launch {
            val match = channels.value.firstOrNull { it.id == channelId }
                ?: runCatching { chat.refreshChannels() }.getOrNull()?.firstOrNull { it.id == channelId }
                ?: return@launch
            open(match)
        }
    }

    fun openThread(message: MessageDto) {
        select(channel ?: return, message)
        refreshQuiet()
    }

    fun closeThread() {
        select(channel ?: return, null)
        refreshQuiet()
    }

    fun createChannel(name: String, isPrivate: Boolean) {
        viewModelScope.launch {
            try {
                val created = api.createChannel(name, isPrivate)
                chat.refreshChannels()
                open(created)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun addMember(channelId: String, userId: String) {
        viewModelScope.launch {
            try {
                api.addChannelMember(channelId, userId)
                val list = chat.refreshChannels()
                list.firstOrNull { it.id == channelId }?.let { select(it, thread) }
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun openByUsername(raw: String, onReady: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val user = api.lookupUser(raw)
                val created = api.openDirect(user.id)
                chat.refreshChannels()
                open(created)
                onReady()
            } catch (e: Exception) {
                onError(e.userMessage())
            }
        }
    }

    fun openDirect(userId: String) {
        viewModelScope.launch {
            try {
                val created = api.openDirect(userId)
                chat.refreshChannels()
                open(created)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun openGroup(name: String, memberIds: List<String>) {
        viewModelScope.launch {
            try {
                val created = api.openGroup(name, memberIds)
                chat.refreshChannels()
                open(created)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun makeTask(message: MessageDto) {
        viewModelScope.launch {
            banner = null
            error = null
            try {
                val title = messageTitle(message.body)
                offline.saveTask(null, com.workhub.shared.TaskWrite(title = title, description = message.body))
                banner = "task"
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun makeNote(message: MessageDto) {
        viewModelScope.launch {
            banner = null
            error = null
            try {
                val title = messageTitle(message.body)
                offline.saveNote(null, com.workhub.shared.NoteWrite(title = title, body = message.body))
                banner = "note"
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun search(query: String, onResult: (List<SearchHit>) -> Unit) {
        viewModelScope.launch {
            try {
                onResult(api.search(query).hits)
                error = null
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun react(message: MessageDto, emoji: String, userId: String) {
        val current = message.reactions.firstOrNull { it.userId == userId }?.emoji
        val next = if (current == emoji) "" else emoji
        viewModelScope.launch {
            chat.applyReaction(
                com.workhub.shared.ReactionEvent(message.id, message.channelId, userId, next),
            )
            runCatching { api.react(message.channelId, message.id, next) }
        }
    }

    fun forward(message: MessageDto, channelId: String) {
        viewModelScope.launch {
            runCatching { chat.send(channelId, message.body, null, message.locationUrl) }
                .onFailure { error = it.userMessage() }
        }
    }

    fun send(body: String, locationUrl: String? = null) {
        val selected = channel ?: return
        if (body.isBlank() && locationUrl.isNullOrBlank()) return
        val root = thread?.id
        viewModelScope.launch {
            try {
                chat.send(selected.id, body, root, locationUrl)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun retry(message: MessageDto) {
        viewModelScope.launch { chat.retry(message.id) }
    }

    fun discard(message: MessageDto) {
        viewModelScope.launch { chat.discard(message.id) }
    }
}

private fun messageTitle(body: String): String =
    body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80)?.ifBlank { null }
        ?: body.trim().take(80).ifBlank { "…" }

private fun chatFactory(graph: com.workhub.app.data.AppGraph) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(graph.api, graph.realtime, graph.chat, graph.offline) as T
    }
}
