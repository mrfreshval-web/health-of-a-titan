package com.healthofatitan.app.domain.checkin

/**
 * Plain codecs for storage. Choices are joined with the unit separator (U+001F), so the names of
 * the person's own stack items may hold commas. The S5 text is encrypted by the repository right
 * after [encodePrivate] and is never logged.
 */
object Codec {
    private const val SEP = '\u001F'

    fun choices(set: Set<String>): String = set.sorted().joinToString(SEP.toString())

    fun choices(text: String?): Set<String> = text?.split(SEP)?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    fun encodePrivate(p: PrivateAnswer): String =
        listOf(p.intimacy?.name.orEmpty(), p.pulls.map { it.name }.sorted().joinToString(","), if (p.wantLess) "1" else "0").joinToString(SEP.toString())

    fun decodePrivate(text: String): PrivateAnswer {
        val parts = text.split(SEP)
        return PrivateAnswer(
            intimacy = parts.getOrNull(0)?.let { n -> Intimacy.entries.firstOrNull { it.name == n } },
            pulls = parts.getOrNull(1).orEmpty().split(',').mapNotNull { n -> Pull.entries.firstOrNull { it.name == n } }.toSet(),
            wantLess = parts.getOrNull(2) == "1",
        )
    }
}
