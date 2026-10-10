package org.uxaoro.app.ui.news

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.LoadingBox
import org.uxaoro.app.ui.components.inkTextButtonColors
import org.uxaoro.app.ui.motion.staggerIn
import org.uxaoro.app.ui.premium.BadgesAndSalesSection
import org.uxaoro.app.data.PostOut
import org.uxaoro.app.ui.components.EmptyBox
import org.uxaoro.app.ui.feed.PostItem
import org.uxaoro.app.ui.theme.BlurScaffold
import org.uxaoro.app.ui.theme.GlassPanel

const val NEWS_REMOTE_BASE = "https://fourtexec.site/uxaoro/news/"

data class NewsItem(
    val id: String,
    val type: String, // "popup" or "tab"
    val title: String,
    val date: String = "",
    val badge: String = "",
    val summary: String = "",
    val htmlFile: String = "",
    val htmlContent: String = "",
    val mdFile: String = "",
    val markdown: String = "",
    val appearOnEnter: Boolean = true,
    val closable: Boolean = false,
    val fullscreen: Boolean = false,
    val allowFullscreen: Boolean = true,
    val once: Boolean = true,
    val platforms: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
)

data class LoadedNewsBundle(
    val popups: List<NewsItem>,
    val tabItems: List<NewsItem>,
)

object NewsRepository {
    private val FALLBACK_POPUP_HTML = """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            body { margin:0; padding:22px; font-family:sans-serif; background:#0b0b10; color:#f5f5f7; display:flex; flex-direction:column; min-height:100vh; box-sizing:border-box; justify-content:space-between; }
            .pill { display:inline-block; padding:4px 10px; border-radius:999px; font-size:11px; font-weight:800; background:linear-gradient(135deg,#6366f1,#8b5cf6); color:#fff; }
            h1 { margin:12px 0 8px; font-size:22px; }
            p { margin:0 0 12px; color:#a0a0ab; font-size:13.5px; line-height:1.5; }
            .card { padding:11px 13px; border-radius:12px; background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.12); margin-bottom:8px; }
            .card b { display:block; font-size:13px; margin-bottom:2px; color:#fff; }
            .card span { font-size:12px; color:#a0a0ab; }
            .row { display:flex; gap:8px; margin-top:14px; }
            button { border:1px solid rgba(255,255,255,0.16); background:rgba(255,255,255,0.08); color:#fff; padding:11px 14px; border-radius:12px; font-weight:700; font-size:13px; }
            button.pri { flex:1; border:none; background:linear-gradient(135deg,#6366f1,#8b5cf6); }
          </style>
        </head>
        <body>
          <div>
            <span class="pill">Uxaoro v0.1.0 · Beta News</span>
            <h1>Welcome to Uxaoro Beta</h1>
            <p>Configured from <code>https://fourtexec.site/uxaoro/news/</code>. This popup closes when you tap <b>Continue to Uxaoro</b>.</p>
            <div class="card"><b>News Tab</b><span>Read HTML &amp; Markdown announcements in the News tab.</span></div>
            <div class="card"><b>More Tab</b><span>Access Settings, UX Wallet, Premium, Sales, and Badges in the More tab.</span></div>
          </div>
          <div class="row">
            <button onclick="window.UxaoroNews && window.UxaoroNews.toggleFullscreen()">⛶ Full screen</button>
            <button class="pri" onclick="window.UxaoroNews && window.UxaoroNews.close()">Continue to Uxaoro</button>
          </div>
        </body>
        </html>
    """.trimIndent()

    private val FALLBACK_TAB_HTML = """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            body { margin:0; padding:16px; font-family:sans-serif; background:#101118; color:#f4f4f6; }
            h3 { margin:0 0 6px; font-size:16px; color:#6366f1; }
            p { margin:0 0 10px; font-size:13px; color:#9ca3af; line-height:1.45; }
            .box { padding:10px 12px; border-radius:10px; background:rgba(255,255,255,0.05); border:1px solid rgba(255,255,255,0.1); margin-top:8px; }
          </style>
        </head>
        <body>
          <h3>Inside Uxaoro v0.1.0 Beta</h3>
          <p>Rendered from HTML configured in <code>https://fourtexec.site/uxaoro/news/</code>.</p>
          <div class="box"><b>✦ Premium, Sales &amp; Badges</b><p>Manage Premium, apply icon badges, and browse UX sales in the More tab.</p></div>
        </body>
        </html>
    """.trimIndent()

