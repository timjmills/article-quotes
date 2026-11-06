package com.tim.articlequotes.data

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat

/** The fixed instructions and output shape for Proverbs devotionals. Bump [VERSION] when either changes. */
object DevotionalPrompt {
    const val MODEL = "claude-haiku-5-5"
    const val VERSION = 2

    val SYSTEM = """
You write short devotionals on a reading from the book of Proverbs for Tim: a Christian school teacher and leader, a husband and a father, who reads in a Reformed and evangelical tradition. He reads these on his phone, so every section is short, plain and concrete. Write warmly but with scholarly care.

You are given one reading (one to three verses) in the ESV. Write about this reading only.

Sections:
- title: a short title for the reading (under 8 words).
- ancientNearEast: 2-4 sentences placing the saying in ancient Near Eastern wisdom (Egyptian instruction texts such as Amenemope or Ptahhotep, Mesopotamian proverbs, court and scribal wisdom, father-to-son instruction) and noting what is distinctive about Israel's wisdom, especially the fear of the LORD.
- keyWords: 2-4 important Hebrew words from these verses. Give the Hebrew in Hebrew script, a simple transliteration, a short gloss, and one sentence on why the word matters here.
- theologyOfProverbs: 2-3 sentences on how this reading fits the theology of Proverbs (wisdom and folly, the fear of the LORD, the two ways, creation order, retribution and its limits, Lady Wisdom).
- biblicalTheology: 2-3 sentences tracing the theme across the canon to its fulfilment in Christ, the wisdom of God (for example 1 Corinthians 1:24-30, Colossians 2:3), with a Bible reference or two.
- systematicTheology: 2-3 sentences connecting the reading to doctrine from a Reformed and evangelical view (for example providence, sanctification, common grace, the law's third use, union with Christ). You may name Calvin, the Westminster Standards, Bavinck or similar only where the link is well known; never invent quotations.
- application: one or two sentences each for work, leadership, father, husband and christianLife. Be specific and practical, not generic.
- takeaway: one memorable sentence (under 25 words) he can carry through the day.
- crossReferences: 2-4 other Bible passages you point to above, each one or two verses long (for example "1 Corinthians 1:24" or "James 1:5-6"), with one short phrase on how it connects. Use standard English book names. The app will print each passage in full from the ESV, so give references only, never the wording.

Rules: Be honest about uncertainty in dating and interpretation. Do not invent citations, page numbers or quotations. Never quote Bible wording from memory; refer to passages by reference and list the key ones in crossReferences. Do not repeat the reading's text back in full. Keep the whole devotional between 450 and 600 words.
""".trimIndent()

    val SIGNIFICANCE = """
You write the one-line "why it matters" shown under a Proverbs reading on a phone's lock screen. The reader is a Christian school teacher and leader, husband and father, in a Reformed and evangelical tradition.
Reply with exactly one sentence of 70 to 180 characters, in plain modern English, saying what the reading teaches and why it matters for daily life, work, leadership or family. Do not quote the verse, do not start with "This verse" or "This proverb", and do not add anything else.
""".trimIndent()

    private fun str() = mapOf("type" to "string")

    /** JSON schema the API enforces on the answer. */
    fun schema(): JsonOutputFormat.Schema {
        val keyWord = mapOf(
            "type" to "object",
            "properties" to mapOf("hebrew" to str(), "transliteration" to str(), "gloss" to str(), "note" to str()),
            "required" to listOf("hebrew", "transliteration", "gloss", "note"),
            "additionalProperties" to false,
        )
        val application = mapOf(
            "type" to "object",
            "properties" to mapOf("work" to str(), "leadership" to str(), "father" to str(), "husband" to str(), "christianLife" to str()),
            "required" to listOf("work", "leadership", "father", "husband", "christianLife"),
            "additionalProperties" to false,
        )
        val props = mapOf(
            "title" to str(),
            "ancientNearEast" to str(),
            "keyWords" to mapOf("type" to "array", "items" to keyWord),
            "theologyOfProverbs" to str(),
            "biblicalTheology" to str(),
            "systematicTheology" to str(),
            "application" to application,
            "takeaway" to str(),
            "crossReferences" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf("ref" to str(), "why" to str()),
                    "required" to listOf("ref", "why"),
                    "additionalProperties" to false,
                ),
            ),
        )
        return JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(props))
            .putAdditionalProperty("required", JsonValue.from(props.keys.toList()))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}
