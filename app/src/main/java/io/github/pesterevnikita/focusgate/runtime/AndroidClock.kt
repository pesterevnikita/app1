package io.github.pesterevnikita.focusgate.runtime
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.github.pesterevnikita.focusgate.policy.ClockSnapshot
import java.time.*
/** Pair wall time with monotonic uptime and a boot identifier so duration calculations resist clock edits. */
class AndroidClock(private val context: Context) {
    fun now(zoneId: String = ZoneId.systemDefault().id) = ClockSnapshot(Instant.now(),SystemClock.elapsedRealtime(),Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,0).toString(),zoneId)
}
