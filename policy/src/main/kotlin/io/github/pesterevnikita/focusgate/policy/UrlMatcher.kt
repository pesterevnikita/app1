package io.github.pesterevnikita.focusgate.policy
import java.net.URI
import java.net.IDN
import java.util.Locale
import com.google.re2j.Pattern

object UrlMatcher {
    fun normalized(visibleUrl: String?): String? = runCatching {
        val raw = visibleUrl?.trim()?.takeIf { it.isNotEmpty() && it.length <= 8192 } ?: return null
        val uri = URI(if (raw.contains("://")) raw else "https://$raw")
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        val host = uri.host ?: return null
        URI(uri.scheme.lowercase(Locale.ROOT), null, IDN.toASCII(host).lowercase(Locale.ROOT), uri.port, uri.path.ifEmpty { "/" }, uri.query, null).toASCIIString()
    }.getOrNull()
    fun matches(target: Target, visibleUrl: String?): Boolean {
        val url = normalized(visibleUrl) ?: return false
        return runCatching {
            when (target.kind) {
                "host" -> {
                    val host = URI(url).host.lowercase(Locale.ROOT).trimEnd('.')
                    val domain = IDN.toASCII(target.value).lowercase(Locale.ROOT).trimEnd('.')
                    host == domain || (target.includeSubdomains && host.endsWith(".$domain"))
                }
                "regex" -> target.value.length <= 1024 && Pattern.compile(target.value).matcher(url).matches()
                else -> false
            }
        }.getOrDefault(false)
    }
    fun validPattern(pattern: String): Boolean = pattern.length in 1..1024 && runCatching { Pattern.compile(pattern) }.isSuccess
}
