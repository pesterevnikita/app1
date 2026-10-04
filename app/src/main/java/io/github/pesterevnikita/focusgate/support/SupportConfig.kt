package io.github.pesterevnikita.focusgate.support

/** Public support destinations are copied locally; FocusGate never opens a network connection. */
object SupportConfig {
    const val REPOSITORY_URL = "https://github.com/pesterevnikita/app1"

    // Set a verified public https://t.me/... feedback link when you are ready to receive messages.
    // Leave empty to show "coming soon"; never put a Telegram bot token or private invite here.
    const val TELEGRAM_FEEDBACK_URL = ""
}
