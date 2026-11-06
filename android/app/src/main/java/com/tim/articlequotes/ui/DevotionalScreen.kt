package com.tim.articlequotes.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tim.articlequotes.data.Budget
import com.tim.articlequotes.data.BudgetView
import com.tim.articlequotes.data.Devotional
import com.tim.articlequotes.data.DevotionalStore
import com.tim.articlequotes.data.DevotionalWriter
import com.tim.articlequotes.data.EsvClient
import com.tim.articlequotes.data.FeedRepo
import com.tim.articlequotes.data.Prefs
import com.tim.articlequotes.data.Quote
import com.tim.articlequotes.data.Study
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Opens the right detail screen: a devotional for Proverbs, a Bible study for Wisdom, else the article summary. */
@Composable
fun DetailScreen(
    id: String, repo: FeedRepo, prefs: Prefs, onBack: () -> Unit,
    openUrl: ((String) -> Unit)? = null, onOpenSettings: (() -> Unit)? = null,
) {
    when {
        id.startsWith("prov-") -> DevotionalScreen(id, repo, onBack, onOpenSettings)
        id.startsWith("w-") -> StudyScreen(id, repo, prefs, onBack, onOpenSettings)
        else -> ArticleScreen(id, repo, prefs, onBack, openUrl)
    }
}

/**
 * A Proverbs reading with its devotional. The first open writes the devotional with Claude Haiku
 * and saves it with the reading; later opens show the saved one for free until Refresh.
 */
