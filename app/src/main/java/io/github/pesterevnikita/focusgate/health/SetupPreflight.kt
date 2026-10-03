package io.github.pesterevnikita.focusgate.health
object SetupPreflight {
    fun refusal(accessibility: Boolean, needsPassword: Boolean, passwordPresent: Boolean): String? = when {
        !accessibility -> "Enable and connect Accessibility before starting Restricted Mode."
        needsPassword && !passwordPresent -> "Set a trusted-person password first."
        else -> null
    }
}
