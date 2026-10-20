package com.freedomfighter.readerslauncher.data

/**
 * The colour themes shared by the Reader's apps: the two inks (white on black, black on white),
 * then the palettes of the Plume keyboard, in its order. Plain ARGB values so the parts that
 * cannot use Compose (the quick-settings tile) read the same colours.
 */
data class Palette(val mode: ThemeMode, val bg: Long, val fg: Long, val accent: Long, val onAccent: Long) {
    /** Dark background: the status and navigation bar icons depend on it. */
    val isDark: Boolean get() {
        val r = (bg shr 16 and 0xFF) / 255f; val g = (bg shr 8 and 0xFF) / 255f; val b = (bg and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b < 0.5f
    }
}

object Palettes {
    val ALL = listOf(
        Palette(ThemeMode.DARK, 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000),
        Palette(ThemeMode.LIGHT, 0xFFFFFFFF, 0xFF000000, 0xFF000000, 0xFFFFFFFF),
        Palette(ThemeMode.LAVANDE, 0xFF1F1A2E, 0xFFF4EFFF, 0xFFB8A4FF, 0xFF1F1A2E),
        Palette(ThemeMode.SAUGE, 0xFFD9E0D4, 0xFF1E2A21, 0xFF4C7A5B, 0xFFFFFFFF),
        Palette(ThemeMode.NORDIQUE, 0xFF2E3440, 0xFFECEFF4, 0xFF88C0D0, 0xFF2E3440),
        Palette(ThemeMode.TERRACOTTA, 0xFFE6D9CC, 0xFF2B211B, 0xFFC25E38, 0xFFFFFFFF),
        Palette(ThemeMode.COQUETTE, 0xFFF3D9DC, 0xFF4A1F2B, 0xFFD2455E, 0xFFFFFFFF),
        Palette(ThemeMode.MILITANTE, 0xFFF2EEE4, 0xFF121212, 0xFFE8412E, 0xFFFFFFFF),
        Palette(ThemeMode.TORRIDE, 0xFF1E0D13, 0xFFF8E9E6, 0xFFE02A50, 0xFFFFF4F2),
        Palette(ThemeMode.NATURE, 0xFF2B2A24, 0xFFF2EFE4, 0xFFD19A4E, 0xFF2B2A24),
    )

    /** The palette of a setting; "system" follows the phone between the two inks. */
    fun of(mode: ThemeMode, systemDark: Boolean): Palette = when (mode) {
        ThemeMode.SYSTEM -> if (systemDark) ALL[0] else ALL[1]
        else -> ALL.first { it.mode == mode }
    }

    /** The theme after this one, round and round: the inks first, then the palettes. */
    fun next(mode: ThemeMode): ThemeMode = ALL[(ALL.indexOfFirst { it.mode == mode } + 1) % ALL.size].mode

    fun parse(name: String?): ThemeMode = runCatching { ThemeMode.valueOf(name ?: "") }.getOrDefault(ThemeMode.DARK)
}
