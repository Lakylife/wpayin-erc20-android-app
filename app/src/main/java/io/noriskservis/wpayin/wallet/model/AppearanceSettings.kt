package io.noriskservis.wpayin.wallet.model

import java.time.DateTimeException
import java.time.ZoneId

enum class AppColorTheme(val storageValue: String) {
    INDIGO("indigo"),
    EMERALD("emerald"),
    AMBER("amber"),
    ROSE("rose"),
    SKY("sky");

    companion object {
        fun fromStorage(value: String?): AppColorTheme =
            entries.firstOrNull { it.storageValue == value } ?: INDIGO
    }
}

enum class AssetListStyle(val storageValue: String) {
    CARDS("cards"),
    COMPACT("compact");

    companion object {
        fun fromStorage(value: String?): AssetListStyle =
            entries.firstOrNull { it.storageValue == value } ?: CARDS
    }
}

const val AUTOMATIC_TIME_ZONE_ID = "automatic"

fun AppSettings.resolvedZoneId(): ZoneId {
    if (timeZoneId == AUTOMATIC_TIME_ZONE_ID) return ZoneId.systemDefault()
    return try {
        ZoneId.of(timeZoneId)
    } catch (_: DateTimeException) {
        ZoneId.systemDefault()
    }
}
