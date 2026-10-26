package io.noriskservis.wpayin.wallet.model

/**
 * In-app display languages, mirroring the iOS Language list. Czech is fully
 * translated; the other languages fall back to English resources until their
 * translations land (same convention as iOS).
 */
enum class AppLanguage(val tag: String, val nativeName: String, val flag: String) {
    ENGLISH("en", "English", "🇬🇧"),
    CZECH("cs", "Čeština", "🇨🇿"),
    GERMAN("de", "Deutsch", "🇩🇪"),
    SPANISH("es", "Español", "🇪🇸"),
    FRENCH("fr", "Français", "🇫🇷"),
    JAPANESE("ja", "日本語", "🇯🇵"),
    KOREAN("ko", "한국어", "🇰🇷"),
    CHINESE("zh", "中文", "🇨🇳");

    companion object {
        fun fromTag(tag: String?): AppLanguage? =
            entries.firstOrNull { candidate ->
                tag?.startsWith(candidate.tag) == true
            }
    }
}
