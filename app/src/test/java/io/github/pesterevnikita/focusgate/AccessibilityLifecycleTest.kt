package io.github.pesterevnikita.focusgate

import io.github.pesterevnikita.focusgate.accessibility.FocusGateAccessibilityService
import io.github.pesterevnikita.focusgate.accessibility.ServiceStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityLifecycleTest {
    @Test fun interruptingFeedbackDoesNotDisconnectEnforcement() {
        // Android interrupts feedback without unbinding the service. A running
        // blocker must still report connected rather than demand another grant.
        val service = FocusGateAccessibilityService()
        ServiceStatus.connected.value = true
        try {
            service.onInterrupt()
            assertTrue(ServiceStatus.connected.value)
        } finally {
            ServiceStatus.connected.value = false
        }
    }
}
