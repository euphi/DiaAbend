package org.diaabend.smb

/**
 * What the user wants from a DigikamFS export: the resolution profile to show and the lowest star
 * rating to accept. A blank [profile] turns off skipping of the profile level.
 */
data class DigikamPrefs(val profile: String = DEFAULT_PROFILE, val minStars: Int = DEFAULT_MIN_STARS) {
    companion object {
        const val DEFAULT_PROFILE = "QHD"
        const val DEFAULT_MIN_STARS = 3
        const val MAX_STARS = 5
    }
}

/** A folder listing with DigikamFS' sorting levels already resolved; [photosUrl] is where its [Listing.photos] live. */
class Resolved(val listing: Listing, val photosUrl: String, val ratingFiltered: Boolean = false)

/**
 * DigikamFS exports `<Profile>/<Year>/<Album>/<N>Sterne/<photo>`. Nobody wants to click through the
 * profile and star levels, so they are skipped here, using the user's fixed profile and star filter.
 * Works on plain listings and recognises DigikamFS by its folder names, so other shares are left alone.
 */
object DigikamFs {
    private val STAR_FOLDER = Regex("""(\d+)Sterne""", RegexOption.IGNORE_CASE)

    /** Prefix of the shares `generate_smb_shares.py` creates, one per profile. */
    private const val SHARE_PREFIX = "DigikamFS-"

    fun starLevel(name: String): Int? = STAR_FOLDER.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Resolves the listing of [url]. [list] fetches further folders and is only called when a sorting
     * level has to be looked into.
     */
    fun resolve(server: Server, url: String, listing: Listing, prefs: DigikamPrefs, list: (String) -> Listing): Resolved {
        val profile = prefs.profile.trim()
        if (url == server.rootUrl) return Resolved(withoutOtherProfiles(listing, profile), url)

        var currentUrl = url
        var current = listing
        // A share holding all profiles: carry on inside the one the user picked
        if (profile.isNotEmpty() && isShareRoot(server, url) && listing.photos.isEmpty()) {
            listing.folders.firstOrNull { it.name.equals(profile, ignoreCase = true) }?.let {
                currentUrl = it.url
                current = list(it.url)
            }
        }

        val stars = current.folders.mapNotNull { folder -> starLevel(folder.name)?.let { it to folder } }
        if (stars.isEmpty()) return Resolved(current, currentUrl)

        // A folder "3Sterne" holds everything rated 3 or more, so the lowest level that still
        // satisfies the filter shows the most photos without going below it
        val chosen = stars.filter { it.first >= prefs.minStars }.minByOrNull { it.first }?.second
        val others = current.folders.filter { starLevel(it.name) == null }
        if (chosen == null) return Resolved(Listing(others, current.photos), currentUrl, ratingFiltered = true)
        return Resolved(Listing(others, list(chosen.url).photos), chosen.url, ratingFiltered = true)
    }

    /**
     * With one share per profile ("DigikamFS-QHD", "DigikamFS-Web", ...) the other profiles' shares
     * are just more sorting levels. Only touched if the wanted profile's share exists.
     */
    private fun withoutOtherProfiles(listing: Listing, profile: String): Listing {
        if (profile.isEmpty()) return listing
        val wanted = SHARE_PREFIX + profile.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        if (listing.folders.none { it.name.equals(wanted, ignoreCase = true) }) return listing
        return Listing(
            listing.folders.filter { it.name.equals(wanted, ignoreCase = true) || !it.name.startsWith(SHARE_PREFIX, ignoreCase = true) },
            listing.photos,
        )
    }

    /** "smb://host/share/" as opposed to the server root or anything below a share. */
    private fun isShareRoot(server: Server, url: String): Boolean =
        url.startsWith(server.rootUrl) && url.removePrefix(server.rootUrl).trim('/').let { it.isNotEmpty() && '/' !in it }
}
