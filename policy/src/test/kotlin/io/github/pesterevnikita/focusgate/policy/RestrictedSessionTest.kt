package io.github.pesterevnikita.focusgate.policy
import org.junit.Assert.*
import org.junit.Test
class RestrictedSessionTest {
    @Test fun releaseTruthTable() {
        for (mode in ReleasePolicy.entries) for (password in listOf(false,true)) for (expired in listOf(false,true)) {
            val expected = when(mode) { ReleasePolicy.PASSWORD -> password; ReleasePolicy.TIMER -> expired; ReleasePolicy.PASSWORD_OR_TIMER -> password || expired; ReleasePolicy.PASSWORD_AND_TIMER -> password && expired }
            assertEquals("$mode $password $expired", expected, RestrictedSession.canRelease(mode,password,expired))
        }
    }
    @Test fun earlyAndPasswordDoesNotRelease() { assertFalse(RestrictedSession.canRelease(ReleasePolicy.PASSWORD_AND_TIMER,true,false)) }
    @Test fun addingTargetsPreservesLockedRule() {
        val old = Blocker("a","A",targets=listOf(Target.App("one")))
        assertTrue(MutationGuard.isRestrictive(PolicySnapshot(0,listOf(old)),PolicySnapshot(1,listOf(old.copy(targets=old.targets+Target.App("two"))))))
        assertFalse(MutationGuard.isRestrictive(PolicySnapshot(0,listOf(old)),PolicySnapshot(1,listOf(old.copy(enabled=false)))))
        assertFalse(MutationGuard.isRestrictive(PolicySnapshot(0,listOf(old)),PolicySnapshot(1)))
    }
    @Test fun quotaCannotBeResetByReplacingGroup() {
        val old = PolicySnapshot(0,listOf(Blocker("a","A",targets=listOf(Target.App("one")),quotaGroupId="g")),listOf(QuotaGroup("g",QuotaPeriod.HOUR,900000)))
        assertFalse(MutationGuard.isRestrictive(old, old.copy(blockers=old.blockers.map{it.copy(quotaGroupId="new")}, groups=listOf(QuotaGroup("new",QuotaPeriod.HOUR,900000)))))
        assertTrue(MutationGuard.isRestrictive(old,old.copy(groups=old.groups.map{it.copy(allowanceMillis=600000)})))
    }
    @Test fun capCannotBeWeakenedOrBreakShortened() {
        val old = PolicySnapshot(groups=listOf(QuotaGroup("g",QuotaPeriod.HOUR,900000,600000,300000)))
        assertFalse(MutationGuard.isRestrictive(old,old.copy(groups=old.groups.map{it.copy(continuousCapMillis=null)})))
        assertFalse(MutationGuard.isRestrictive(old,old.copy(groups=old.groups.map{it.copy(requiredBreakMillis=100000)})))
    }
}
