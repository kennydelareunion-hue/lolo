package com.termux.devcenter.ui.omniroute

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.devcenter.data.omniroute.OmniRouteConfig
import com.termux.devcenter.data.omniroute.OmniRouteSettings

/**
 * Tableau de bord OmniRoute intégré : connexion (mot de passe du dashboard),
 * ajout des fournisseurs (Kiro…) et création des clés API, sans passer par un navigateur.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OmniRouteScreen() {
    val context = LocalContext.current
    val omniSettings = remember { OmniRouteSettings(context.applicationContext) }
    val config by omniSettings.config.collectAsState(initial = null)
    val baseUrl = config?.normalizedBaseUrl ?: return
    val startUrl = "$baseUrl/dashboard/providers"

    var currentUrl by remember { mutableStateOf(startUrl) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    val webView = remember(baseUrl) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme.orEmpty()
                    if (scheme == "http" || scheme == "https") return false
                    // Liens spéciaux (intent:, mailto:…) : délégués au système.
                    runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    loading = true
                    loadError = null
                    currentUrl = url
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading = false
                    canGoBack = view.canGoBack()
                    CookieManager.getInstance().flush()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        loading = false
                        loadError = "${error.description} (${request.url})"
                    }
                }
            }

            // Les fenêtres pop-up (OAuth des fournisseurs) s'ouvrent dans la même vue
            // pour que la redirection vers localhost revienne bien dans OmniRoute.
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                    val popup = WebView(view.context)
                    popup.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                            view.loadUrl(request.url.toString())
                            return true
                        }

                        override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                            view.loadUrl(url)
                            v.stopLoading()
                        }
                    }
                    (resultMsg.obj as WebView.WebViewTransport).webView = popup
                    resultMsg.sendToTarget()
                    return true
                }
            }
            loadUrl(startUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            CookieManager.getInstance().flush()
            webView.destroy()
        }
    }

    BackHandler(enabled = canGoBack) { webView.goBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(onClick = { webView.goBack() }, enabled = canGoBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            IconButton(onClick = { webView.loadUrl(startUrl) }) {
                Icon(Icons.Default.Home, contentDescription = "Fournisseurs")
            }
            IconButton(onClick = { webView.loadUrl("$baseUrl/dashboard/api-manager") }) {
                Icon(Icons.Default.Key, contentDescription = "Clés API")
            }
            Text(
                text = currentUrl,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { webView.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Recharger")
            }
            IconButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
            }) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = "Ouvrir dans le navigateur")
            }
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        loadError?.let {
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("OmniRoute est injoignable", style = MaterialTheme.typography.titleSmall)
                    Text(it, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Démarrez-le depuis l'Accueil (bouton « Démarrer OmniRoute ») ou vérifiez l'URL dans Paramètres " +
                            "(par défaut ${OmniRouteConfig.DEFAULT_BASE_URL}).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
}
