package app.magen.lock.core

/** One app's own rules. All parts are optional: an app with none of them has no rule of its own. */
class AppRule(
    val schedule: BlockSchedule? = null,
    val quota: QuotaDef? = null,
    /** The quota group the app belongs to, or null. An app is in at most one group. */
    val groupId: String? = null,
) {
    val isEmpty: Boolean get() = (schedule == null || schedule.isEmpty) && quota == null && groupId == null
}

class GroupRule(val name: String, val quota: QuotaDef)

/** Shabbat and holiday blocking (spec section 8). */
class ShabbatConfig(
    val enabled: Boolean = false,
    val settings: JewishTimes.Settings = JewishTimes.Settings(),
    /** Latitude and longitude chosen or detected; null until a place is set, which keeps the feature unverified. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Minutes before the window starts to warn. */
    val warnMin: Int = 30,
) {
    val hasPlace: Boolean get() = latitude != null && longitude != null
}

/**
 * Everything the person configures about when apps are blocked, as one immutable value. Kept separate from the
 * code, recovery and uninstall keys, which it never touches. Saved and restored through [RuleCodec].
 */
class RuleConfig(
    val apps: Map<String, AppRule> = emptyMap(),
    val groups: Map<String, GroupRule> = emptyMap(),
    val shabbat: ShabbatConfig = ShabbatConfig(),
) {
    fun withApp(pkg: String, rule: AppRule): RuleConfig =
        RuleConfig(if (rule.isEmpty) apps - pkg else apps + (pkg to rule), groups, shabbat)

    fun withGroup(id: String, g: GroupRule): RuleConfig = RuleConfig(apps, groups + (id to g), shabbat)

    /** Deleting a group also takes its members out of it (their own quota and schedule stay). */
    fun withoutGroup(id: String): RuleConfig {
        val kept = apps.mapValues { (_, r) -> if (r.groupId == id) AppRule(r.schedule, r.quota, null) else r }
            .filterValues { !it.isEmpty }
        return RuleConfig(kept, groups - id, shabbat)
    }

    fun withShabbat(s: ShabbatConfig): RuleConfig = RuleConfig(apps, groups, s)

    /** Moves [pkg] to [groupId] (or out of every group when null). Earlier usage of the old group is kept by the ledger. */
    fun withGroupMembership(pkg: String, groupId: String?): RuleConfig {
        val old = apps[pkg] ?: AppRule()
        return withApp(pkg, AppRule(old.schedule, old.quota, groupId?.takeIf { it in groups }))
    }

    fun policyFor(pkg: String, essential: Boolean, permanent: Boolean, customRuleActive: Boolean): AppPolicy {
        val r = apps[pkg]
        return AppPolicy(essential, permanent, r?.schedule, r?.quota, r?.groupId, customRuleActive)
    }

    val groupDefs: Map<String, QuotaDef> get() = groups.mapValues { it.value.quota }
}
