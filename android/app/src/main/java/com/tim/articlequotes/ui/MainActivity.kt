package com.tim.articlequotes.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tim.articlequotes.BuildConfig
import com.tim.articlequotes.Notifications
import com.tim.articlequotes.data.ArticleDetail
import com.tim.articlequotes.data.ArticleSummary
import com.tim.articlequotes.data.Categories
import com.tim.articlequotes.data.FeedRepo
import com.tim.articlequotes.data.Prefs
import com.tim.articlequotes.data.Quote
import com.tim.articlequotes.work.Rotator
import com.tim.articlequotes.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ARTICLE = "articleId"
        val pendingArticle = mutableStateOf<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingArticle.value = intent?.getStringExtra(EXTRA_ARTICLE)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingArticle.value = intent.getStringExtra(EXTRA_ARTICLE)
    }
}

// ---------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------

private val Scheme = darkColorScheme(
    primary = Color(0xFFE0B04A), onPrimary = Color(0xFF1B1400),
    primaryContainer = Color(0xFF3A2F12), onPrimaryContainer = Color(0xFFF7E3B0),
    secondary = Color(0xFFB9B3A6), onSecondary = Color(0xFF1B1F27),
    secondaryContainer = Color(0xFF2C3140), onSecondaryContainer = Color(0xFFE6E1D6),
    background = Color(0xFF121417), onBackground = Color(0xFFF4F1EA),
    surface = Color(0xFF1B1F27), onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFF262B35), onSurfaceVariant = Color(0xFFB9B3A6),
    outline = Color(0xFF4A505C),
)

private fun scaledTypography(s: Float): Typography {
    val b = Typography()
    fun TextStyle.sc() = copy(fontSize = fontSize * s, lineHeight = if (lineHeight.isSpecified) lineHeight * s else lineHeight)
    return Typography(
        displayLarge = b.displayLarge.sc(), displayMedium = b.displayMedium.sc(), displaySmall = b.displaySmall.sc(),
        headlineLarge = b.headlineLarge.sc(), headlineMedium = b.headlineMedium.sc(), headlineSmall = b.headlineSmall.sc(),
        titleLarge = b.titleLarge.sc(), titleMedium = b.titleMedium.sc(), titleSmall = b.titleSmall.sc(),
        bodyLarge = b.bodyLarge.sc(), bodyMedium = b.bodyMedium.sc(), bodySmall = b.bodySmall.sc(),
        labelLarge = b.labelLarge.sc(), labelMedium = b.labelMedium.sc(), labelSmall = b.labelSmall.sc(),
    )
}

