package io.github.pesterevnikita.focusgate
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import io.github.pesterevnikita.focusgate.ui.FocusGateApp
import io.github.pesterevnikita.focusgate.health.HealthWorker
import io.github.pesterevnikita.focusgate.accessibility.ServiceStatus
import kotlinx.coroutines.launch
/** Configuration host. Closing this activity must not own or stop enforcement. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); Graph.initialize(this); HealthWorker.schedule(this)
        setContent { MaterialTheme(colorScheme=if(isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) { FocusGateApp() } }
    }
    override fun onResume() {
        super.onResume()
        Graph.scope.launch { Graph.store.refresh() }
        // Reopening the visible app permits another foreground-promotion attempt
        // if Android previously refused it while we were in the background.
        ServiceStatus.retryForeground.tryEmit(Unit)
    }
}
