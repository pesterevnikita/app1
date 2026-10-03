package io.github.pesterevnikita.focusgate

import io.github.pesterevnikita.focusgate.accessibility.needsForeground
import io.github.pesterevnikita.focusgate.data.AppState
import io.github.pesterevnikita.focusgate.data.PresetFactory
import io.github.pesterevnikita.focusgate.data.ProtectionSettings
import org.junit.Assert.*
import org.junit.Test

class ForegroundRequirementTest {
    @Test fun settingsAndRecentsProtectionRemainIndependentOfOrdinaryBlockers() {
        assertTrue(needsForeground(AppState(settings=ProtectionSettings(settingsMode=1))))
        assertTrue(needsForeground(AppState(settings=ProtectionSettings(recents=true))))
    }

    @Test fun noEnforcementDoesNotKeepServiceInForeground() {
        val disabled=PresetFactory.create().let { it.copy(blockers=it.blockers.map { rule -> rule.copy(enabled=false) }) }
        assertFalse(needsForeground(AppState(policy=disabled)))
        assertFalse(needsForeground(AppState(settings=ProtectionSettings(uninstallResistance=true))))
        assertTrue(needsForeground(AppState(policy=PresetFactory.create())))
    }
}