// ---------------------------------------------------------------------------
// Root
// ---------------------------------------------------------------------------

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val repo = remember { FeedRepo(ctx, prefs) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openArticle by rememberSaveable { mutableStateOf<String?>(null) }
    var textScale by remember { mutableFloatStateOf(prefs.textScale) }

    val pending = MainActivity.pendingArticle.value
    LaunchedEffect(pending) {
        if (pending != null) { openArticle = pending; MainActivity.pendingArticle.value = null }
    }

    MaterialTheme(colorScheme = Scheme, typography = remember(textScale) { scaledTypography(textScale) }) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val article = openArticle
            if (article != null) {
                BackHandler { openArticle = null }
                ArticleScreen(article, repo, prefs, onBack = { openArticle = null })
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Today") })
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.List, null) }, label = { Text("Browse") })
                            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Favorite, null) }, label = { Text("Saved") })
                            NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                        }
                    },
                ) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (tab) {
                            0 -> TodayScreen(prefs, repo, onOpen = { openArticle = it })
                            1 -> BrowseScreen(prefs, repo, onOpen = { openArticle = it })
                            2 -> SavedScreen(prefs, onOpen = { openArticle = it })
                            else -> SettingsScreen(prefs, repo, onTextScale = { textScale = it })
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Today
// ---------------------------------------------------------------------------

@Composable
fun TodayScreen(prefs: Prefs, repo: FeedRepo, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var quote by remember { mutableStateOf(prefs.currentQuote) }
    var fav by remember { mutableStateOf(quote?.let { prefs.isFavorite(it.id) } ?: false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var onboarded by remember { mutableStateOf(prefs.onboarded) }
    var hasData by remember { mutableStateOf(repo.hasData()) }
    // Position in the history of shown quotes; swiping moves through it.
    var histIndex by remember { mutableIntStateOf(prefs.historyIndex) }
    var histSize by remember { mutableIntStateOf(prefs.history.size) }

    LifecycleResumeEffect(Unit) {
        quote = prefs.currentQuote
        fav = quote?.let { prefs.isFavorite(it.id) } ?: false
        hasData = repo.hasData()
        histIndex = prefs.historyIndex; histSize = prefs.history.size
        onPauseOrDispose { }
    }

    var wallpaperJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun applyWallpaperSoon(q: Quote) {
        wallpaperJob?.cancel()
        wallpaperJob = scope.launch {
            kotlinx.coroutines.delay(900)
            withContext(Dispatchers.IO) { Rotator.applyWallpaper(ctx, prefs, q) }
        }
    }

    fun newQuote() {
        busy = true; status = ""
        scope.launch {
            val q = Rotator.rotate(ctx, notify = false, respectQuietHours = false)
            quote = q; fav = q?.let { prefs.isFavorite(it.id) } ?: false
            hasData = repo.hasData()
            histIndex = prefs.historyIndex; histSize = prefs.history.size
            if (q == null) status = "No quotes yet. Connect to Wi-Fi and tap Download."
            busy = false
        }
    }

    fun goTo(index: Int) {
        val q = Rotator.showFromHistory(ctx, index) ?: return
        quote = q; fav = prefs.isFavorite(q.id); histIndex = index
        applyWallpaperSoon(q)
    }

    fun previous() { if (histIndex > 0) goTo(histIndex - 1) }
    fun next() { if (histIndex < histSize - 1) goTo(histIndex + 1) else if (!busy) newQuote() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Article Quotes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (hasData) prefs.lastSyncMessage.ifBlank { "Quotes from your article archive" } else "Quotes from your article archive",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (!onboarded) {
            OnboardingCard(prefs, repo, onDone = { onboarded = true; newQuote() })
            Spacer(Modifier.height(16.dp))
        }

        val q = quote
        if (q == null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp)) {
                    Text(if (hasData) "Ready for your first quote." else "Download your quotes to get started.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { newQuote() }, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (hasData) "Show a quote" else "Download and show a quote")
                    }
                    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            val style = prefs.cardStyle; val ts = prefs.textScale; val showCtx = prefs.showContext
            val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, q.id, style, ts, showCtx) {
                value = withContext(Dispatchers.Default) { QuoteCardRenderer.preview(q, style, ts, 720, showCtx).asImageBitmap() }
            }
            val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f / 1.6f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .clickable { onOpen(q.articleId) }
                    .pointerInput(histIndex, histSize, busy) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag <= -swipeThreshold) next() else if (drag >= swipeThreshold) previous()
                            },
                            onHorizontalDrag = { change, amount -> drag += amount; change.consume() },
                        )
                    },
            ) {
                bmp?.let { Image(it, contentDescription = "Current quote: ${q.text}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { previous() }, enabled = histIndex > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous quote") }
                Text(
                    if (histSize > 0) "${histIndex + 1} of $histSize · swipe for more" else "Swipe for more",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                IconButton(onClick = { next() }, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, if (histIndex < histSize - 1) "Next quote" else "New quote") }
            }
            if (q.context.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Why it matters", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(q.context, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { newQuote() }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("New quote") }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onOpen(q.articleId) }) { Text("Read summary") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { fav = prefs.toggleFavorite(q) }) {
                    Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Save", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { share(ctx, q) }) { Icon(Icons.Default.Share, "Share") }
            }
            Spacer(Modifier.height(8.dp))
            val mode = prefs.wallpaperMode
            Text(
                buildString {
                    append("A new quote every ${intervalLabel(prefs.intervalMinutes).lowercase()}")
                    if (prefs.quietEnabled) append(", quiet ${prefs.quietStartHour}:00–${prefs.quietEndHour}:00")
                    append(". Lock screen: ${if (mode == "off") "off" else "on"}.")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardingCard(prefs: Prefs, repo: FeedRepo, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var notifOk by remember { mutableStateOf(Notifications.canPost(ctx)) }
    var wallpaper by remember { mutableStateOf(prefs.wallpaperMode != "off") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text("Welcome", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "Quotes from your Leadership, Family and Education articles will appear on your lock screen and as a gentle notification. Tap one to read the summary.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1. Notifications", Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (notifOk) Text("Allowed", color = MaterialTheme.colorScheme.onPrimaryContainer)
                else TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else notifOk = true
                }) { Text("Allow") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("2. Put quotes on my lock screen", Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Switch(wallpaper, onCheckedChange = { wallpaper = it; prefs.wallpaperMode = if (it) "lock" else "off" })
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                busy = true; msg = "Downloading your quotes…"
                scope.launch {
                    val r = repo.sync()
                    msg = if (r.ok) "" else r.message
                    busy = false
                    if (r.ok) { prefs.onboarded = true; onDone() }
                }
            }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("3. Get my first quote")
            }
            if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Browse
// ---------------------------------------------------------------------------

@Composable
fun BrowseScreen(prefs: Prefs, repo: FeedRepo, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(prefs.categories) }
    val all = remember(selected) { repo.articles(selected) }
    val list = remember(all, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) all else all.filter { it.title.lowercase().contains(q) || it.author.lowercase().contains(q) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search titles and authors") }, singleLine = true,
        )
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Categories.ALL) { c ->
                FilterChip(selected = c in selected, onClick = { selected = if (c in selected) selected - c else selected + c }, label = { Text(Categories.short(c)) })
            }
        }
        Text("${list.size} articles", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { a -> ArticleRow(a) { onOpen(a.id) } }
        }
    }
}

