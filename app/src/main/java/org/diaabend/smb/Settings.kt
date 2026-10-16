package org.diaabend.smb

import android.content.Context

/** The user's choices from the settings screen, kept in app-private preferences. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Name of the DigikamFS resolution profile to show; blank means "don't skip it". */
    var profile: String
        get() = prefs.getString(PROFILE, DigikamPrefs.DEFAULT_PROFILE) ?: DigikamPrefs.DEFAULT_PROFILE
        set(value) = prefs.edit().putString(PROFILE, value.trim()).apply()

    /** Lowest star rating of the photos to show from a DigikamFS export. */
    var minStars: Int
        get() = prefs.getInt(MIN_STARS, DigikamPrefs.DEFAULT_MIN_STARS).coerceIn(1, DigikamPrefs.MAX_STARS)
        set(value) = prefs.edit().putInt(MIN_STARS, value).apply()

    val digikam: DigikamPrefs get() = DigikamPrefs(profile, minStars)

    private companion object {
        const val PROFILE = "profile"
        const val MIN_STARS = "min_stars"
    }
}