    fun matchesTarget(item: NewsItem): Boolean {
        val curPlat = "android"
        val curVer = Prefs.APP_VERSION.removePrefix("v").removePrefix("V").trim().lowercase()
        if (item.platforms.isNotEmpty()) {
            val norm = item.platforms.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (norm.isNotEmpty() && "all" !in norm && "*" !in norm && curPlat !in norm) {
                return false
            }
        }
        if (item.versions.isNotEmpty()) {
            val normV = item.versions.map { it.trim().removePrefix("v").removePrefix("V").lowercase() }.filter { it.isNotEmpty() }
            if (normV.isNotEmpty() && "all" !in normV && "*" !in normV && curVer !in normV) {
                return false
            }
        }
        return true
    }

    suspend fun fetchText(subPath: String): String? = withContext(Dispatchers.IO) {
        val clean = subPath.trim().trimStart('/')
        if (clean.isEmpty()) return@withContext null
        val urls = if (clean.startsWith("http://") || clean.startsWith("https://")) {
            listOf(clean)
        } else {
            listOf(
                NEWS_REMOTE_BASE + clean,
                Prefs.apiBase.trimEnd('/') + "/news/" + clean,
            )
        }
        val client = Api.http ?: Api.mediaClient()
        for (u in urls) {
            try {
                val req = Request.Builder().url(u).header("Cache-Control", "no-cache").build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) return@withContext body
                    }
                }
            } catch (_: Throwable) {
            }
        }
        null
    }

    private fun parseStringList(el: JsonElement?): List<String> {
        if (el == null || el.isJsonNull) return emptyList()
        if (el.isJsonArray) {
            return el.asJsonArray.mapNotNull { runCatching { it.asString.trim() }.getOrNull() }.filter { it.isNotEmpty() }
        }
        if (el.isJsonPrimitive) {
            return el.asString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return emptyList()
    }

    private fun JsonObject.str(vararg keys: String): String {
        for (k in keys) {
            val v = get(k)
            if (v != null && v.isJsonPrimitive) {
                val s = runCatching { v.asString }.getOrNull()
                if (!s.isNullOrBlank()) return s.trim()
            }
        }
        return ""
    }

    private fun JsonObject.bool(default: Boolean, vararg keys: String): Boolean {
        for (k in keys) {
            val v = get(k)
            if (v != null && v.isJsonPrimitive) {
                return runCatching { v.asBoolean }.getOrDefault(default)
            }
        }
        return default
    }

    private fun parseItem(obj: JsonObject, defaultType: String, idx: Int): NewsItem {
        val type = obj.str("type").ifBlank { defaultType }
        val id = obj.str("id").ifBlank { "$type-$idx-${obj.str("title").hashCode()}" }
        return NewsItem(
            id = id,
            type = type,
            title = obj.str("title").ifBlank { "Uxaoro News" },
            date = obj.str("date"),
            badge = obj.str("badge", "tag"),
            summary = obj.str("summary", "description"),
            htmlFile = obj.str("html", "file", "url"),
            htmlContent = obj.str("html_content"),
            mdFile = obj.str("md"),
            markdown = obj.str("markdown"),
            appearOnEnter = obj.bool(true, "appear_on_enter", "show_on_enter"),
            closable = obj.bool(false, "closable", "allow_close", "can_close"),
            fullscreen = obj.bool(false, "fullscreen"),
            allowFullscreen = obj.bool(true, "allow_fullscreen"),
            once = obj.bool(true, "once"),
            platforms = parseStringList(obj.get("platforms") ?: obj.get("platform")),
            versions = parseStringList(obj.get("versions") ?: obj.get("version")),
        )
    }

    private fun parseMarkdownSections(mdRaw: String): List<NewsItem> {
        val trimmed = mdRaw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return trimmed.split(Regex("\\n---\\n")).mapIndexedNotNull { idx, block ->
            val b = block.trim()
            if (b.isEmpty()) return@mapIndexedNotNull null
            val lines = b.lines()
            val first = lines.firstOrNull()?.trim().orEmpty()
            val title = if (first.startsWith("# ")) first.removePrefix("# ").trim() else "News #${idx + 1}"
            val body = if (first.startsWith("# ")) lines.drop(1).joinToString("\n").trim() else b
            NewsItem(
                id = "md-section-$idx",
                type = "tab",
                title = title,
                badge = "Markdown",
                markdown = body,
            )
        }
    }

    suspend fun loadBundle(): LoadedNewsBundle = withContext(Dispatchers.IO) {
        val rawCfg = fetchText("config.json") ?: fetchText("news.json")
        val popups = mutableListOf<NewsItem>()
        val tabs = mutableListOf<NewsItem>()
        var mdFileName = "news.md"

        if (!rawCfg.isNullOrBlank()) {
            runCatching {
                val root = JsonParser.parseString(rawCfg).asJsonObject
                val mf = root.str("md_file", "markdown_file")
                if (mf.isNotBlank()) mdFileName = mf

                fun collectArray(arr: JsonArray?, defaultType: String) {
                    if (arr == null) return
                    arr.forEachIndexed { idx, el ->
                        if (el.isJsonObject) {
                            val item = parseItem(el.asJsonObject, defaultType, idx)
                            if (item.type == "popup") popups.add(item) else tabs.add(item)
                        }
                    }
                }
                collectArray(root.getAsJsonArray("popups"), "popup")
                collectArray(root.getAsJsonArray("tab"), "tab")
                collectArray(root.getAsJsonArray("items"), "tab")
                collectArray(root.getAsJsonArray("news"), "tab")
            }
        }

        val mdRaw = fetchText(mdFileName)
        if (!mdRaw.isNullOrBlank()) {
            tabs.addAll(parseMarkdownSections(mdRaw))
        }

        if (popups.isEmpty() && tabs.isEmpty()) {
            popups.add(
                NewsItem(
                    id = "uxaoro-beta-0.1.0-popup",
                    type = "popup",
                    title = "Welcome to Uxaoro v0.1.0 Beta",
                    htmlContent = FALLBACK_POPUP_HTML,
                    appearOnEnter = true,
                    closable = false,
                    fullscreen = false,
                    allowFullscreen = true,
                    once = true,
                    platforms = listOf("android", "desktop", "web"),
                    versions = listOf("0.1.0"),
                )
            )
            tabs.add(
                NewsItem(
                    id = "uxaoro-beta-launch-html",
                    type = "tab",
                    title = "Uxaoro v0.1.0 Beta — Official Launch Overview",
                    date = "2026-10-01",
                    badge = "Featured HTML",
                    summary = "Interactive HTML overview of Uxaoro v0.1.0 Beta.",
                    htmlContent = FALLBACK_TAB_HTML,
                    platforms = listOf("android", "desktop", "web"),
                    versions = listOf("0.1.0", "*"),
                )
            )
            tabs.add(
                NewsItem(
                    id = "uxaoro-android-more-tab",
                    type = "tab",
                    title = "Android App Update: New More & News Tabs",
                    date = "2026-10-01",
                    badge = "Android",
                    summary = "Settings, UX Wallet, Uxaoro Premium, Sales, and Badges are now inside the More tab.",
                    markdown = "### New Navigation on Android\n- **News**: Read announcements and HTML pages configured from `https://fourtexec.site/uxaoro/news/`.\n- **More**: Access **Settings**, **UX Wallet**, **Uxaoro Premium**, **Sales**, and **Badges** in one place.",
                    platforms = listOf("android"),
                    versions = listOf("0.1.0"),
                )
            )
        }

        val resolvedPopups = popups.filter { matchesTarget(it) }.map { item ->
            if (item.htmlContent.isBlank() && item.htmlFile.isNotBlank()) {
                val loaded = fetchText(item.htmlFile) ?: FALLBACK_POPUP_HTML
                item.copy(htmlContent = loaded)
            } else item
        }
        val resolvedTabs = tabs.filter { matchesTarget(it) }.map { item ->
            var next = item
            if (next.htmlContent.isBlank() && next.htmlFile.isNotBlank()) {
                val loaded = fetchText(next.htmlFile)
                if (!loaded.isNullOrBlank()) next = next.copy(htmlContent = loaded)
            }
            if (next.markdown.isBlank() && next.mdFile.isNotBlank()) {
                val loadedMd = fetchText(next.mdFile)
                if (!loadedMd.isNullOrBlank()) next = next.copy(markdown = loadedMd)
            }
            next
        }
        LoadedNewsBundle(resolvedPopups, resolvedTabs)
    }

    fun injectBridge(rawHtml: String): String {
        val bridge = """
            <script>
            (function(){
              window.UxaoroNews = {
                close: function() {
                  try { if (window.UxaoroNewsAndroid) window.UxaoroNewsAndroid.close(); } catch (_) {}
                },
                fullscreen: function(val) {
                  try { if (window.UxaoroNewsAndroid) window.UxaoroNewsAndroid.setFullscreen(!!val); } catch (_) {}
                },
                toggleFullscreen: function() {
                  try { if (window.UxaoroNewsAndroid) window.UxaoroNewsAndroid.toggleFullscreen(); } catch (_) {}
                }
              };
              window.addEventListener("message", function(ev) {
                var d = ev && ev.data;
                if (!d || typeof d !== "object") return;
                if (d.type === "uxaoro-news-close") window.UxaoroNews.close();
                else if (d.type === "uxaoro-news-fullscreen") {
                  if (d.toggle) window.UxaoroNews.toggleFullscreen();
                  else window.UxaoroNews.fullscreen(!!d.value);
                }
              });
              document.addEventListener("click", function(e) {
                var el = e.target && e.target.closest ? e.target.closest("[data-news-close], a[href='uxaoro://news/close'], a[href='#close']") : null;
                if (el) { e.preventDefault(); window.UxaoroNews.close(); }
                var fs = e.target && e.target.closest ? e.target.closest("[data-news-fullscreen], a[href='uxaoro://news/fullscreen']") : null;
                if (fs) { e.preventDefault(); window.UxaoroNews.toggleFullscreen(); }
              });
            })();
            </script>
        """.trimIndent()
        return if (rawHtml.contains("</body>", ignoreCase = true)) {
            rawHtml.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$bridge</body>")
        } else {
            rawHtml + bridge
        }
    }
}

