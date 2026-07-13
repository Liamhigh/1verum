package com.verumomnis.forensic.engine.contradiction

import kotlin.math.sqrt

/**
 * Semantic Analyzer — v5.2.9.
 * Deterministic word embeddings + negation detection.
 * No external ML dependencies. Same input = same output, always.
 */
object SemanticAnalyzer {

    private val STOP_WORDS = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been",
        "being", "have", "has", "had", "do", "does", "did", "will", "would",
        "could", "should", "may", "might", "must", "shall", "can", "need",
        "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
        "and", "but", "if", "or", "because", "not", "no", "this", "that"
    )
    private const val EMBEDDING_DIM = 100

    private val NEGATORS = setOf(
        "no", "not", "never", "none", "nobody", "nothing", "neither",
        "nowhere", "hardly", "scarcely", "barely", "deny", "denies",
        "denied", "refuse", "refuses", "rejected", "false", "incorrect",
        "wrong", "without", "lacks", "missing", "absent"
    )

    private val OPPOSITES = listOf(
        "exists" to "does not exist",
        "has" to "does not have",
        "true" to "false",
        "yes" to "no",
        "agreed" to "denied",
        "paid" to "unpaid",
        "valid" to "invalid",
        "signed" to "unsigned",
        "binding" to "non-binding",
        "accepted" to "rejected"
    )

    /** Tokenize text deterministically. */
    private fun tokenize(text: String): List<String> {
        val cleaned = text.lowercase().replace(Regex("[^\\w\\s]"), " ")
        return cleaned.split(Regex("\\s+")).filter { it.length > 2 && it !in STOP_WORDS }
    }

    /** Deterministic word embedding — character hashing, no randomness. */
    private fun embed(text: String, cache: MutableMap<String, DoubleArray> = mutableMapOf()): DoubleArray {
        cache[text]?.let { return it.copyOf() }
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return DoubleArray(EMBEDDING_DIM) { 0.0 }

        val embedding = DoubleArray(EMBEDDING_DIM) { 0.0 }
        for (token in tokens) {
            for (i in token.indices) {
                val idx = (token[i].code + i * 31) % EMBEDDING_DIM
                embedding[idx] += 1.0
            }
        }
        val norm = sqrt(embedding.sumOf { it * it })
        if (norm > 0) {
            for (i in 0 until EMBEDDING_DIM) embedding[i] /= norm
        }
        cache[text] = embedding.copyOf()
        return embedding
    }

    /** Cosine similarity between two text strings — deterministic. */
    fun cosineSimilarity(textA: String, textB: String): Double {
        val cache = mutableMapOf<String, DoubleArray>()
        val embA = embed(textA, cache)
        val embB = embed(textB, cache)
        var dot = 0.0
        for (i in 0 until EMBEDDING_DIM) dot += embA[i] * embB[i]
        return dot
    }

    /** Detect negation between two text strings. */
    fun negationScore(textA: String, textB: String): Double {
        var score = 0.0
        val aHasNeg = NEGATORS.any { textA.lowercase().contains(it) }
        val bHasNeg = NEGATORS.any { textB.lowercase().contains(it) }
        if (aHasNeg != bHasNeg) score += 0.4

        for ((pos, neg) in OPPOSITES) {
            if ((textA.lowercase().contains(pos) && textB.lowercase().contains(neg)) ||
                (textA.lowercase().contains(neg) && textB.lowercase().contains(pos))
            ) {
                score += 0.6
            }
        }
        return score.coerceAtMost(1.0)
    }

    /**
     * Detect semantic contradiction between two claims.
     * Returns Pair(isContradiction, confidenceScore).
     */
    fun detectSemanticContradiction(claimA: EngineClaim, claimB: EngineClaim): Pair<Boolean, Double> {
        val textA = claimA.value.lowercase()
        val textB = claimB.value.lowercase()
        val similarity = cosineSimilarity(textA, textB)
        val neg = negationScore(textA, textB)
        val sameSubject = claimA.subject == claimB.subject

        return when {
            sameSubject && similarity < 0.3 && neg > 0.5 -> true to 0.8
            sameSubject && similarity < 0.5 && neg > 0.3 -> true to 0.6
            neg > 0.7 -> true to 0.5
            else -> false to 0.0
        }
    }
}
