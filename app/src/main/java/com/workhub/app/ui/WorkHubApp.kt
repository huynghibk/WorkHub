package com.workhub.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import kotlinx.coroutines.launch

private val Ink = Color(0xFF111827)
private val Indigo = Color(0xFF4F46E5)
private val IndigoSoft = Color(0xFFEEF2FF)
private val Cyan = Color(0xFF06B6D4)
private val Muted = Color(0xFF9CA3AF)
private val Line = Color(0xFFE5E7EB)

@Composable
fun WorkHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Indigo,
            onPrimary = Color.White,
            primaryContainer = IndigoSoft,
            onPrimaryContainer = Color(0xFF312E81),
            secondary = Cyan,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFECFEFF),
            tertiary = Color(0xFF10B981),
            tertiaryContainer = Color(0xFFECFDF5),
            background = Color(0xFFF9FAFB),
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFF3F4F6),
            outline = Line,
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

private val BarAvatar = 48.dp
private val BarAction = 48.dp

@Composable
internal fun WorkspaceBar(
    onOpenSettings: () -> Unit = {},
    onInvite: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val workspace = session?.workspace
    val user = session?.user
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val name = workspace?.name?.ifBlank { null } ?: stringResource(R.string.workspace)
    Surface(color = Color.Transparent, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, Line, RoundedCornerShape(16.dp))
                    .clickable { scope.launch { graph.session.clearWorkspace() } }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(BarAvatar)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Indigo),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.switch_workspace),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        roleLabel(workspace?.role.orEmpty()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(BarAction)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Line, CircleShape)
                    .clickable(onClick = onInvite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = stringResource(R.string.add_workspace_member),
                    tint = Indigo,
                    modifier = Modifier.size(24.dp),
                )
            }
            HideKeyboardButton()
            Box {
                Box(
                    modifier = Modifier
                        .size(BarAction)
                        .clip(CircleShape)
                        .clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    ProfileAvatar(
                        name = user?.name.orEmpty(),
                        avatarDataUrl = user?.avatarDataUrl,
                        size = BarAction,
                        corner = BarAction / 2,
                        background = IndigoSoft,
                        contentColor = Indigo,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings)) },
                        onClick = {
                            menu = false
                            onOpenSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.logout)) },
                        onClick = {
                            menu = false
                            scope.launch { graph.session.clear() }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteMemberDialog(
    inviteCode: String,
    canInvite: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val qr = remember(inviteCode, canInvite) {
        if (canInvite) qrBitmap(inviteCode, 420).asImageBitmap() else null
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.add_workspace_member),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (!canInvite) {
                    Text(
                        stringResource(R.string.invite_admin_only),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        inviteCode,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            copyText(context, inviteCode)
                            copied = true
                        },
                    )
                    if (copied) {
                        Text(stringResource(R.string.copied), color = MaterialTheme.colorScheme.secondary)
                    }
                    qr?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(R.string.share_qr),
                            modifier = Modifier.size(160.dp),
                        )
                    }
                    SoftPrimaryButton(
                        text = stringResource(R.string.share_code),
                        onClick = { shareInviteText(context, inviteCode) },
                    )
                    SoftPrimaryButton(
                        text = stringResource(R.string.share_qr),
                        onClick = { shareInviteQr(context, inviteCode) },
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun primaryTabs(): List<Tab> = listOf(
    Tab("home", stringResource(R.string.tab_home), Icons.Outlined.Home, Icons.Filled.Home),
    Tab("tasks", stringResource(R.string.tab_tasks), Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    Tab("chat", stringResource(R.string.tab_chat), Icons.Outlined.ChatBubbleOutline, Icons.Filled.Chat),
)

@Composable
fun WorkHubNav() {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val current = session
    if (current == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        return
    }
    if (current.token == null || current.user == null) {
        AuthNav()
    } else if (current.workspace == null) {
        WorkspaceHomeScreen()
    } else {
        MainNav()
    }
}

@Composable
private fun MainNav() {
    NotificationBinder()
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsState(initial = null)
    val tabs = primaryTabs()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val immersive = route == "maps" || route == "search" || route == "calendar"
    val hideWorkspaceBar by graph.hideWorkspaceBar.collectAsState()
    val chatChromeVisible by graph.chatChromeVisible.collectAsState()
    val hideChrome = hideWorkspaceBar || (route == "chat" && !chatChromeVisible)
    var appsOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    var previousRoute by remember { mutableStateOf<String?>(null) }
    val go: (String) -> Unit = { target ->
        if (target != route) previousRoute = route
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openTool: (String) -> Unit = { target ->
        if (target != route) previousRoute = route
        nav.navigate(target) { launchSingleTop = true }
    }
    LaunchedEffect(Unit) {
        graph.pendingDeepLink.collect { link ->
            if (link == null) return@collect
            graph.pendingDeepLink.value = null
            when (link.route) {
                "chat" -> {
                    graph.chatChromeVisible.value = false
                    go("chat")
                    link.channelId?.let { graph.pendingChatChannelId.value = it }
                }
                "tasks" -> go("tasks")
                else -> go("home")
            }
        }
    }
    fun navigateBack() {
        when {
            appsOpen -> appsOpen = false
            immersive -> {
                if (!nav.popBackStack()) {
                    val back = previousRoute
                    previousRoute = null
                    go(back?.takeIf { it != route } ?: "home")
                }
            }
            previousRoute != null && previousRoute != route -> {
                val back = previousRoute!!
                previousRoute = if (back == "home") null else "home"
                go(back)
            }
            route != "home" -> go("home")
        }
    }
    BackHandler(enabled = appsOpen || immersive || route != "home") {
        navigateBack()
    }
    val edgeSwipe = rememberEdgeSwipeBack(
        enabled = appsOpen || immersive || route != "home" || hideWorkspaceBar,
    )
    val workspace = session?.workspace
    Scaffold(
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            if (!immersive && !hideChrome) {
                WorkspaceBar(
                    onOpenSettings = { settingsOpen = true },
                    onInvite = { inviteOpen = true },
                )
            }
        },
        bottomBar = {
            if (!immersive && !hideChrome) {
                SoftTabBar(
                    tabs = tabs,
                    selectedRoute = route,
                    appsHighlighted = appsOpen || route == "notes" || route == "org",
                    onSelect = { tab -> go(tab.route) },
                    onApps = { appsOpen = true },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).then(edgeSwipe).fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("home") {
                HomeScreen(
                    onOpenTasks = { go("tasks") },
                    onOpenNotes = { go("notes") },
                    onOpenChat = { go("chat") },
                    onOpenApps = { appsOpen = true },
                    onOpenMaps = { openTool("maps") },
                    onOpenSearch = { q ->
                        openTool("search")
                        runCatching {
                            nav.getBackStackEntry("search").savedStateHandle["search_q"] = q
                        }
                    },
                    onOpenCalendar = { openTool("calendar") },
                )
            }
            composable("tasks") { TaskScreen() }
            composable("notes") { NoteScreen() }
            composable("chat") {
                ChatScreen(onOpenMaps = { openTool("maps") })
            }
            composable("org") { AdminOrgScreen() }
            composable("maps") {
                MapsInAppScreen(
                    onBack = { nav.popBackStack() },
                    onUseLink = { link ->
                        graph.attachLink.tryEmit(normalizeMapsShareLink(link) ?: link)
                        nav.popBackStack()
                    },
                )
            }
            composable("search") { entry ->
                val seed by entry.savedStateHandle
                    .getStateFlow("search_q", "")
                    .collectAsState()
                key(seed) {
                    SearchInAppScreen(
                        onBack = { nav.popBackStack() },
                        initialQuery = seed,
                    )
                }
            }
            composable("calendar") {
                WorkspaceCalendarScreen(onBack = { nav.popBackStack() })
            }
        }
        } // edge-swipe Box
    }
    if (settingsOpen) {
        UserSettingsSheet(
            onDismiss = { settingsOpen = false },
            onOpenPlanner = { openTool("calendar") },
            onOpenOrg = { go("org") },
            onInvite = {
                settingsOpen = false
                inviteOpen = true
            },
        )
    }
    if (inviteOpen) {
        InviteMemberDialog(
            inviteCode = workspace?.inviteCode.orEmpty(),
            canInvite = workspace?.role == "ADMIN" && !workspace?.inviteCode.isNullOrBlank(),
            onDismiss = { inviteOpen = false },
        )
    }
    if (appsOpen) {
        AppsSheet(
            showOrg = session?.workspace?.role == "ADMIN",
            onDismiss = { appsOpen = false },
            onOpenNotes = { go("notes") },
            onOpenChat = { go("chat") },
            onOpenOrg = { go("org") },
            onOpenMaps = {
                appsOpen = false
                openTool("maps")
            },
            onOpenSearch = {
                appsOpen = false
                openTool("search")
                runCatching {
                    nav.getBackStackEntry("search").savedStateHandle["search_q"] = ""
                }
            },
            onOpenCalendar = {
                appsOpen = false
                openTool("calendar")
            },
        )
    }
}

@Composable
private fun SoftTabBar(
    tabs: List<Tab>,
    selectedRoute: String,
    appsHighlighted: Boolean,
    onSelect: (Tab) -> Unit,
    onApps: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF9FAFB))
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Line),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { tab ->
                SoftTabItem(
                    tab = tab,
                    selected = selectedRoute == tab.route && !appsHighlighted,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
            SoftAppsTab(
                selected = appsHighlighted,
                onClick = onApps,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SoftAppsTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) Indigo else Muted,
        label = "appsTint",
    )
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (selected) IndigoSoft else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.Apps else Icons.Outlined.Apps,
                contentDescription = stringResource(R.string.tab_apps),
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            stringResource(R.string.tab_apps),
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SoftTabItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) Indigo else Muted,
        label = "tabTint",
    )
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            tab.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