private class AndroidNewsJsBridge(
    private val onClose: () -> Unit,
    private val onSetFullscreen: (Boolean) -> Unit,
    private val onToggleFullscreen: () -> Unit,
) {
    @JavascriptInterface
    fun close() {
        onClose()
    }

    @JavascriptInterface
    fun setFullscreen(fs: Boolean) {
        onSetFullscreen(fs)
    }

    @JavascriptInterface
    fun toggleFullscreen() {
        onToggleFullscreen()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NewsHtmlWebView(
    html: String,
    modifier: Modifier = Modifier,
    onCloseRequested: () -> Unit = {},
    onSetFullscreen: (Boolean) -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val preparedHtml = remember(html) { NewsRepository.injectBridge(html) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(
                    AndroidNewsJsBridge(
                        onClose = { scope.launch { onCloseRequested() } },
                        onSetFullscreen = { v -> scope.launch { onSetFullscreen(v) } },
                        onToggleFullscreen = { scope.launch { onToggleFullscreen() } },
                    ),
                    "UxaoroNewsAndroid",
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val u = request?.url?.toString().orEmpty()
                        if (u.equals("uxaoro://news/close", ignoreCase = true) || u.endsWith("#close")) {
                            scope.launch { onCloseRequested() }
                            return true
                        }
                        if (u.equals("uxaoro://news/fullscreen", ignoreCase = true)) {
                            scope.launch { onToggleFullscreen() }
                            return true
                        }
                        return false
                    }
                }
                loadDataWithBaseURL(NEWS_REMOTE_BASE, preparedHtml, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != preparedHtml) {
                webView.tag = preparedHtml
                webView.loadDataWithBaseURL(NEWS_REMOTE_BASE, preparedHtml, "text/html", "utf-8", null)
            }
        },
    )
}

