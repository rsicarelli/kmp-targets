package com.rsicarelli.kmptargets.parser

/**
 * Returns the candidate string closest to [input] by Levenshtein edit distance, or `null` if no
 * candidate is within [maxDistance] edits.
 *
 * If [input] matches a candidate exactly (case-sensitive), returns `null` — the caller already had
 * a hit and a suggestion would be noise. If [input] matches case-insensitively but with different
 * casing, the canonical-cased candidate is returned (so users learn the preferred form).
 *
 * Ties are broken by shorter candidate (a short canonical name is a better suggestion than a longer
 * one when they're equally distant).
 */
internal fun didYouMean(
    input: String,
    candidates: Collection<String>,
    maxDistance: Int = 2,
): String? {
    if (input.isEmpty() || candidates.isEmpty()) return null
    if (input in candidates) return null

    val caseInsensitive = candidates.firstOrNull { it.equals(input, ignoreCase = true) }
    if (caseInsensitive != null) return caseInsensitive

    val normalized = input.lowercase()
    var best: String? = null
    var bestDistance = Int.MAX_VALUE

    for (candidate in candidates) {
        val distance = levenshtein(normalized, candidate.lowercase())
        if (distance > maxDistance) continue
        val isBetter =
            distance < bestDistance ||
                (distance == bestDistance && best != null && candidate.length < best.length)
        if (isBetter) {
            best = candidate
            bestDistance = distance
        }
    }
    return best
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)

    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        val swap = prev
        prev = curr
        curr = swap
    }
    return prev[b.length]
}
