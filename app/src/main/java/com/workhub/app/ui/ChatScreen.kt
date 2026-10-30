package com.workhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.WorkHubApi
import com.workhub.app.data.userMessage
import com.workhub.shared.ChannelDto
import com.workhub.shared.MemberDto
import com.workhub.shared.MessageDto
import com.workhub.shared.SearchHit
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onOpenMaps: () -> Unit = {}) {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val chromeVisible by graph.chatChromeVisible.collectAsState()
    val myId = session?.user?.id
    val vm: ChatViewModel = viewModel(factory = chatFactory(graph))
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var draft by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        graph.chatChromeVisible.value = false
        graph.attachLink.collect { link ->
            draft = if (draft.isBlank()) link else "$draft\n$link"
        }
    }
    LaunchedEffect(Unit) {
        graph.pendingChatChannelId.collect { channelId ->
            if (channelId == null) return@collect
            graph.pendingChatChannelId.value = null
            val match = vm.channels.firstOrNull { it.id == channelId }
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
    var creating by remember { mutableStateOf(false) }
    var channelName by remember { mutableStateOf("") }
    var channelPrivate by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var addingMember by remember { mutableStateOf(false) }
    var memberId by remember { mutableStateOf("") }
    var directOpen by remember { mutableStateOf(false) }
    var directUserId by remember { mutableStateOf("") }
    var groupOpen by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var groupMembers by remember { mutableStateOf(setOf<String>()) }
    var groupError by remember { mutableStateOf<String?>(null) }
    val groupNeedTwo = stringResource(R.string.group_need_two)
    val mentionQuery = draft.substringAfterLast(' ').let { token ->
        if (token.startsWith("@")) token.drop(1) else null
    }
    val channels = vm.channels.filter { it.kind.equals("CHANNEL", ignoreCase = true) || it.kind.isBlank() }
    val directs = vm.channels.filter { it.kind.equals("DIRECT", ignoreCase = true) || it.kind.equals("DM", ignoreCase = true) }
    val groups = vm.channels.filter { it.kind.equals("GROUP", ignoreCase = true) }
    val otherChannels = vm.channels.filter { ch ->
        channels.none { it.id == ch.id } && directs.none { it.id == ch.id } && groups.none { it.id == ch.id }
    }

    DisposableEffect(vm.channel?.id) {
        AppVisibility.channelId = vm.channel?.id
        onDispose { AppVisibility.channelId = null }
    }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    fun openChannel(channel: ChannelDto) {
        vm.open(channel)
        scope.launch { drawerState.close() }
    }

    // Close drawer first; leave edge-swipe free for iOS-style back-to-home.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Scrim tap + swipe dismiss (was false → sidebar opened but could not close).
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                ChatSidebar(
                    channels = channels,
                    directs = directs,
                    groups = groups,
                    others = otherChannels,
                    selectedId = vm.channel?.id,
                    canCreateChannel = canManageContent(session?.workspace?.role),
                    onClose = { scope.launch { drawerState.close() } },
                    onSelect = ::openChannel,
                    onDirect = {
                        scope.launch { drawerState.close() }
                        directUserId = ""
                        directOpen = true
                    },
                    onGroup = {
                        scope.launch { drawerState.close() }
                        groupName = ""
                        groupMembers = emptySet()
                        groupError = null
                        groupOpen = true
                    },
                    onCreateChannel = {
                        scope.launch { drawerState.close() }
                        creating = true
                    },
                    onAddMember = {
                        scope.launch { drawerState.close() }
                        addingMember = true
                    },
                    showAddMember = vm.channel?.isPrivate == true,
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
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (drawerState.isOpen) drawerState.close() else drawerState.open()
                            }
                        },
                    ) {
                        Icon(
                            if (drawerState.isOpen) Icons.Filled.Close else Icons.Filled.Menu,
                            contentDescription = stringResource(
                                if (drawerState.isOpen) R.string.chat_close_sidebar else R.string.chat_open_sidebar,
                            ),
                        )
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
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.channel))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.direct_message)) },
                            onClick = {
                                menu = false
                                directUserId = ""
                                directOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_group)) },
                            onClick = {
                                menu = false
                                groupName = ""
                                groupMembers = emptySet()
                                groupError = null
                                groupOpen = true
                            },
                        )
                        if (vm.channel?.isPrivate == true) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_person)) },
                                onClick = {
                                    menu = false
                                    addingMember = true
                                },
                            )
                        }
                        if (canManageContent(session?.workspace?.role)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.create_channel)) },
                                onClick = {
                                    menu = false
                                    creating = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            vm.banner?.let {
                Text(
                    stringResource(R.string.task_created),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (vm.loading && vm.messages.isEmpty()) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        mine = message.senderId == myId,
                        onThread = if (vm.thread == null) ({ vm.openThread(message) }) else null,
                        onMakeTask = { vm.makeTask(message) },
                    )
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
                        Text(if (vm.thread == null) stringResource(R.string.chat_hint) else stringResource(R.string.chat_reply_hint))
                    },
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
                        draft = ""
                        vm.send(text)
                    },
                    enabled = draft.isNotBlank() && vm.channel != null,
                ) {
                    Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.send))
                }
            }
        }
    }
    } // ModalNavigationDrawer

    if (creating) {
        EditorDialog(
            title = stringResource(R.string.new_channel),
            onDismiss = { creating = false },
            onSave = {
                vm.createChannel(channelName, channelPrivate)
                channelName = ""
                channelPrivate = false
                creating = false
            },
            onDelete = null,
        ) {
            OutlinedField(channelName, { channelName = it }, stringResource(R.string.channel_name))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.private_channel))
                Switch(checked = channelPrivate, onCheckedChange = { channelPrivate = it })
            }
        }
    }

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
                        vm.channels.firstOrNull { it.id == hit.channelId }?.let(vm::open)
                    }
                }) { Text("${hit.title}: ${hit.snippet}") }
            }
        }
    }

    if (directOpen) {
        EditorDialog(
            title = stringResource(R.string.direct_message),
            onDismiss = { directOpen = false },
            onSave = {
                if (directUserId.isNotBlank() && directUserId != myId) {
                    vm.openDirect(directUserId)
                    directUserId = ""
                    directOpen = false
                }
            },
            onDelete = null,
        ) {
            MemberPicker(
                label = stringResource(R.string.members),
                members = vm.members.filter { it.id != myId },
                selectedIds = if (directUserId.isBlank()) emptySet() else setOf(directUserId),
                multiple = false,
                onToggle = { directUserId = it },
            )
        }
    }

    if (groupOpen) {
        EditorDialog(
            title = stringResource(R.string.new_group),
            onDismiss = { groupOpen = false },
            onSave = {
                val others = groupMembers.filter { it != myId }
                if (others.size < 2) {
                    groupError = groupNeedTwo
                } else {
                    vm.openGroup(groupName, others.toList())
                    groupName = ""
                    groupMembers = emptySet()
                    groupOpen = false
                }
            },
            onDelete = null,
            error = groupError,
        ) {
            Text(stringResource(R.string.group_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedField(groupName, { groupName = it }, stringResource(R.string.group_name))
            MemberPicker(
                label = stringResource(R.string.members),
                members = vm.members.filter { it.id != myId },
                selectedIds = groupMembers,
                onToggle = { id ->
                    groupMembers = if (id in groupMembers) groupMembers - id else groupMembers + id
                    groupError = null
                },
            )
        }
    }

    if (addingMember) {
        EditorDialog(
            title = stringResource(R.string.add_to_channel),
            onDismiss = { addingMember = false },
            onSave = {
                vm.channel?.let { vm.addMember(it.id, memberId) }
                addingMember = false
            },
            onDelete = null,
        ) {
            MemberPicker(
                label = stringResource(R.string.members),
                members = vm.members,
                selectedIds = if (memberId.isBlank()) emptySet() else setOf(memberId),
                multiple = false,
                onToggle = { memberId = it },
            )
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
    canCreateChannel: Boolean,
    showAddMember: Boolean,
    onClose: () -> Unit,
    onSelect: (ChannelDto) -> Unit,
    onDirect: () -> Unit,
    onGroup: () -> Unit,
    onCreateChannel: () -> Unit,
    onAddMember: () -> Unit,
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
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onDirect) { Text(stringResource(R.string.direct_message)) }
            TextButton(onClick = onGroup) { Text(stringResource(R.string.new_group)) }
            if (canCreateChannel) {
                TextButton(onClick = onCreateChannel) { Text(stringResource(R.string.create_channel)) }
            }
            if (showAddMember) {
                TextButton(onClick = onAddMember) { Text(stringResource(R.string.add_person)) }
            }
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
                channel.kind.lowercase().replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9CA3AF),
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageDto,
    mine: Boolean,
    onThread: (() -> Unit)?,
    onMakeTask: () -> Unit,
) {
    val context = LocalContext.current
    val mapsUrl = Regex("""https?://(?:www\.)?(?:maps\.google\.com|google\.com/maps)[^\s]*""")
        .find(message.body)?.value
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (!mine) {
            Text(message.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(
                    if (mapsUrl != null) {
                        Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)))
                        }
                    } else {
                        Modifier
                    },
                )
                .background(
                    color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                message.body,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(formatTime(message.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Row {
            if (onThread != null) {
                TextButton(onClick = onThread) {
                    Text(
                        if (message.replyCount > 0) {
                            stringResource(R.string.thread_count, message.replyCount)
                        } else {
                            stringResource(R.string.chat_thread)
                        },
                    )
                }
            }
            TextButton(onClick = onMakeTask) { Text(stringResource(R.string.make_task)) }
        }
    }
}

class ChatViewModel(
    private val api: WorkHubApi,
    private val realtime: com.workhub.app.data.RealtimeHub,
) : ViewModel() {
    var channels by mutableStateOf<List<ChannelDto>>(emptyList())
        private set
    var channel by mutableStateOf<ChannelDto?>(null)
        private set
    var messages by mutableStateOf<List<MessageDto>>(emptyList())
        private set
    var thread by mutableStateOf<MessageDto?>(null)
        private set
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
            realtime.connected.collect { live = it }
        }
        viewModelScope.launch {
            realtime.events.collect { event ->
                val incoming = event.message
                if (event.type == "message" && incoming != null && incoming.channelId == channel?.id) {
                    add(incoming)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                channels = api.channels()
                members = api.members()
                val selected = channel?.let { current -> channels.firstOrNull { it.id == current.id } } ?: channels.firstOrNull()
                channel = selected
                messages = if (selected == null) emptyList() else api.messages(selected.id, thread?.id)
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    /** Resume without full-screen loading spinner. */
    fun refreshQuiet() {
        viewModelScope.launch {
            try {
                val selected = channel ?: return@launch
                messages = api.messages(selected.id, thread?.id)
                error = null
            } catch (_: Exception) {
                // Keep previous messages; realtime will reconnect separately.
            }
        }
    }

    fun open(next: ChannelDto) {
        channel = next
        thread = null
        viewModelScope.launch {
            try {
                messages = api.messages(next.id)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun openById(channelId: String) {
        viewModelScope.launch {
            try {
                if (channels.none { it.id == channelId }) {
                    channels = api.channels()
                }
                val match = channels.firstOrNull { it.id == channelId } ?: return@launch
                open(match)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun openThread(message: MessageDto) {
        val selected = channel ?: return
        thread = message
        viewModelScope.launch {
            try {
                messages = api.messages(selected.id, message.id)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun closeThread() {
        val selected = channel ?: return
        thread = null
        viewModelScope.launch {
            try {
                messages = api.messages(selected.id)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun createChannel(name: String, isPrivate: Boolean) {
        viewModelScope.launch {
            try {
                val created = api.createChannel(name, isPrivate)
                channels = api.channels()
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
                channels = api.channels()
                channel = channels.firstOrNull { it.id == channelId } ?: channel
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    fun openDirect(userId: String) {
        viewModelScope.launch {
            try {
                val created = api.openDirect(userId)
                channels = api.channels()
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
                channels = api.channels()
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
                api.messageToTask(message.channelId, message.id)
                banner = "created"
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

    fun send(body: String) {
        val selected = channel ?: return
        if (body.isBlank()) return
        viewModelScope.launch {
            try {
                val saved = api.sendMessage(selected.id, body.trim(), thread?.id)
                add(saved)
            } catch (e: Exception) {
                error = e.userMessage()
            }
        }
    }

    private fun add(message: MessageDto) {
        viewModelScope.launch {
            val viewing = thread?.id
            if (message.parentId == viewing) {
                if (messages.none { it.id == message.id }) messages = messages + message
            } else if (viewing == null && message.parentId != null) {
                messages = messages.map { item ->
                    if (item.id == message.parentId) item.copy(replyCount = item.replyCount + 1) else item
                }
            }
        }
    }
}

private fun chatFactory(graph: com.workhub.app.data.AppGraph) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(graph.api, graph.realtime) as T
    }
}
