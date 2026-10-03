package io.github.pesterevnikita.focusgate

import com.google.gson.Gson
import io.github.pesterevnikita.focusgate.maintenance.MaintenanceArguments
import org.junit.Assert.*
import org.junit.Test

class MaintenanceArgumentsTest {
    @Test fun stringEnvelopePreservesColonsRegexQuotesAndNestedProfile() {
        val values=mapOf("value" to "https://example.com/a:b?q=\"x\"","json" to "{\"version\":1,\"host\":\"example.com\"}","regex" to "https?://example\\.com/.*")
        assertEquals(values,MaintenanceArguments.parseJson(Gson().toJson(values)))
    }
    @Test fun malformedNonStringAndDuplicateArgumentsAreRejected() {
        for(value in listOf("[]","null","{bad}","{\"x\":1}","{\"x\":true}","{\"x\":null}","{\"x\":{}}","{\"x\":[]}","{\"x\":\"one\",\"x\":\"two\"}","{} {}")) {
            assertTrue(value,runCatching{MaintenanceArguments.parseJson(value)}.isFailure)
        }
    }
    @Test fun envelopeCountsUtf8BytesAndCannotMixWithExtras() {
        assertTrue(runCatching{MaintenanceArguments.parseJson(Gson().toJson(mapOf("x" to "я".repeat(50000))))}.isFailure)
        assertEquals(mapOf("x" to "y"),MaintenanceArguments.resolve(null,mapOf("x" to "y")))
        assertEquals(mapOf("x" to "y"),MaintenanceArguments.resolve("{\"x\":\"y\"}",emptyMap()))
        assertTrue(runCatching{MaintenanceArguments.resolve("{}",mapOf("x" to "y"))}.isFailure)
    }
}
