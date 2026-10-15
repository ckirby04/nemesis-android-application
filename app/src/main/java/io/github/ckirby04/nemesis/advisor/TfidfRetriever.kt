package io.github.ckirby04.nemesis.advisor

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Sparse TF-IDF retrieval over the principle KB, mirroring Nemesis' sklearn setup:
 * unigrams + bigrams, smoothed idf (`ln((1+n)/(1+df)) + 1`), raw term counts,
 * L2-normalised vectors, cosine similarity. The stop-word set is a short list of
 * common filler words rather than sklearn's full list; ranking over a dozen
 * principles is driven by the domain terms either way.
 */
class TfidfRetriever(private val docs: List<Principle>) {

    data class Hit(val principle: Principle, val score: Double)

    private val idf: Map<String, Double>
    private val docVectors: List<Map<String, Double>>

    init {
        val docTerms = docs.map { terms("${it.title} ${it.tags} ${it.text}") }
        val df = HashMap<String, Int>()
        for (ts in docTerms) for (t in ts.toSet()) df[t] = (df[t] ?: 0) + 1
        val n = docs.size
        idf = df.mapValues { (_, d) -> ln((1.0 + n) / (1.0 + d)) + 1.0 }
        docVectors = docTerms.map(::vectorize)
    }

    fun retrieve(query: String, k: Int = 5): List<Hit> {
        val q = vectorize(terms(query))
        return docs.indices
            .map { i -> Hit(docs[i], dot(q, docVectors[i])) }
            .sortedByDescending { it.score }
            .take(k)
    }

    /** Term counts weighted by idf, L2-normalised. Terms outside the fitted vocabulary are dropped. */
    private fun vectorize(terms: List<String>): Map<String, Double> {
        val counts = HashMap<String, Int>()
        for (t in terms) if (t in idf) counts[t] = (counts[t] ?: 0) + 1
        val weighted = counts.mapValues { (t, c) -> c * idf.getValue(t) }
        val norm = sqrt(weighted.values.sumOf { it * it })
        return if (norm == 0.0) emptyMap() else weighted.mapValues { it.value / norm }
    }

    private fun dot(a: Map<String, Double>, b: Map<String, Double>): Double {
        val (small, large) = if (a.size <= b.size) a to b else b to a
        return small.entries.sumOf { (t, w) -> w * (large[t] ?: 0.0) }
    }

    companion object {
        private val TOKEN = Regex("""\b\w\w+\b""")

        private val STOP_WORDS = setOf(
            "the", "and", "for", "are", "but", "not", "you", "your", "with", "that",
            "this", "from", "they", "their", "them", "have", "has", "was", "were",
            "into", "than", "then", "its", "can", "will", "more", "most", "when",
        )

        /** Lower-cased tokens (2+ word chars) minus stop words, followed by adjacent bigrams. */
        fun terms(text: String): List<String> {
            val words = TOKEN.findAll(text.lowercase()).map { it.value }.filter { it !in STOP_WORDS }.toList()
            return words + words.zipWithNext { a, b -> "$a $b" }
        }
    }
}
