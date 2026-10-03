package io.github.pesterevnikita.focusgate.runtime
import io.github.pesterevnikita.focusgate.policy.*
/** Connect pure policy/accounting to the Android Home action; the callback is replaceable in tests. */
class EnforcementController(initial: LedgerState = LedgerState(), private val home: () -> Boolean) {
    val ledger=UsageLedger(initial)
    private var lastHomeMillis: Long?=null
    /** Charge the old foreground interval first, then select future billing and rate-limit repeated kicks. */
    fun evaluate(policy: PolicySnapshot, observation: Observation, clock: ClockSnapshot): Decision {
        val usage=ledger.snapshot(policy.groups,clock)
        val decision=PolicyEngine.evaluate(policy,usage,observation,clock)
        ledger.transition(decision.billableGroupIds,policy.groups,clock)
        if(decision.denied && (lastHomeMillis==null || clock.elapsedMillis-lastHomeMillis!!>=750)) { lastHomeMillis=clock.elapsedMillis; home() }
        return decision
    }
}
