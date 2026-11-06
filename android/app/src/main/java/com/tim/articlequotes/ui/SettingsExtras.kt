package com.tim.articlequotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tim.articlequotes.data.Budget
import com.tim.articlequotes.data.BudgetView
import com.tim.articlequotes.data.DevotionalStore
import com.tim.articlequotes.data.EsvClient
import com.tim.articlequotes.data.FeedRepo
import com.tim.articlequotes.data.Prefs
import com.tim.articlequotes.data.SecretStore
import com.tim.articlequotes.data.WisdomGroups
import kotlinx.coroutines.launch

private val FREQ = listOf("Off", "Rarely", "Sometimes", "Often")

@Composable
private fun Title(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

@Composable
private fun Hint(t: String) {
    Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        items(options) { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

/** Wisdom and Proverbs sections: on/off and how often they come up. */
@Composable
fun SectionsSettings(prefs: Prefs, repo: FeedRepo) {
    var wFreq by remember { mutableIntStateOf(prefs.wisdomFreq) }
    var pFreq by remember { mutableIntStateOf(prefs.proverbFreq) }
    var groups by remember { mutableStateOf(prefs.wisdomGroups) }
    var order by remember { mutableStateOf(prefs.proverbOrder) }
    val wisdomCount = remember { repo.wisdom().groupingBy { it.category }.eachCount() }
    val planSize = remember { repo.proverbsPlan().size }

    Title("Wisdom")
    Hint("Quotes from the Stoics, John Maxwell and Brian Tracy, classic Christian writers, and statesmen, each checked against a source.")
    Chips((0..3).toList(), wFreq, { FREQ[it] }) { wFreq = it; prefs.wisdomFreq = it }
    if (wFreq > 0) WisdomGroups.ALL.forEach { g ->
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$g${wisdomCount[g]?.let { " ($it)" } ?: ""}", style = MaterialTheme.typography.bodyLarge)
                Hint(WisdomGroups.blurb(g))
            }
            Switch(g in groups, { on ->
                val n = if (on) groups + g else groups - g
                if (n.isNotEmpty()) { groups = n; prefs.wisdomGroups = n }
            })
        }
    }

    Title("Proverbs")
    Hint("Works through all of Proverbs (ESV), one to three verses at a time. Tap a reading for a short devotional.")
    Chips((0..3).toList(), pFreq, { FREQ[it] }) { pFreq = it; prefs.proverbFreq = it }
    if (pFreq > 0) {
        Chips(listOf("order", "random"), order, { if (it == "order") "In order" else "Random" }) { order = it; prefs.proverbOrder = it }
        if (order == "order" && planSize > 0) {
            val i = prefs.proverbIndex % planSize
            val next = repo.proverbsPlan()[i].ref
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hint("Next: $next · reading ${i + 1} of $planSize")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { prefs.proverbIndex = 0 }) { Text("Start over") }
            }
        }
    }
}

/** API keys (Claude, ESV, optional Admin) and the monthly budget view. */
@Composable
fun ApiSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val secrets = remember { SecretStore(ctx) }
    val budget = remember { Budget(ctx) }
    var view by remember { mutableStateOf<BudgetView?>(null) }
    var loading by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var allowance by remember { mutableStateOf(budget.allowance.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }) }
    var resetDay by remember { mutableIntStateOf(budget.resetDay) }

    LaunchedEffect(tick) { loading = true; view = budget.view(); loading = false }

    Title("Claude API and Bible text")
    Hint("Keys are stored encrypted on this phone only and never backed up or shared.")
    KeyField("Claude API key", "From console.anthropic.com. Used to write devotionals and Bible studies.", SecretStore.CLAUDE, secrets) { tick++ }
    KeyField("ESV API key", "Free from api.esv.org (create a personal app). Used for the Proverbs text.", SecretStore.ESV, secrets) { tick++ }
    KeyField("Admin API key (optional)", "Only for the whole account's spending below. It can manage your organization, so add it only if you want that.", SecretStore.ADMIN, secrets) { tick++ }

    Title("API budget")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            val v = view
            if (v == null) {
                Hint(if (loading) "Checking…" else "")
            } else {
                Text("${Budget.money(v.left)} left of ${Budget.money(v.allowance)}", style = MaterialTheme.typography.titleLarge)
                val frac = if (v.allowance > 0) (v.spent / v.allowance).toFloat().coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                if (v.orgSpent != null) Text("Whole account this month: ${Budget.money(v.orgSpent)}", style = MaterialTheme.typography.bodyMedium)
                Text("This app this month: ${Budget.money(v.appSpent)}", style = MaterialTheme.typography.bodyMedium)
                Hint("Since ${v.periodStart}. " + when {
                    v.orgError != null -> v.orgError
                    v.orgSpent == null -> "Add an Admin API key above to include everything else you use the API for."
                    else -> "Account figures can lag by a few minutes."
                })
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    allowance, { allowance = it.filter { c -> c.isDigit() || c == '.' } },
                    Modifier.width(140.dp), label = { Text("Monthly $") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { allowance.toDoubleOrNull()?.let { budget.allowance = it; tick++ } }) { Text("Save") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { tick++ }) { Text("Refresh") }
            }
            Text("Resets on day $resetDay of the month", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Chips(listOf(1, 5, 10, 15, 20, 25), resetDay, { "$it" }) { resetDay = it; budget.resetDay = it; tick++ }
        }
    }
    val saved = remember(tick) { DevotionalStore(ctx).count() }
    val studies = remember(tick) { DevotionalStore(ctx).studyCount() }
    val esvVerses = remember(tick) { EsvClient(ctx).cachedVerseCount() }
    Hint("Saved devotionals: $saved · Bible studies: $studies · ESV verses on this phone: $esvVerses of ${EsvClient.MAX_VERSES} allowed")
}

@Composable
private fun KeyField(label: String, help: String, name: String, secrets: SecretStore, onChange: () -> Unit) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf(secrets.hint(name)) }
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Hint(if (hint.isNotBlank()) "Saved ($hint)" else help)
            }
            if (!editing) TextButton(onClick = { editing = true; value = "" }) { Text(if (hint.isBlank()) "Add" else "Change") }
            if (!editing && hint.isNotBlank()) TextButton(onClick = { secrets.put(name, null); hint = ""; onChange() }) { Text("Remove") }
        }
        if (editing) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value, { value = it.trim() }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Paste the key") }, visualTransformation = PasswordVisualTransformation(),
            )
            TextButton(onClick = {
                if (secrets.put(name, value)) { hint = secrets.hint(name); editing = false; onChange() }
                else Toast.makeText(ctx, "The phone couldn't store the key securely. Try again.", Toast.LENGTH_LONG).show()
            }, enabled = value.length > 8) { Text("Save") }
            TextButton(onClick = { editing = false }) { Text("Cancel") }
        }
    }
}
