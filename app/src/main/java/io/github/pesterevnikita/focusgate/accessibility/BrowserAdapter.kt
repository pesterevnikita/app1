package io.github.pesterevnikita.focusgate.accessibility
import io.github.pesterevnikita.focusgate.policy.UrlMatcher
object BrowserAdapter {
    val browsers=setOf("com.android.chrome","com.microsoft.emmx")
    fun resolve(packageName: String, viewId: String?, text: String?, editing: Boolean, visible: Boolean = true, stableWindow: Boolean = true): String? {
        if(editing || !visible || !stableWindow || packageName !in browsers || viewId !in setOf("$packageName:id/url_bar","$packageName:id/url_bar_text")) return null
        return UrlMatcher.normalized(text)
    }
}
enum class SettingsScreen { SENSITIVE, WIFI, MOBILE, OTHER, UNKNOWN }
object SystemProtection {
    fun shouldBlockSettings(mode: Int, screen: SettingsScreen, networkExceptions: Boolean): Boolean =
        mode>0 && !(networkExceptions && screen in setOf(SettingsScreen.WIFI,SettingsScreen.MOBILE)) && (mode==2 || screen==SettingsScreen.SENSITIVE)
}
