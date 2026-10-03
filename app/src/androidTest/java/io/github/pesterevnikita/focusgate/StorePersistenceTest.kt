package io.github.pesterevnikita.focusgate
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pesterevnikita.focusgate.data.*
import io.github.pesterevnikita.focusgate.policy.*
import io.github.pesterevnikita.focusgate.policy.Target
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class StorePersistenceTest {
    // Explicit Unit keeps the generated JVM method void, as JUnit requires.
    // deleteDatabase returns Boolean and must not become the test's return type.
    @Test fun reopenPreservesEditablePolicyAndRejectsStaleRevision() = runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-${System.nanoTime()}.db"
        val store=AppStore(context,name); store.load()
        val policy=PolicySnapshot(blockers=listOf(Blocker("custom","Editable",false,listOf(Target.App("example.app")))))
        assertNull(store.updatePolicy(policy,0))
        assertNotNull(store.updatePolicy(policy,0))
        store.close()
        val reopened=AppStore(context,name); reopened.load()
        assertEquals("Editable",reopened.state.value.policy.blockers.single().name)
        assertFalse(reopened.state.value.policy.blockers.single().enabled)
        reopened.close(); context.deleteDatabase(name)
    }
}
