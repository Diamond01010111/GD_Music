package com.diamond.gdmusic

import android.content.Context

enum class AudioQuality(val bitrate: Int, val label: String) {
    STANDARD(128, "标准 128K"),
    HIGH(320, "高品质 320K"),
    LOSSLESS(999, "无损")
    ;

    companion object {
        fun fromBitrate(bitrate: Int): AudioQuality =
            entries.firstOrNull { it.bitrate == bitrate } ?: LOSSLESS
    }
}

object PlaybackPreferences {
    private const val PREFS_NAME = "playback_preferences"
    private const val KEY_DEFAULT_BITRATE = "default_bitrate"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_SHOW_LYRIC_TRANSLATION = "show_lyric_translation"

    @JvmStatic
    fun defaultBitrate(context: Context): Int = preferences(context)
        .getInt(KEY_DEFAULT_BITRATE, AudioQuality.LOSSLESS.bitrate)

    @JvmStatic
    fun setDefaultBitrate(context: Context, bitrate: Int) {
        preferences(context).edit()
            .putInt(KEY_DEFAULT_BITRATE, AudioQuality.fromBitrate(bitrate).bitrate)
            .apply()
    }

    @JvmStatic
    fun darkMode(context: Context): Boolean = preferences(context)
        .getBoolean(KEY_DARK_MODE, false)

    @JvmStatic
    fun setDarkMode(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    @JvmStatic
    fun showLyricTranslation(context: Context): Boolean = preferences(context)
        .getBoolean(KEY_SHOW_LYRIC_TRANSLATION, false)

    @JvmStatic
    fun setShowLyricTranslation(context: Context, enabled: Boolean) {
        preferences(context).edit()
            .putBoolean(KEY_SHOW_LYRIC_TRANSLATION, enabled)
            .apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
}
