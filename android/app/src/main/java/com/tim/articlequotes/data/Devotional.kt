package com.tim.articlequotes.data

import android.content.Context
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

data class KeyWord(val hebrew: String, val transliteration: String, val gloss: String, val note: String)

data class CrossRef(val ref: String, val why: String)

/** A short devotional on one Proverbs reading, as returned by Claude and saved on the phone. */
data class Devotional(
    val ref: String,
    val title: String,
    val ancientNearEast: String,
    val keyWords: List<KeyWord>,
    val theologyOfProverbs: String,
    val biblicalTheology: String,
    val systematicTheology: String,
    val work: String,
    val leadership: String,
    val father: String,
    val husband: String,
    val christianLife: String,
    val takeaway: String,
    val crossReferences: List<CrossRef> = emptyList(),
    val model: String,
    val createdAt: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val costUsd: Double,
    val promptVersion: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ref", ref); put("title", title); put("ancientNearEast", ancientNearEast)
        put("keyWords", JSONArray().apply {
            keyWords.forEach { put(JSONObject().put("hebrew", it.hebrew).put("transliteration", it.transliteration).put("gloss", it.gloss).put("note", it.note)) }
        })
        put("theologyOfProverbs", theologyOfProverbs); put("biblicalTheology", biblicalTheology); put("systematicTheology", systematicTheology)
        put("application", JSONObject().put("work", work).put("leadership", leadership).put("father", father).put("husband", husband).put("christianLife", christianLife))
        put("crossReferences", JSONArray().apply { crossReferences.forEach { put(JSONObject().put("ref", it.ref).put("why", it.why)) } })
        put("takeaway", takeaway); put("model", model); put("createdAt", createdAt)
        put("inputTokens", inputTokens); put("outputTokens", outputTokens); put("costUsd", costUsd); put("promptVersion", promptVersion)
    }

    companion object {
        /** Reads both a saved devotional and Claude's raw JSON (which has no metadata yet). */
        fun fromJson(o: JSONObject, ref: String = o.optString("ref")): Devotional {
            val app = o.optJSONObject("application") ?: JSONObject()
            val kw = o.optJSONArray("keyWords") ?: JSONArray()
            return Devotional(
                ref = ref, title = o.optString("title"), ancientNearEast = o.optString("ancientNearEast"),
                keyWords = List(kw.length()) { i ->
                    val k = kw.getJSONObject(i)
                    KeyWord(k.optString("hebrew"), k.optString("transliteration"), k.optString("gloss"), k.optString("note"))
                },
                theologyOfProverbs = o.optString("theologyOfProverbs"), biblicalTheology = o.optString("biblicalTheology"),
                systematicTheology = o.optString("systematicTheology"),
                work = app.optString("work"), leadership = app.optString("leadership"), father = app.optString("father"),
                husband = app.optString("husband"), christianLife = app.optString("christianLife"),
                takeaway = o.optString("takeaway"),
                crossReferences = (o.optJSONArray("crossReferences") ?: JSONArray()).let { a -> List(a.length()) { i -> a.getJSONObject(i).let { CrossRef(it.optString("ref"), it.optString("why")) } } },
                model = o.optString("model"), createdAt = o.optLong("createdAt"),
                inputTokens = o.optLong("inputTokens"), outputTokens = o.optLong("outputTokens"),
                costUsd = o.optDouble("costUsd", 0.0), promptVersion = o.optInt("promptVersion", 0),
            )
        }
    }
}

/** Saved devotionals: one file per reading, kept until Refresh. Earlier versions are kept as .prev files. */
class DevotionalStore(ctx: Context) {
    private val dir = File(ctx.applicationContext.filesDir, "devotionals").apply { mkdirs() }
    private fun f(id: String) = File(dir, "$id.json")

    fun get(id: String): Devotional? = f(id).takeIf { it.exists() }?.let { runCatching { Devotional.fromJson(JSONObject(it.readText())) }.getOrNull() }

    fun put(id: String, d: Devotional) {
        val cur = f(id)
        if (cur.exists()) cur.copyTo(File(dir, "$id.${cur.lastModified()}.prev"), overwrite = true)
        cur.writeText(d.toJson().toString(1))
    }

    fun count(): Int = dir.listFiles()?.count { it.name.endsWith(".json") } ?: 0

    /** The one-line "why it matters" for a reading's card (written before any full devotional). */
    fun significance(id: String): String? = File(dir, "$id.sig.txt").takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    fun putSignificance(id: String, text: String) = File(dir, "$id.sig.txt").writeText(text)
}

/** Writes devotionals with Claude Haiku 5.5 through the official Anthropic Java SDK. */
class DevotionalWriter(private val ctx: Context) {
    private companion object { val locks = ConcurrentHashMap<String, Mutex>() }

