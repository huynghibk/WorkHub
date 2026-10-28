package com.workhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var rememberLogin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        serverUrl = graph.session.current().baseUrl
        val saved = graph.session.rememberedLogin()
        if (saved != null) {
            login = saved.first
            password = saved.second
            rememberLogin = true
        }
    }
    AuthColumn(title = "WorkHub", subtitle = stringResource(R.string.login_subtitle)) {
        SoftField(
            serverUrl,
            { serverUrl = it },
            stringResource(R.string.server_url),
            placeholder = stringResource(R.string.server_url_hint),
        )
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
        }
        TextButton(onClick = onRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.create_account))
        }
    }
}

@Composable
private fun RegisterScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm: AuthViewModel = viewModel(factory = authFactory(graph))
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        serverUrl = graph.session.current().baseUrl
    }
    AuthColumn(title = stringResource(R.string.register_title), subtitle = stringResource(R.string.register_subtitle)) {
        SoftField(
            serverUrl,
            { serverUrl = it },
            stringResource(R.string.server_url),
            placeholder = stringResource(R.string.server_url_hint),
        )
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
    }
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
