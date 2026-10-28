package com.workhub.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.workhub.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ToolBg = Color(0xFFF9FAFB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsInAppScreen(
    onBack: () -> Unit,
    onUseLink: ((String) -> Unit)? = null,
    initialQuery: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var query by remember { mutableStateOf(initialQuery) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var shareMenu by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var pageUrl by remember { mutableStateOf(startUrlFallback(initialQuery)) }
    val startUrl = remember(initialQuery) { startUrlFallback(initialQuery) }

    fun fallbackLink(): String {
        val raw = pageUrl.takeIf { it.isNotBlank() && !it.equals("about:blank", true) }
            ?: webView?.url?.takeIf { it.isNotBlank() && !it.equals("about:blank", true) }
        return normalizeMapsShareLink(raw) ?: currentMapsUrl(context) ?: "https://www.google.com/maps"
    }

    fun resolveAndUse(onReady: (String) -> Unit) {
        val view = webView
        if (view == null) {
            onReady(fallbackLink())
            return
        }
        view.evaluateJavascript(MAPS_SHARE_JS) { raw ->
            val parsed = parseJsString(raw)
            val link = normalizeMapsShareLink(parsed) ?: normalizeMapsShareLink(view.url) ?: fallbackLink()
            onReady(link)
        }
    }

    fun goMyLocation() {
        if (locating) return
        locating = true
        scope.launch {
            val location = awaitBestLocation(context)
            locating = false
            if (location == null) {
                toastMissingLocation(context)
                return@launch
            }
            val url = mapsUrlFrom(location.latitude, location.longitude)
            webView?.loadUrl(url)
            pageUrl = url
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) goMyLocation() else toastMissingLocation(context)
    }

    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        while (true) {
            delay(800)
            val href = view.url
            if (!href.isNullOrBlank() && href != pageUrl) {
                pageUrl = href
            }
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    Scaffold(
        containerColor = ToolBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.app_maps), fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    IconButton(
                        enabled = !locating,
                        onClick = {
                            if (hasLocationPermission(context)) {
                                goMyLocation()
                            } else {
                                locationPermission.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ),
                                )
                            }
                        },
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.maps_my_location))
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    Box {
                        IconButton(onClick = { shareMenu = true }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_code))
                        }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_via_zalo)) },
                                onClick = {
                                    shareMenu = false
                                    resolveAndUse { link ->
                                        shareLocationToChatApp(context, AppPackages.ZALO, link, "https://zalo.me/")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_via_messenger)) },
                                onClick = {
                                    shareMenu = false
                                    resolveAndUse { link ->
                                        shareLocationToChatApp(context, AppPackages.MESSENGER, link, "https://www.messenger.com/")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_via_wechat)) },
                                onClick = {
                                    shareMenu = false
                                    resolveAndUse { link ->
                                        shareLocationToChatApp(context, AppPackages.WECHAT, link, "https://www.wechat.com/")
                                    }
                                },
                            )
                        }
                    }
                    IconButton(onClick = {
                        resolveAndUse { link ->
                            val ok = copyTextToClipboard(context, link)
                            Toast.makeText(
                                context,
                                context.getString(if (ok) R.string.copied_text else R.string.clipboard_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy_text))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.maps_search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) webView?.loadUrl(mapsSearchUrl(query.trim()))
                    }),
                )
                if (onUseLink != null) {
                    SoftPrimaryButton(
                        text = stringResource(R.string.maps_use_link),
                        onClick = {
                            resolveAndUse { link ->
                                onUseLink(link)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.maps_attached),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
                TextButton(
                    onClick = {
                        if (query.isNotBlank()) webView?.loadUrl(mapsSearchUrl(query.trim()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.maps_search_action))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            WebToolView(
                initialUrl = startUrl,
                onCreated = { webView = it },
                onLoading = { loading = it },
                onProgress = { progress = it },
                onUrlChanged = { pageUrl = it },
            )
            if (loading && progress < 1f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInAppScreen(
    onBack: () -> Unit,
    initialQuery: String = "",
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var query by remember { mutableStateOf(initialQuery) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    val startUrl = remember(initialQuery) {
        if (initialQuery.isBlank()) "https://www.google.com" else googleSearchUrl(initialQuery)
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    Scaffold(
        containerColor = ToolBg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_search), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text(stringResource(R.string.search_web_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (query.isNotBlank()) webView?.loadUrl(googleSearchUrl(query.trim()))
                }),
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                },
            )
            Box(Modifier.fillMaxSize()) {
                WebToolView(
                    initialUrl = startUrl,
                    onCreated = { webView = it },
                    onLoading = { loading = it },
                    onProgress = { progress = it },
                )
                if (loading && progress < 1f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
                if (loading && progress == 0f) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
fun MapsInAppDialog(
    onDismiss: () -> Unit,
    onUseLink: (String) -> Unit,
    initialQuery: String = "",
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White),
        ) {
            MapsInAppScreen(
                onBack = onDismiss,
                onUseLink = onUseLink,
                initialQuery = initialQuery,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebToolView(
    initialUrl: String,
    onCreated: (WebView) -> Unit,
    onLoading: (Boolean) -> Unit,
    onProgress: (Float) -> Unit,
    onUrlChanged: (String) -> Unit = {},
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress / 100f)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            onUrlChanged(url)
                            return false
                        }
                        return true
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onLoading(true)
                        if (!url.isNullOrBlank()) onUrlChanged(url)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoading(false)
                        onProgress(1f)
                        val href = url ?: view?.url
                        if (!href.isNullOrBlank()) onUrlChanged(href)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (!url.isNullOrBlank()) onUrlChanged(url)
                    }
                }
                loadUrl(initialUrl)
                onCreated(this)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.destroy() },
    )
}

private const val MAPS_SHARE_JS = """
(function(){
  var href = location.href || '';
  var m = href.match(/@(-?\d+\.?\d*),(-?\d+\.?\d*)/);
  if (m) return 'https://maps.google.com/?q=' + m[1] + ',' + m[2];
  m = href.match(/[?&]q=(-?\d+\.?\d*),(-?\d+\.?\d*)/);
  if (m) return 'https://maps.google.com/?q=' + m[1] + ',' + m[2];
  m = href.match(/!3d(-?\d+\.?\d*)!4d(-?\d+\.?\d*)/);
  if (m) return 'https://maps.google.com/?q=' + m[1] + ',' + m[2];
  m = href.match(/[?&]q=([^&]+)/);
  if (m) return 'https://www.google.com/maps/search/?api=1&query=' + m[1];
  return href;
})();
"""

private fun startUrlFallback(initialQuery: String): String =
    if (initialQuery.isBlank()) "https://www.google.com/maps" else mapsSearchUrl(initialQuery)

private fun parseJsString(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return runCatching {
        org.json.JSONTokener(raw).nextValue() as? String
    }.getOrElse {
        raw.trim()
            .removeSurrounding("\"")
            .replace("\\/", "/")
            .replace("\\u003d", "=")
            .replace("\\u0026", "&")
    }
}

fun normalizeMapsShareLink(raw: String?): String? {
    if (raw.isNullOrBlank() || raw.equals("about:blank", true)) return null
    val decoded = android.net.Uri.decode(raw)
    Regex("""@(-?\d+\.?\d*),(-?\d+\.?\d*)""").find(decoded)?.let { m ->
        return "https://maps.google.com/?q=${m.groupValues[1]},${m.groupValues[2]}"
    }
    Regex("""[?&]q=(-?\d+\.?\d*),(-?\d+\.?\d*)""").find(decoded)?.let { m ->
        return "https://maps.google.com/?q=${m.groupValues[1]},${m.groupValues[2]}"
    }
    Regex("""!3d(-?\d+\.?\d*)!4d(-?\d+\.?\d*)""").find(decoded)?.let { m ->
        return "https://maps.google.com/?q=${m.groupValues[1]},${m.groupValues[2]}"
    }
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
    return null
}

fun mapsSearchUrl(query: String): String =
    "https://www.google.com/maps/search/?api=1&query=" + android.net.Uri.encode(query)

fun googleSearchUrl(query: String): String =
    "https://www.google.com/search?q=" + android.net.Uri.encode(query) + "&hl=vi"
