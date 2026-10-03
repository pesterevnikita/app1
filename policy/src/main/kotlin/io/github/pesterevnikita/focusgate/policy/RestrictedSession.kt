package io.github.pesterevnikita.focusgate.policy
enum class ReleasePolicy { PASSWORD, TIMER, PASSWORD_OR_TIMER, PASSWORD_AND_TIMER }
data class LockedSession(val id: String, val releasePolicy: ReleasePolicy, val deadlineUtcMillis: Long?, val deadlineElapsedMillis: Long?, val bootId: String, val zoneId: String, val allowRestrictiveAdditions: Boolean = true)
object RestrictedSession {
    fun canRelease(mode: ReleasePolicy, password: Boolean, expired: Boolean): Boolean = when(mode) {
        ReleasePolicy.PASSWORD -> password; ReleasePolicy.TIMER -> expired
        ReleasePolicy.PASSWORD_OR_TIMER -> password || expired; ReleasePolicy.PASSWORD_AND_TIMER -> password && expired
    }
    fun expired(session: LockedSession, clock: ClockSnapshot): Boolean = if (session.deadlineUtcMillis == null) false
        else if (session.bootId == clock.bootId && session.deadlineElapsedMillis != null) clock.elapsedMillis >= session.deadlineElapsedMillis else clock.instant.toEpochMilli() >= session.deadlineUtcMillis
}
object MutationGuard {
    fun isRestrictive(old: PolicySnapshot, new: PolicySnapshot): Boolean {
        if (!old.blockers.all { before ->
                val after = new.blockers.find { it.id == before.id } ?: return@all false
                before.copy(targets=after.targets) == after && after.targets.containsAll(before.targets)
            }) return false
        if (!new.blockers.filter { rule -> old.blockers.none { it.id == rule.id } }.all { it.enabled }) return false
        return old.groups.all { before ->
            val after = new.groups.find { it.id == before.id } ?: return@all false
            before.period == after.period && after.allowanceMillis <= before.allowanceMillis && after.requiredBreakMillis >= before.requiredBreakMillis &&
                (before.continuousCapMillis == null || (after.continuousCapMillis != null && after.continuousCapMillis <= before.continuousCapMillis))
        }
    }
}
