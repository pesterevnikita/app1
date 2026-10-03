package io.github.pesterevnikita.focusgate.accessibility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import io.github.pesterevnikita.focusgate.MainActivity
import io.github.pesterevnikita.focusgate.R
import io.github.pesterevnikita.focusgate.data.AppState

/** Extracted eligibility check; scheduled rules need priority even outside their active window. */
internal fun needsForeground(state: AppState): Boolean =
    state.policy.blockers.any { it.enabled } || state.settings.settingsMode != 0 || state.settings.recents

/**
 * Give the existing, system-bound Accessibility service foreground importance.
 * This adds no worker, wake lock or polling: Android continues delivering events.
 * OEMs may still kill it; autostart and manual Accessibility repair remain relevant.
 */
internal object EnforcementNotification {
    private const val CHANNEL = "enforcement"
    private const val ID = 1001

    /** Returns false if Android denies foreground promotion; never crash the blocker. */
    fun start(service: Service): Boolean = try {
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Blocking service", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows that FocusGate is enforcing your enabled rules." })
        val open = PendingIntent.getActivity(service, 0,
            Intent(service, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(service, CHANNEL)
            .setSmallIcon(R.drawable.ic_focusgate)
            .setContentTitle("FocusGate is active")
            .setContentText("Your enabled blockers are running. Tap to check setup and rules.")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        service.startForeground(ID, notification, type)
        true
    } catch (_: RuntimeException) {
        // A foreground-start restriction is a health issue, not permission to
        // discard policy or terminate the still-useful Accessibility service.
        false
    }

    fun stop(service: Service) { service.stopForeground(Service.STOP_FOREGROUND_REMOVE) }
}
