package app.magen.lock

/**
 * Pure policy calculation shared by Device Owner enforcement and local unit tests.
 * Multiple Settings screens can map to one Android restriction; it must remain enabled
 * while at least one screen using that restriction is selected.
 */
internal object PolicyRules {
    fun groupedRestrictionStates(
        settings: List<Pair<String, String?>>,
        enabledIds: Set<String>,
    ): Map<String, Boolean> {
        return settings
            .mapNotNull { (id, restriction) -> restriction?.let { id to it } }
            .groupBy({ it.second }, { it.first })
            .mapValues { (_, ids) -> ids.any(enabledIds::contains) }
    }
}
