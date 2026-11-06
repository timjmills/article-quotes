package com.tim.articlequotes.data

import org.json.JSONObject

/** One quotable line, with enough context to render a card without loading the article. */
data class Quote(
    val id: String,          // "<articleId>:<index>"
    val articleId: String,
    val text: String,
    val category: String,
    val title: String,
    val author: String,
    val date: String,        // YYYY-MM-DD
    /** "Why it matters": the article's point this quote serves. May be empty for old feeds. */
    val context: String = "",
    /** "article" (from the archive), "wisdom" (curated quotes) or "proverb" (a Proverbs reading). */
    val kind: String = KIND_ARTICLE,
) {
    val isArticle get() = kind == KIND_ARTICLE
    val isProverb get() = kind == KIND_PROVERB
    val isWisdom get() = kind == KIND_WISDOM
    /** What the card's detail button opens: the article, the Proverbs devotional, or a Wisdom Bible study. */
    val detailId: String get() = if (isWisdom) id else articleId
    val detailLabel: String get() = when { isProverb -> "Devotional"; isWisdom -> "Bible study"; else -> "Read summary" }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("aid", articleId).put("t", text).put("c", category)
        .put("ti", title).put("a", author).put("d", date).put("x", context).put("k", kind)

    companion object {
        fun fromJson(o: JSONObject): Quote = Quote(
            id = o.getString("id"), articleId = o.getString("aid"), text = o.getString("t"),
            category = o.optString("c"), title = o.optString("ti"), author = o.optString("a"), date = o.optString("d"),
            context = o.optString("x"), kind = o.optString("k", KIND_ARTICLE).ifBlank { KIND_ARTICLE },
        )

        const val KIND_ARTICLE = "article"
        const val KIND_WISDOM = "wisdom"
        const val KIND_PROVERB = "proverb"
    }
}

/** Wisdom groups shipped in wisdom.json, in display order. */
object WisdomGroups {
    val ALL = listOf("Stoics", "Leadership", "Christian", "Statesmen")
    fun blurb(g: String) = when (g) {
        "Stoics" -> "Marcus Aurelius, Seneca, Epictetus"
        "Leadership" -> "John Maxwell, Brian Tracy"
        "Christian" -> "Augustine, Calvin, Luther, Spurgeon, Lewis and others"
        "Statesmen" -> "Lincoln, Roosevelt, Washington, Churchill, Drucker and others"
        else -> ""
    }
}

data class ArticleSummary(
    val id: String,
    val title: String,
    val author: String,
    val category: String,
    val date: String,
    val quoteCount: Int,
)

data class ArticleDetail(
    val id: String,
    val title: String,
    val author: String,
    val source: String,
    val date: String,
    val dateDisplay: String,
    val category: String,
    val url: String,
    val summary: String,
    val points: List<String>,
    val quotes: List<String>,
    val contexts: List<String> = emptyList(),
) {
    fun contextFor(i: Int): String = contexts.getOrNull(i) ?: ""

    companion object {
        fun fromJson(o: JSONObject): ArticleDetail {
            fun arr(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return List(a.length()) { a.getString(it) }
            }
            return ArticleDetail(
                id = o.getString("id"), title = o.optString("title"), author = o.optString("author"),
                source = o.optString("source"), date = o.optString("date"), dateDisplay = o.optString("dateDisplay"),
                category = o.optString("category"), url = o.optString("url"), summary = o.optString("summary"),
                points = arr("points"), quotes = arr("quotes"), contexts = arr("context"),
            )
        }
    }
}

object Categories {
    val ALL = listOf(
        "Leadership",
        "Family",
        "Classical Education",
        "Education - Practice & Culture",
        "Education - Learning & Curriculum",
        "Education - Ed-Tech",
    )

    fun short(c: String): String = when (c) {
        "Education - Practice & Culture" -> "Practice & Culture"
        "Education - Learning & Curriculum" -> "Learning & Curriculum"
        "Education - Ed-Tech" -> "Ed-Tech"
        else -> c
    }

    fun blurb(c: String): String = when (c) {
        "Leadership" -> "Leading people, feedback, character, communication"
        "Family" -> "Marriage, fatherhood, parenting, manhood"
        "Classical Education" -> "What education is for; the classical tradition"
        "Education - Practice & Culture" -> "Classroom craft, behaviour, school culture, the profession"
        "Education - Learning & Curriculum" -> "How people learn; pedagogy, curriculum, assessment"
        "Education - Ed-Tech" -> "AI and technology in a school context"
        else -> ""
    }
}
