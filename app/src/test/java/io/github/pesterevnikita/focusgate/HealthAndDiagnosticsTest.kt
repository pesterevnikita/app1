package io.github.pesterevnikita.focusgate
import io.github.pesterevnikita.focusgate.health.SetupPreflight
import io.github.pesterevnikita.focusgate.diagnostics.LocalDiagnostics
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
class HealthAndDiagnosticsTest {
    @Test fun missingAccessibilityRefusesStartAndUnknownAutostartIsNotVerified() {
        assertNotNull(SetupPreflight.refusal(false,false,true))
        assertNotNull(SetupPreflight.refusal(true,true,false))
        assertNull(SetupPreflight.refusal(true,true,true))
    }
    @Test fun diagnosticsAreBoundedAndContainOnlyTypedEvents() {
        val dir=Files.createTempDirectory("focusgate-log-test").toFile()
        val log=LocalDiagnostics(dir,200)
        repeat(100) { log.record("DENY",123456) }
        assertTrue(log.export().isNotEmpty()); assertTrue(log.export().length<=200)
        dir.deleteRecursively()
    }
}