@Composable
fun DevotionalScreen(id: String, repo: FeedRepo, onBack: () -> Unit, onOpenSettings: (() -> Unit)?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val chunk = remember(id) { repo.proverbsPlan().firstOrNull { it.id == id } }
    val store = remember { DevotionalStore(ctx) }
    val writer = remember { DevotionalWriter(ctx) }
    var verse by remember { mutableStateOf<String?>(null) }
    var dev by remember { mutableStateOf(store.get(id)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmRefresh by remember { mutableStateOf(false) }
    var budget by remember { mutableStateOf<BudgetView?>(null) }

    fun generate() {
        val c = chunk ?: return
        val v = verse ?: return
        busy = true; error = ""
        scope.launch {
            writer.write(c, v).onSuccess { dev = it }.onFailure { error = it.message ?: "Couldn't write the devotional." }
            busy = false
            budget = Budget(ctx).view()
        }
    }

    LaunchedEffect(id) {
        val c = chunk ?: return@LaunchedEffect
        EsvClient(ctx).text(c).onSuccess { verse = it }.onFailure { error = it.message ?: "Couldn't load the verse." }
        budget = Budget(ctx).view()
        // First open writes it automatically (if there's a key); after that it's saved.
        if (dev == null && verse != null && writer.hasKey()) generate()
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Proverbs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            val d = dev
            if (d != null && !busy) {
                IconButton(onClick = { confirmRefresh = true }) { Icon(Icons.Default.Refresh, "Write a new devotional") }
                IconButton(onClick = { shareDevotional(ctx, chunk?.ref.orEmpty(), verse.orEmpty(), d) }) { Icon(Icons.Default.Share, "Share devotional") }
            }
        }
        budget?.let { BudgetMeter(it, Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) }
        if (chunk == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text("This reading isn't in the plan yet. Update the feed in Settings.") }
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(chunk.ref, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            val v = verse
            if (v != null) {
                Text("“$v”", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium)
                Text("ESV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            val d = dev
            when {
                busy -> Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Writing your devotional… this takes about 10–20 seconds.", style = MaterialTheme.typography.bodyMedium)
                }
                d != null -> DevotionalBody(d)
                !writer.hasKey() -> Card(Modifier.padding(top = 20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Add your Claude API key to write devotionals", style = MaterialTheme.typography.titleSmall)
                        Text("Settings → Claude API. Each devotional costs well under a tenth of a cent and is saved with the reading.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        if (onOpenSettings != null) TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                }
                else -> Button(onClick = { generate() }, enabled = verse != null, modifier = Modifier.padding(top = 20.dp)) { Text("Write devotional") }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            if (d != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    buildString {
                        if (d != null) {
                            append("Written by Claude Haiku on ").append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(d.createdAt)))
                            append(" · cost ").append(Budget.money(d.costUsd))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmRefresh) AlertDialog(
        onDismissRequest = { confirmRefresh = false },
        title = { Text("Write a new devotional?") },
        text = { Text("This replaces the saved one (the old one is kept on the phone) and costs about a twentieth of a cent.") },
        confirmButton = { TextButton(onClick = { confirmRefresh = false; generate() }) { Text("Refresh") } },
        dismissButton = { TextButton(onClick = { confirmRefresh = false }) { Text("Cancel") } },
    )
}

/**
 * A Bible study on the theme of a Wisdom quote. Opening it (the "Bible study" button) writes it
 * with Claude Haiku and saves it with the quote; later opens are free until Refresh.
 */
@Composable
fun StudyScreen(id: String, repo: FeedRepo, prefs: Prefs, onBack: () -> Unit, onOpenSettings: (() -> Unit)?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val quote: Quote? = remember(id) {
        repo.wisdom().firstOrNull { it.id == id }
            ?: prefs.favorites.firstOrNull { it.id == id }
            ?: prefs.history.firstOrNull { it.id == id }
    }
    val store = remember { DevotionalStore(ctx) }
    val writer = remember { DevotionalWriter(ctx) }
    var study by remember { mutableStateOf(store.getStudy(id)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmRefresh by remember { mutableStateOf(false) }
    var budget by remember { mutableStateOf<BudgetView?>(null) }

    fun generate() {
        val q = quote ?: return
        busy = true; error = ""
        scope.launch {
            writer.writeStudy(q).onSuccess { study = it }.onFailure { error = it.message ?: "Couldn't write the Bible study." }
            busy = false
            budget = Budget(ctx).view()
        }
    }

    LaunchedEffect(id) {
        budget = Budget(ctx).view()
        if (study == null && quote != null && writer.hasKey()) generate()
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Bible study", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            val s = study
            if (s != null && !busy && quote != null) {
                IconButton(onClick = { confirmRefresh = true }) { Icon(Icons.Default.Refresh, "Write a new Bible study") }
                IconButton(onClick = { shareStudy(ctx, quote, s) }) { Icon(Icons.Default.Share, "Share Bible study") }
            }
        }
        budget?.let { BudgetMeter(it, Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) }
        if (quote == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text("This quote isn't in the feed any more. Update the feed in Settings.") }
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("“${quote.text}”", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(quote.author, quote.title).filter { it.isNotBlank() }.joinToString(", "),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
            val s = study
            when {
                busy -> Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Writing your Bible study… this takes about 10–20 seconds.", style = MaterialTheme.typography.bodyMedium)
                }
                s != null -> StudyBody(s)
                !writer.hasKey() -> Card(Modifier.padding(top = 20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Add your Claude API key to write Bible studies", style = MaterialTheme.typography.titleSmall)
                        Text("Settings → Claude API. Each study costs well under a tenth of a cent and is saved with the quote.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        if (onOpenSettings != null) TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                }
                else -> Button(onClick = { generate() }, modifier = Modifier.padding(top = 20.dp)) { Text("Write Bible study") }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            if (s != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Written by Claude Haiku on " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(s.createdAt)) + " · cost " + Budget.money(s.costUsd),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmRefresh) AlertDialog(
        onDismissRequest = { confirmRefresh = false },
        title = { Text("Write a new Bible study?") },
        text = { Text("This replaces the saved one (the old one is kept on the phone) and costs about a twentieth of a cent.") },
        confirmButton = { TextButton(onClick = { confirmRefresh = false; generate() }) { Text("Refresh") } },
        dismissButton = { TextButton(onClick = { confirmRefresh = false }) { Text("Cancel") } },
    )
}

@Composable
private fun StudyBody(s: Study) {
    Text(s.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp))
    if (s.theme.isNotBlank()) Text(s.theme, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    if (s.takeaway.isNotBlank()) Text(s.takeaway, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
    Part("The quote in its setting", s.quoteInContext)
    Part("What Scripture says", s.whatScriptureSays)
    if (s.keyPassages.isNotEmpty()) CrossRefs(s.keyPassages)
    if (s.keyWords.isNotEmpty()) {
        Header("Key Hebrew and Greek words")
        s.keyWords.forEach { k ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text("${k.hebrew}  ${k.transliteration} — ${k.gloss}", style = MaterialTheme.typography.titleSmall)
                Text(k.note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Part("Where Scripture agrees", s.agreement)
    Part("Where Scripture goes further", s.difference)
    Part("Across the Bible", s.biblicalTheology)
    Part("Doctrine", s.systematicTheology)
    Header("For your life")
    listOf("At work" to s.work, "As a leader" to s.leadership, "As a father" to s.father, "As a husband" to s.husband, "As a Christian" to s.christianLife)
        .filter { it.second.isNotBlank() }
        .forEach { (label, text) ->
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    if (s.questions.isNotEmpty()) {
        Header("To think about")
        s.questions.forEachIndexed { i, q -> Text("${i + 1}. $q", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 3.dp)) }
    }
}

private fun shareStudy(ctx: android.content.Context, q: Quote, s: Study) {
    val text = buildString {
        append("“").append(q.text).append("” — ").append(q.author).append("\n\n")
        append(s.title).append("\n").append(s.takeaway).append("\n\n")
        append("The quote in its setting: ").append(s.quoteInContext).append("\n\n")
        append("What Scripture says: ").append(s.whatScriptureSays).append("\n")
        s.keyPassages.forEach { append("• ").append(it.ref).append(" — ").append(it.why).append("\n") }
        append("\nWhere Scripture agrees: ").append(s.agreement)
        append("\n\nWhere Scripture goes further: ").append(s.difference)
        append("\n\nAcross the Bible: ").append(s.biblicalTheology).append("\n\nDoctrine: ").append(s.systematicTheology)
        append("\n\nAt work: ").append(s.work).append("\nAs a leader: ").append(s.leadership).append("\nAs a father: ").append(s.father)
        append("\nAs a husband: ").append(s.husband).append("\nAs a Christian: ").append(s.christianLife)
        if (s.questions.isNotEmpty()) { append("\n\nTo think about:\n"); s.questions.forEachIndexed { i, x -> append(i + 1).append(". ").append(x).append("\n") } }
    }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share Bible study").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun DevotionalBody(d: Devotional) {
    Text(d.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp))
    if (d.takeaway.isNotBlank()) Text(d.takeaway, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
    Part("Ancient Near Eastern context", d.ancientNearEast)
    if (d.keyWords.isNotEmpty()) {
        Header("Key Hebrew words")
        d.keyWords.forEach { k ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text("${k.hebrew}  ${k.transliteration} — ${k.gloss}", style = MaterialTheme.typography.titleSmall)
                Text(k.note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Part("The theology of Proverbs", d.theologyOfProverbs)
    Part("Across the Bible", d.biblicalTheology)
    if (d.crossReferences.isNotEmpty()) CrossRefs(d.crossReferences)
    Part("Doctrine", d.systematicTheology)
    Header("For your life")
    listOf("At work" to d.work, "As a leader" to d.leadership, "As a father" to d.father, "As a husband" to d.husband, "As a Christian" to d.christianLife)
        .filter { it.second.isNotBlank() }
        .forEach { (label, text) ->
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
}

/** Cross references printed in full from the ESV (fetched with the ESV key, never from memory). */
@Composable
private fun CrossRefs(refs: List<com.tim.articlequotes.data.CrossRef>) {
    val ctx = LocalContext.current
    val texts = androidx.compose.runtime.produceState(initialValue = emptyMap<String, String>(), refs) {
        val esv = EsvClient(ctx)
        val out = LinkedHashMap<String, String>()
        for (r in refs) esv.textForRef(r.ref).onSuccess { out[r.ref] = it }
        value = out
    }.value
    refs.forEach { r ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp)) {
                Text(r.ref, style = MaterialTheme.typography.titleSmall)
                val t = texts[r.ref]
                if (t != null) Text("“$t” (ESV)", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
                if (r.why.isNotBlank()) Text(r.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun Header(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

@Composable
private fun Part(title: String, body: String) {
    if (body.isBlank()) return
    Header(title)
    Text(body, style = MaterialTheme.typography.bodyLarge)
}

private fun shareDevotional(ctx: android.content.Context, ref: String, verse: String, d: Devotional) {
    val text = buildString {
        append(ref).append(" (ESV)\n“").append(verse).append("”\n\n")
        append(d.title).append("\n").append(d.takeaway).append("\n\n")
        append("Ancient Near East: ").append(d.ancientNearEast).append("\n\n")
        d.keyWords.forEach { append(it.hebrew).append(" (").append(it.transliteration).append("): ").append(it.gloss).append(". ").append(it.note).append("\n") }
        append("\nTheology of Proverbs: ").append(d.theologyOfProverbs).append("\n\nAcross the Bible: ").append(d.biblicalTheology)
        append("\n\nDoctrine: ").append(d.systematicTheology)
        append("\n\nAt work: ").append(d.work).append("\nAs a leader: ").append(d.leadership).append("\nAs a father: ").append(d.father)
        append("\nAs a husband: ").append(d.husband).append("\nAs a Christian: ").append(d.christianLife)
    }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share devotional").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** "Claude API: $198.40 left of $200" with a bar showing what's left; turns red under 10%. */
@Composable
fun BudgetMeter(v: BudgetView, modifier: Modifier = Modifier) {
    val leftFrac = if (v.allowance > 0) (v.left / v.allowance).toFloat().coerceIn(0f, 1f) else 0f
    val low = leftFrac < 0.10f
    val color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Claude API budget", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                "${Budget.money(v.left)} left of ${Budget.money(v.allowance)}",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color,
            )
        }
        LinearProgressIndicator(
            progress = { leftFrac }, color = color,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp),
        )
    }
}
