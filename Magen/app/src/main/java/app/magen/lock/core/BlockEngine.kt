package app.magen.lock.core

/** Why an app is blocked, in the order a reason is shown when there are several. */
enum class BlockSource { SHABBAT, PERMANENT, SCHEDULE, PERSONAL_QUOTA, GROUP_QUOTA, CUSTOM_RULE }

/** Everything the central decision needs to know about one app. */
class AppPolicy(
    /** Never blocked: Magen itself, emergency calls and system parts that must stay usable. */
    val essential: Boolean = false,
    /** The app is locked at all times (the existing lock/code mechanism, not a quota or schedule). */
    val permanent: Boolean = false,
    val schedule: BlockSchedule? = null,
    val personalQuota: QuotaDef? = null,
    val groupId: String? = null,
    /** A custom rule (for example video blocking) currently applies to this app. */
    val customRuleActive: Boolean = false,
)

class EvalContext(
    /** Local minute index (see [CivilDate.localMinuteIndex]) of the moment being decided. */
    val localMinute: Long,
    /** Day of week of that moment, 0 = Sunday. */
    val dayOfWeek: Int,
    val personalUsedMs: Long = 0L,
    val groupUsedMs: Long = 0L,
    /** Group quotas by group id. */
    val groups: Map<String, QuotaDef> = emptyMap(),
    /** True from before Shabbat or a holiday begins until after it ends (see [JewishTimes]). */
    val shabbatActive: Boolean = false,
)

class Decision(val sources: List<BlockSource>) {
    val blocked: Boolean get() = sources.isNotEmpty()

    /** The reason to show first, or null when nothing blocks. */
    val primary: BlockSource? get() = sources.firstOrNull()
}

/**
 * The single place that decides whether an app is blocked (spec section 7). Every source is judged on its own and
 * the app is blocked while ANY source blocks, so pausing or ending one source can never lift another one.
 * Shabbat and holidays win over pauses and breaks: a paused quota does not matter while it is Shabbat, because
 * that source does not depend on any quota. Essential apps are never blocked.
 */
object BlockEngine {
    fun decide(app: AppPolicy, ctx: EvalContext): Decision {
        if (app.essential) return Decision(emptyList())
        val out = ArrayList<BlockSource>(2)
        if (ctx.shabbatActive) out += BlockSource.SHABBAT
        if (app.permanent) out += BlockSource.PERMANENT
        if (app.schedule?.isBlocked(ctx.localMinute) == true) out += BlockSource.SCHEDULE
        val pq = app.personalQuota
        if (pq != null && Quotas.isExhausted(pq, ctx.personalUsedMs, ctx.dayOfWeek, ctx.localMinute)) {
            out += BlockSource.PERSONAL_QUOTA
        }
        val gq = app.groupId?.let { ctx.groups[it] }
        if (gq != null && Quotas.isExhausted(gq, ctx.groupUsedMs, ctx.dayOfWeek, ctx.localMinute)) {
            out += BlockSource.GROUP_QUOTA
        }
        if (app.customRuleActive) out += BlockSource.CUSTOM_RULE
        return Decision(out)
    }
}
