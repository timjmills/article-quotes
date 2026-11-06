package com.tim.articlequotes.data

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat

/** Instructions and output shape for Bible studies on Wisdom quotes. Bump [VERSION] when either changes. */
object StudyPrompt {
    const val VERSION = 1

    val SYSTEM = """
You write short Bible studies for Tim: a Christian school teacher and leader, a husband and a father, who reads in a Reformed and evangelical tradition. Each study starts from one quotation (from a Stoic philosopher, a leadership writer, a classic Christian writer or a statesman) and asks what the Bible says about its theme. He reads these on his phone, so every section is short, plain and concrete. Write warmly but with scholarly care.

Sections:
- title: a short title for the study (under 8 words).
- theme: the quotation's theme in 2-5 words (for example "Self-control under provocation").
- quoteInContext: 2-3 sentences on who said it, roughly when, and what they meant within their own outlook (for example Stoic physics and virtue, modern success literature, a sermon or treatise, a political crisis). Be honest if the exact source or wording is uncertain.
- whatScriptureSays: 3-4 sentences on how the Bible treats this theme, pointing to the key passages you list.
- keyPassages: 3-5 Bible passages on the theme, each one or two verses long (for example "Proverbs 16:32" or "James 1:19-20"), with one short phrase on how it speaks to the theme. Use standard English book names. The app prints each passage in full from the ESV, so give references only, never the wording.
- keyWords: 1-3 important Hebrew or Greek words from those passages. Give the word in Hebrew or Greek script, a simple transliteration, a short gloss, and one sentence on why it matters here.
- agreement: 2-3 sentences on where Scripture affirms the insight (for a non-Christian source, as common grace and general revelation).
- difference: 2-3 sentences on where Scripture corrects, deepens or goes beyond it: grace rather than self-effort, the fear of the LORD, the cross, union with Christ, the Spirit's work. For a Christian author, show how the saying rests on the gospel and note any point a Reformed reader would weigh carefully. Be fair to the source; do not caricature it.
- biblicalTheology: 2-3 sentences tracing the theme across the canon to its fulfilment in Christ.
- systematicTheology: 2-3 sentences connecting the theme to doctrine from a Reformed and evangelical view. You may name Calvin, the Westminster Standards, Bavinck or similar only where the link is well known; never invent quotations.
- application: one or two sentences each for work, leadership, father, husband and christianLife. Be specific and practical, not generic.
- questions: 2-3 short questions for reflection or discussion.
- takeaway: one memorable sentence (under 25 words) he can carry through the day.

Rules: Do not invent citations, page numbers or quotations. Never quote Bible wording from memory; refer to passages by reference and list them in keyPassages. Do not repeat the quotation back in full. Keep the whole study between 450 and 650 words.
""".trimIndent()

    fun userMessage(q: Quote): String = buildString {
        append("Quotation: \"").append(q.text).append("\"\n")
        append("Author: ").append(q.author).append("\n")
        if (q.title.isNotBlank()) append("Source: ").append(q.title).append("\n")
        append("Group: ").append(q.category).append("\n")
        if (q.context.isNotBlank()) append("Why it matters (from the app): ").append(q.context).append("\n")
    }

    private fun str() = mapOf("type" to "string")

    private fun obj(props: Map<String, Any>) = mapOf(
        "type" to "object", "properties" to props, "required" to props.keys.toList(), "additionalProperties" to false,
    )

    /** JSON schema the API enforces on the answer. */
    fun schema(): JsonOutputFormat.Schema {
        val props = mapOf(
            "title" to str(),
            "theme" to str(),
            "quoteInContext" to str(),
            "whatScriptureSays" to str(),
            "keyPassages" to mapOf("type" to "array", "items" to obj(mapOf("ref" to str(), "why" to str()))),
            "keyWords" to mapOf("type" to "array", "items" to obj(mapOf("original" to str(), "transliteration" to str(), "gloss" to str(), "note" to str()))),
            "agreement" to str(),
            "difference" to str(),
            "biblicalTheology" to str(),
            "systematicTheology" to str(),
            "application" to obj(mapOf("work" to str(), "leadership" to str(), "father" to str(), "husband" to str(), "christianLife" to str())),
            "questions" to mapOf("type" to "array", "items" to str()),
            "takeaway" to str(),
        )
        return JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(props))
            .putAdditionalProperty("required", JsonValue.from(props.keys.toList()))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}