@Composable
private fun ArticleRow(a: ArticleSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        Text("${a.author} · ${a.date} · ${Categories.short(a.category)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

// ---------------------------------------------------------------------------
// Saved
// ---------------------------------------------------------------------------

@Composable
fun SavedScreen(prefs: Prefs, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var favs by remember { mutableStateOf(prefs.favorites) }
    LifecycleResumeEffect(Unit) { favs = prefs.favorites; onPauseOrDispose { } }
    if (favs.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Nothing saved yet. Tap the heart on a quote to keep it here.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(favs, key = { it.id }) { q ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    QuoteText(q.text)
                    if (q.context.isNotBlank()) Text(q.context, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Text("— ${q.author} · ${q.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onOpen(q.articleId) }) { Text("Read summary") }
                        TextButton(onClick = { scope.launch { Rotator.show(ctx, q, notify = false) } }) { Icon(Icons.Default.Lock, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Lock screen") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { prefs.toggleFavorite(q); favs = prefs.favorites }) { Icon(Icons.Default.Favorite, "Remove", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteText(text: String) {
    val base = when {
        text.length <= 120 -> 22.sp
        text.length <= 220 -> 19.sp
        text.length <= 350 -> 17.sp
        else -> 16.sp
    }
    val scale = MaterialTheme.typography.bodyLarge.fontSize.value / 16f
    Text("“$text”", fontFamily = FontFamily.Serif, fontSize = base * scale, lineHeight = base * scale * 1.35f)
}

// ---------------------------------------------------------------------------
// Article
// ---------------------------------------------------------------------------

@Composable
fun ArticleScreen(id: String, repo: FeedRepo, prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<ArticleDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var favTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(id) { loading = true; detail = repo.article(id); loading = false }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(detail?.let { Categories.short(it.category) } ?: "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            detail?.let { d -> IconButton(onClick = { shareArticle(ctx, d) }) { Icon(Icons.Default.Share, "Share") } }
        }
        val d = detail
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (d == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Couldn't load this summary. It downloads on first open, so check your connection and try again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text(d.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("${d.author} · ${d.dateDisplay}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                if (d.source.isNotBlank()) Text(d.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (d.url.isNotBlank()) {
                    Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url))) }) { Text("Open full article") }
                }
                SectionHeader("Summary")
                Text(d.summary, style = MaterialTheme.typography.bodyLarge)
                if (d.points.isNotEmpty()) {
                    SectionHeader("High-impact points")
                    d.points.forEach { p ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text("•  ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                            Text(p, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (d.quotes.isNotEmpty()) {
                    SectionHeader("Notable quotes")
                    d.quotes.forEachIndexed { i, text ->
                        val q = Quote("${d.id}:$i", d.id, text, d.category, d.title, d.author, d.date, d.contextFor(i))
                        val isFav = remember(favTick) { prefs.isFavorite(q.id) }
                        Card(Modifier.padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("“$text”", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyLarge)
                                if (q.context.isNotBlank()) Text(q.context, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { scope.launch { Rotator.show(ctx, q, notify = false) } }) { Icon(Icons.Default.Lock, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Lock screen") }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { prefs.toggleFavorite(q); favTick++ }) {
                                        Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Save", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { share(ctx, q) }) { Icon(Icons.Default.Share, "Share") }
                                }
                            }
                        }
                    }
                }
                if (d.url.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url))) }, Modifier.fillMaxWidth()) { Text("Open full article") }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

private val INTERVALS = listOf(60, 120, 180, 240, 360, 720, 1440)

fun intervalLabel(m: Int): String = when {
    m < 60 -> "$m min"
    m == 60 -> "Hour"
    m < 1440 -> "${m / 60} hours"
    else -> "Day"
}

@Composable
fun SettingsScreen(prefs: Prefs, repo: FeedRepo, onTextScale: (Float) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var interval by remember { mutableIntStateOf(prefs.intervalMinutes) }
    var quietOn by remember { mutableStateOf(prefs.quietEnabled) }
    var quietStart by remember { mutableIntStateOf(prefs.quietStartHour) }
    var quietEnd by remember { mutableIntStateOf(prefs.quietEndHour) }
    var wallpaper by remember { mutableStateOf(prefs.wallpaperMode) }
    var notifs by remember { mutableStateOf(prefs.notificationsOn) }
    var style by remember { mutableStateOf(prefs.cardStyle) }
    var cats by remember { mutableStateOf(prefs.categories) }
    var scale by remember { mutableFloatStateOf(prefs.textScale) }
    var maxChars by remember { mutableIntStateOf(prefs.maxWallpaperChars) }
    var showContext by remember { mutableStateOf(prefs.showContext) }
    var unmetered by remember { mutableStateOf(prefs.unmeteredOnly) }
    var feedUrl by remember { mutableStateOf(prefs.feedUrl) }
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf(if (prefs.lastSync > 0) "Last updated ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(prefs.lastSync))} · ${prefs.lastSyncMessage}" else "Not downloaded yet") }

    fun reapplyWallpaper() {
        val q = prefs.currentQuote ?: return
        scope.launch { withContext(Dispatchers.IO) { Rotator.applyWallpaper(ctx, prefs, q) } }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        SectionHeader("How often")
        Text("A new quote appears…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(INTERVALS, interval, label = { "Every " + intervalLabel(it).lowercase() }) { interval = it; prefs.intervalMinutes = it; Scheduler.reschedule(ctx) }

        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Quiet hours", style = MaterialTheme.typography.bodyLarge)
                Text("No new quotes or notifications overnight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(quietOn, { quietOn = it; prefs.quietEnabled = it })
        }
        if (quietOn) {
            Text("From ${quietStart}:00 to ${quietEnd}:00", style = MaterialTheme.typography.bodyMedium)
            Slider(quietStart.toFloat(), { quietStart = it.toInt(); prefs.quietStartHour = quietStart }, valueRange = 0f..23f, steps = 22)
            Slider(quietEnd.toFloat(), { quietEnd = it.toInt(); prefs.quietEndHour = quietEnd }, valueRange = 0f..23f, steps = 22)
        }

        SectionHeader("Where quotes appear")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notification", style = MaterialTheme.typography.bodyLarge)
                Text("Shows on the lock screen; tap to read the summary", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(notifs, { notifs = it; prefs.notificationsOn = it })
        }
        Text("Wallpaper", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        ChipRow(listOf("off", "lock", "both"), wallpaper, label = { when (it) { "off" -> "Off"; "lock" -> "Lock screen"; else -> "Lock + home" } }) { wallpaper = it; prefs.wallpaperMode = it; reapplyWallpaper() }
        Text("Card style", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        ChipRow(listOf("rotate", "navy", "paper", "forest", "plum"), style, label = { it.replaceFirstChar { c -> c.uppercase() } }) { style = it; prefs.cardStyle = it; reapplyWallpaper() }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show \"why it matters\" on the card", style = MaterialTheme.typography.bodyLarge)
                Text("One line of context from the article under each quote", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(showContext, { showContext = it; prefs.showContext = it; reapplyWallpaper() })
        }
        Text("Longest quote on the lock screen: $maxChars characters", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Slider(maxChars.toFloat(), { maxChars = (it / 20).toInt() * 20 }, onValueChangeFinished = { prefs.maxWallpaperChars = maxChars }, valueRange = 120f..600f)

        SectionHeader("Article types")
        Text("Switch off any type you don't want quotes from.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Categories.ALL.forEach { c ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Categories.short(c), style = MaterialTheme.typography.bodyLarge)
                    Text(Categories.blurb(c), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(c in cats, { on ->
                    val n = if (on) cats + c else cats - c
                    if (n.isNotEmpty()) { cats = n; prefs.categories = n }
                })
            }
        }

        SectionHeader("Text size")
        Text("Applies to the app and the lock-screen card.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(scale, { scale = it }, onValueChangeFinished = { prefs.textScale = scale; onTextScale(scale); reapplyWallpaper() }, valueRange = 0.8f..1.6f)
        Text("“The best leaders listen first.”", fontFamily = FontFamily.Serif, fontSize = 20.sp * scale, lineHeight = 26.sp * scale)

        SectionHeader("Updates")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Download only on Wi-Fi", style = MaterialTheme.typography.bodyLarge)
                Text("New articles are checked once a day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(unmetered, { unmetered = it; prefs.unmeteredOnly = it; Scheduler.reschedule(ctx) })
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                syncing = true
                scope.launch { val r = repo.sync(); syncMsg = if (r.ok) "Updated just now · ${r.message}" else r.message; syncing = false }
            }, enabled = !syncing) { if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Update now") }
            Spacer(Modifier.width(12.dp))
            Text(syncMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(feedUrl, { feedUrl = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Feed address (advanced)") }, singleLine = true)
        if (feedUrl.trim().trimEnd('/') != prefs.feedUrl.trimEnd('/')) {
            TextButton(onClick = { prefs.feedUrl = feedUrl; feedUrl = prefs.feedUrl }) { Text("Save address") }
        }
        Spacer(Modifier.height(24.dp))
        Text("Article Quotes ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        items(options) { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun share(ctx: android.content.Context, q: Quote) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "“${q.text}”\n— ${q.author}, ${q.title}")
    }
    ctx.startActivity(Intent.createChooser(i, "Share quote"))
}

private fun shareArticle(ctx: android.content.Context, d: ArticleDetail) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, d.title)
        putExtra(Intent.EXTRA_TEXT, buildString {
            append(d.title).append("\n").append(d.author).append("\n\n").append(d.summary)
            if (d.url.isNotBlank()) append("\n\n").append(d.url)
        })
    }
    ctx.startActivity(Intent.createChooser(i, "Share summary"))
}