@Composable
fun NewsPopupDialog(
    item: NewsItem,
    onDismiss: () -> Unit,
) {
    var isFullscreen by remember(item.id) { mutableStateOf(item.fullscreen) }
    val canCloseHost = item.closable
    val allowFs = item.allowFullscreen

    Dialog(
        onDismissRequest = {
            if (canCloseHost) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = canCloseHost,
            dismissOnClickOutside = canCloseHost,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(if (isFullscreen) 0.dp else 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlassPanel(
                modifier = if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().height(520.dp)
                },
                shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp),
                intense = true,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            item.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (allowFs) {
                                IconButton(onClick = { isFullscreen = !isFullscreen }) {
                                    Icon(
                                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Toggle full screen",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                            if (canCloseHost) {
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                        }
                    }
                    NewsHtmlWebView(
                        html = item.htmlContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onCloseRequested = onDismiss,
                        onSetFullscreen = { if (allowFs) isFullscreen = it },
                        onToggleFullscreen = { if (allowFs) isFullscreen = !isFullscreen },
                    )
                }
            }
        }
    }
}

/**
 * Automatically checks https://fourtexec.site/uxaoro/news/config.json on enter
 * and displays any matching Popup News item for Android & current version.
 */
@Composable
fun NewsPopupOnEnterLayer() {
    var activePopup by remember { mutableStateOf<NewsItem?>(null) }
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (checked) return@LaunchedEffect
        checked = true
        val bundle = NewsRepository.loadBundle()
        val candidate = bundle.popups.firstOrNull { p ->
            p.appearOnEnter && (!p.once || !Prefs.isNewsPopupDismissed(p.id)) && p.htmlContent.isNotBlank()
        }
        if (candidate != null) {
            activePopup = candidate
        }
    }

    activePopup?.let { popup ->
        NewsPopupDialog(
            item = popup,
            onDismiss = {
                if (popup.once) {
                    Prefs.setNewsPopupDismissed(popup.id, true)
                }
                activePopup = null
            },
        )
    }
}

