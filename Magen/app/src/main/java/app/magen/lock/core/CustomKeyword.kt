package app.magen.lock.core

/**
 * Turns text typed or captured by the person into the page title that is stored as a custom blocked keyword.
 * Pure logic with no Android dependency, so it can be tested on the JVM.
 */
object CustomKeyword {
    /** Characters that separate the steps of a Settings path, in both reading directions. */
    private val SEPARATORS = Regex("[>›»→<‹]")

    const val MAX_LENGTH = 60

    /**
     * Accepts a path such as "Network & internet > Internet" and keeps the last non-empty part, the page itself,
     * trimmed and cut to [MAX_LENGTH] characters. Returns null when nothing usable remains.
     */
    fun parse(raw: String): String? =
        raw.split(SEPARATORS).map { it.trim() }.lastOrNull { it.isNotEmpty() }?.take(MAX_LENGTH)
}
