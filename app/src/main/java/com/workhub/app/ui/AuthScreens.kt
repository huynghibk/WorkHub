package com.workhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.workhub.app.BuildConfig
import com.workhub.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.workhub.app.data.AppGraph
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.data.userMessage
import com.workhub.shared.RegisterRequest
import kotlinx.coroutines.launch

@Composable
fun AuthNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "login") {
        composable("login") { LoginScreen(onRegister = { nav.navigate("register") }) }
        composable("register") { RegisterScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun LoginScreen(onRegister: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: AuthViewModel = viewModel(factory = authFactory(graph))
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(BuildConfig.DEFAULT_BASE_URL) }
    var rememberLogin by remember { mutableStateOf(false) }
    var serverDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        serverUrl = graph.session.current().baseUrl
        val saved = graph.session.rememberedLogin()
        if (saved != null) {
            login = saved.first
            password = saved.second
            rememberLogin = true
        }
    }
    Box(Modifier.fillMaxSize()) {
        AuthColumn(title = "WorkHub", subtitle = stringResource(R.string.login_subtitle)) {
            SoftField(login, { login = it }, stringResource(R.string.login_field))
            PasswordField(password, { password = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = rememberLogin, onCheckedChange = { rememberLogin = it })
                Text(stringResource(R.string.remember_login))
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SoftPrimaryButton(
                text = stringResource(R.string.sign_in),
                onClick = { vm.login(login, password, rememberLogin, serverUrl) },
                enabled = !vm.busy,
            )
            if (vm.busy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(
                    "Đang kết nối máy chủ (có thể mất ~30s nếu Render đang ngủ)…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            TextButton(onClick = onRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.create_account))
            }
            Text(
                serverUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        ServerCornerButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            onClick = { serverDialog = true },
        )
    }
    if (serverDialog) {
        ServerUrlDialog(
            current = serverUrl,
            onDismiss = { serverDialog = false },
            onSave = { url ->
                serverUrl = url.trimEnd('/')
                scope.launch { graph.session.setBaseUrl(serverUrl) }
                serverDialog = false
            },
        )
    }
}

@Composable
private fun RegisterScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: AuthViewModel = viewModel(factory = authFactory(graph))
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(BuildConfig.DEFAULT_BASE_URL) }
    var serverDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        serverUrl = graph.session.current().baseUrl
    }
    Box(Modifier.fillMaxSize()) {
        AuthColumn(title = stringResource(R.string.register_title), subtitle = stringResource(R.string.register_subtitle)) {
            SoftField(name, { name = it }, stringResource(R.string.display_name))
            SoftField(username, { username = it }, "Username")
            SoftField(email, { email = it }, "Email")
            PasswordField(password, { password = it })
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SoftPrimaryButton(
                text = stringResource(R.string.sign_up),
                onClick = {
                    vm.register(
                        RegisterRequest(name = name, email = email, password = password, username = username),
                        serverUrl,
                    )
                },
                enabled = !vm.busy,
            )
            if (vm.busy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.have_account))
            }
            Text(
                serverUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        ServerCornerButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            onClick = { serverDialog = true },
        )
    }
    if (serverDialog) {
        ServerUrlDialog(
            current = serverUrl,
            onDismiss = { serverDialog = false },
            onSave = { url ->
                serverUrl = url.trimEnd('/')
                scope.launch { graph.session.setBaseUrl(serverUrl) }
                serverDialog = false
            },
        )
    }
}

@Composable
private fun ServerCornerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Filled.Dns,
                contentDescription = stringResource(R.string.server_settings),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ServerUrlDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.server_custom),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.trimEnd('/') == BuildConfig.CLOUD_BASE_URL.trimEnd('/'),
                        onClick = { draft = BuildConfig.CLOUD_BASE_URL },
                        label = { Text(stringResource(R.string.server_preset_cloud)) },
                    )
                    FilterChip(
                        selected = draft.trimEnd('/') == BuildConfig.EMULATOR_BASE_URL.trimEnd('/'),
                        onClick = { draft = BuildConfig.EMULATOR_BASE_URL },
                        label = { Text(stringResource(R.string.server_preset_emulator)) },
                    )
                }
                SoftField(
                    draft,
                    { draft = it },
                    stringResource(R.string.server_url),
                    placeholder = stringResource(R.string.server_url_hint),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text(stringResource(R.string.server_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
internal fun PasswordField(value: String, onValue: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(stringResource(R.string.password)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = SoftPill,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) stringResource(R.string.hide_password) else stringResource(R.string.show_password),
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.92f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.78f),
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoiceField(label: String, selectedId: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.first == selectedId }?.second.orEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.choose_option, label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AuthColumn(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    SoftBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandHero(height = 248.dp)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                HideKeyboardButton(always = true)
            }
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

class AuthViewModel(private val graph: AppGraph) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun login(login: String, password: String, remember: Boolean, serverUrl: String) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                graph.session.setBaseUrl(serverUrl)
                // Render free may be asleep — ensureAwake() inside login waits for /health.
                val response = graph.api.login(login.trim(), password)
                if (remember) graph.session.rememberLogin(login.trim(), password) else graph.session.forgetLogin()
                graph.session.save(response.token, response.user)
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    fun register(request: RegisterRequest, serverUrl: String) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                graph.session.setBaseUrl(serverUrl)
                // ensureAwake() inside register waits while Render cold-starts.
                val response = graph.api.register(request)
                graph.session.save(response.token, response.user)
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }
}

private fun authFactory(graph: AppGraph) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(graph) as T
    }
}
