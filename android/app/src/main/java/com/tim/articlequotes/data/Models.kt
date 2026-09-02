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
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("aid", articleId).put("t", text).put("c", category)
        .put("ti", title).put("a", author).put("d", date)

    companion object {
        fun fromJson(o: JSONObject): Quote = Quote(
            id = o.getString("id"), articleId = o.getString("aid"), text = o.getString("t"),
            category = o.optString("c"), title = o.optString("ti"), author = o.optString("a"), date = o.optString("d"),
        )
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
) {
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
                points = arr("points"), quotes = arr("quotes"),
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