    private val secrets = SecretStore(ctx)
    private val store = DevotionalStore(ctx)
    private val budget = Budget(ctx)

    fun hasKey() = secrets.has(SecretStore.CLAUDE)

    suspend fun write(chunk: ProverbChunk, verseText: String): Result<Devotional> {
        val startedAt = System.currentTimeMillis()
        return locks.getOrPut(chunk.id) { Mutex() }.withLock {
            // If another screen (e.g. after a rotation) finished this reading while we waited, use it.
            store.get(chunk.id)?.takeIf { it.createdAt >= startedAt }?.let { Result.success(it) }
                ?: writeNow(chunk, verseText)
        }
    }

    private suspend fun writeNow(chunk: ProverbChunk, verseText: String): Result<Devotional> = withContext(Dispatchers.IO) {
        val key = secrets.get(SecretStore.CLAUDE)
            ?: return@withContext Result.failure(IllegalStateException("Add your Claude API key in Settings to write devotionals."))
        val client = AnthropicOkHttpClient.builder().apiKey(key).timeout(Duration.ofSeconds(90)).maxRetries(2).build()
        try {
            val params = MessageCreateParams.builder()
                .model(DevotionalPrompt.MODEL)
                .maxTokens(8000L)
                .system(DevotionalPrompt.SYSTEM)
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.MEDIUM)
                        .format(JsonOutputFormat.builder().schema(DevotionalPrompt.schema()).build())
                        .build(),
                )
                .addUserMessage("${chunk.ref} (ESV)\n\n$verseText")
                .build()
            val msg = client.messages().create(params)
            val usage = msg.usage()
            val inTok = usage.inputTokens() + (usage.cacheCreationInputTokens().orElse(0L)) + (usage.cacheReadInputTokens().orElse(0L))
            val outTok = usage.outputTokens()
            val cost = Budget.haikuCost(inTok, outTok)
            budget.record(cost)
            val stop = msg.stopReason().orElse(null)
            if (stop == StopReason.REFUSAL) return@withContext Result.failure(IllegalStateException("Claude declined to write this one. Try Refresh later."))
            if (stop == StopReason.MAX_TOKENS) return@withContext Result.failure(IllegalStateException("The devotional ran too long and was cut off. Try Refresh."))
            val text = msg.content().mapNotNull { b -> b.text().orElse(null)?.text() }.joinToString("")
            val raw = JSONObject(text)
            val d = Devotional.fromJson(raw, chunk.ref).copy(
                model = DevotionalPrompt.MODEL, createdAt = System.currentTimeMillis(),
                inputTokens = inTok, outputTokens = outTok, costUsd = cost, promptVersion = DevotionalPrompt.VERSION,
            )
            store.put(chunk.id, d)
            Result.success(d)
        } catch (e: UnauthorizedException) {
            Result.failure(IllegalStateException("The Claude API key was not accepted. Check it in Settings."))
        } catch (e: RateLimitException) {
            Result.failure(IllegalStateException("Claude is busy right now. Try again in a minute."))
        } catch (e: AnthropicServiceException) {
            Result.failure(IllegalStateException("Claude API error ${e.statusCode()}. Try again later."))
        } catch (e: AnthropicIoException) {
            Result.failure(IllegalStateException("No connection to Claude. Check your internet and try again."))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Something went wrong writing the devotional: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            client.close()
        }
    }

    /**
     * One sentence on why a reading matters, for its lock-screen card. Saved per reading, so each
     * is paid for once (about $0.0002). Returns the saved line when there is one.
     */
    suspend fun significance(chunk: ProverbChunk, verseText: String): String? = withContext(Dispatchers.IO) {
        store.significance(chunk.id)?.let { return@withContext it }
        store.get(chunk.id)?.takeaway?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val key = secrets.get(SecretStore.CLAUDE) ?: return@withContext null
        val client = AnthropicOkHttpClient.builder().apiKey(key).timeout(Duration.ofSeconds(45)).maxRetries(1).build()
        try {
            val msg = client.messages().create(
                MessageCreateParams.builder()
                    .model(DevotionalPrompt.MODEL)
                    .maxTokens(1500L)
                    .system(DevotionalPrompt.SIGNIFICANCE)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .addUserMessage("${chunk.ref} (ESV)\n\n$verseText")
                    .build(),
            )
            val u = msg.usage()
            budget.record(Budget.haikuCost(u.inputTokens(), u.outputTokens()))
            if (msg.stopReason().orElse(null) != StopReason.END_TURN) return@withContext null
            val line = msg.content().mapNotNull { b -> b.text().orElse(null)?.text() }.joinToString("").trim().trim('"')
            if (line.length < 30) return@withContext null
            store.putSignificance(chunk.id, line)
            line
        } catch (e: Exception) {
            null
        } finally {
            client.close()
        }
    }
}