@Composable
fun MarkdownBlock(markdown: String) {
    val lines = remember(markdown) { markdown.replace("\r\n", "\n").lines() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) {
                Spacer(Modifier.height(2.dp))
                continue
            }
            when {
                line.startsWith("### ") -> Text(
                    line.removePrefix("### "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp),
                )
                line.startsWith("## ") -> Text(
                    line.removePrefix("## "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 6.dp),
                )
                line.startsWith("# ") -> Text(
                    line.removePrefix("# "),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 6.dp),
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row(
                    Modifier.padding(start = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        line.drop(2).replace("**", "").replace("`", ""),
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                line.startsWith("*") && line.endsWith("*") && line.length > 2 -> Text(
                    line.removeSurrounding("*"),
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    line.replace("**", "").replace("`", ""),
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
fun NewsScreen(vm: AppVm) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var bundle by remember { mutableStateOf(LoadedNewsBundle(emptyList(), emptyList())) }
    var previewPopup by remember { mutableStateOf<NewsItem?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            bundle = NewsRepository.loadBundle()
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    previewPopup?.let { item ->
        NewsPopupDialog(
            item = item,
            onDismiss = { previewPopup = null },
        )
    }

    BlurScaffold(
        topBar = {
            DATopBar(
                "News",
                onBack = { vm.back() },
                actions = {
                    IconButton(onClick = { reload() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh news", modifier = Modifier.size(18.dp))
                    }
                },
            )
        },
    ) { pad ->
        if (loading) {
            Box(Modifier.padding(pad).fillMaxSize()) { LoadingBox() }
        } else {
            val tabNewsOnly = bundle.tabItems.filter { it.type != "popup" }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = pad.calculateTopPadding() + 6.dp,
                    bottom = pad.calculateBottomPadding() + 72.dp,
                    start = 14.dp,
                    end = 14.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(tabNewsOnly, key = { idx, it -> "${it.id}-$idx" }) { i, item ->
                    Box(Modifier.staggerIn(i)) {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                item.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                            if (item.badge.isNotBlank()) {
                                                Box(
                                                    Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(MaterialTheme.colorScheme.primary)
                                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                                ) {
                                                    Text(
                                                        item.badge,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }
                                        }
                                        if (item.date.isNotBlank()) {
                                            Text(
                                                item.date,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (item.htmlContent.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                previewPopup = item.copy(
                                                    closable = true,
                                                    fullscreen = true,
                                                    allowFullscreen = true,
                                                    once = false,
                                                )
                                            },
                                            colors = inkTextButtonColors(),
                                        ) {
                                            Text("⛶ Full screen", fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (item.summary.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        item.summary,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }

                                if (item.markdown.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    MarkdownBlock(item.markdown)
                                }

                                if (item.htmlContent.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(260.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                    ) {
                                        NewsHtmlWebView(
                                            html = item.htmlContent,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Android "More" tab: houses Settings, UX Wallet, Uxaoro Premium, Sales, and Badges.
 */
@Composable
fun MoreScreen(vm: AppVm) {
    val me = vm.me
    val scope = rememberCoroutineScope()

    BlurScaffold(
        topBar = {
            DATopBar("More")
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 96.dp,
                start = 14.dp,
                end = 14.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MoreActionCard(
                    icon = Icons.Default.Article,
                    title = "News",
                    subtitle = "Announcements, updates, and articles",
                    onClick = { vm.go(Screen.News) },
                )
            }
            item {
                MoreActionCard(
                    icon = Icons.Default.Settings,
                    title = "Settings",
                    subtitle = "Appearance, privacy, 2FA, E2EE keys, and account",
                    onClick = { vm.go(Screen.Settings) },
                )
            }
            item {
                val upd by org.uxaoro.app.upd.Updater.state.collectAsState()
                val pending = upd.update
                MoreActionCard(
                    icon = Icons.Default.SystemUpdate,
                    title = "App updates",
                    subtitle = when {
                        pending != null && upd.required -> "Required update ${pending.version} — install to continue"
                        pending != null -> "Update available: ${pending.version}"
                        else -> "Uxaoro v${org.uxaoro.app.data.Prefs.APP_VERSION} · check for a new version"
                    },
                    onClick = { vm.go(Screen.Updates) },
                )
            }
            item {
                MoreActionCard(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "UX Wallet",
                    subtitle = "Balance: ${"%.2f".format(me?.balanceUx ?: 0.0)} UX · Top up & send gifts",
                    onClick = { vm.go(Screen.Wallet) },
                )
            }
            item {
                MoreActionCard(
                    icon = Icons.Default.AutoAwesome,
                    title = if (me?.premium == true) "✦ Manage Uxaoro Premium" else "✦ Get Uxaoro Premium",
                    subtitle = "Profile glow, name colors, chat bubbles, and 10× limits",
                    onClick = { vm.go(Screen.Premium) },
                )
            }
            if (me?.isStaff == true) {
                item {
                    MoreActionCard(
                        icon = Icons.Default.AdminPanelSettings,
                        title = "Control panel",
                        subtitle = "Manage users, moderation, sales, and badges",
                        onClick = { vm.go(Screen.Admin) },
                    )
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                BadgesAndSalesSection(
                    vm = vm,
                    onChanged = {
                        scope.launch {
                            runCatching {
                                vm.me = Api.service().me()
                                Prefs.user = vm.me
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MoreActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ExploreScreen(vm: AppVm, initialTag: String = "") {
    var activeTag by remember(initialTag) { mutableStateOf(initialTag.trim().trimStart('#').lowercase()) }
    var posts by remember { mutableStateOf<List<PostOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun load(tag: String = activeTag) {
        if (tag.isBlank()) {
            posts = emptyList()
            loading = false
            return
        }
        scope.launch {
            loading = true
            posts = runCatching { Api.service().postsByHashtag(tag).items }.getOrDefault(emptyList())
            loading = false
        }
    }
    LaunchedEffect(activeTag) { load(activeTag) }

    BlurScaffold(
        topBar = {
            DATopBar(
                if (activeTag.isNotBlank()) "#$activeTag" else "Hashtags",
                onBack = { vm.back() },
                actions = {
                    if (activeTag.isNotBlank()) {
                        IconButton(onClick = { load(activeTag) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (activeTag.isNotBlank() && loading && posts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad)) { LoadingBox() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = pad.calculateTopPadding() + 8.dp,
                    bottom = pad.calculateBottomPadding() + 32.dp,
                ),
            ) {
                if (activeTag.isBlank()) {
                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Browse by hashtag", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Tap a #hashtag in any post to see related posts.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else if (posts.isEmpty()) {
                    item { EmptyBox("No posts tagged #$activeTag yet.") }
                } else {
                    itemsIndexed(posts, key = { _, p -> p.id }) { i, p ->
                        Box(Modifier.staggerIn(i)) {
                            PostItem(vm, p) { updated ->
                                posts = posts.map { if (it.id == updated.id) updated else it }
                            }
                        }
                    }
                }
            }
        }
    }
}
