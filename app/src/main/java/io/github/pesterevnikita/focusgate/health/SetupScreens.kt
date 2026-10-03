package io.github.pesterevnikita.focusgate.health

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Open setup pages only; grants and OEM switches remain the user's choice. */
object SetupScreens {
    private fun appDetails(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")
    )

    /** OEM activity names vary. Fall back without assuming a page exists or a grant succeeded. */
    private fun open(context: Context, preferred: Intent) {
        try { context.startActivity(preferred) }
        catch (_: RuntimeException) { context.startActivity(appDetails(context)) }
    }

    fun autostart(context: Context) {
        val xiaomi = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
        open(context, if (xiaomi) Intent().setComponent(ComponentName(
            "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )) else appDetails(context))
    }

    fun battery(context: Context) {
        val xiaomi = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
        open(context, if (xiaomi) Intent().setComponent(ComponentName(
            "com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
        )).putExtra("package_name", context.packageName)
            .putExtra("package_label", "FocusGate") else appDetails(context))
    }
}
