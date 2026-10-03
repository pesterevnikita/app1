package io.github.pesterevnikita.focusgate.diagnostics
import java.io.File
class LocalDiagnostics(directory: File, private val limit: Int = 1048576) {
    private val file=File(directory,"diagnostics.txt")
    @Synchronized fun record(category: String, timestamp: Long) {
        require(category in setOf("DENY","HOME_FAILED","SERVICE_CONNECTED","SERVICE_DISCONNECTED","HEALTH_DEGRADED","RECOVERY","UNKNOWN_BROWSER","LOCK_STARTED","LOCK_RELEASED"))
        file.parentFile?.mkdirs()
        val lines=(if(file.exists()) file.readText() else "")+"$timestamp $category\n"
        val bounded=if(lines.length>limit) lines.takeLast(limit).substringAfter('\n',"") else lines
        file.writeText(bounded)
    }
    @Synchronized fun export(): String = if(file.exists()) file.readText() else ""
}
