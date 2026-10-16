package org.diaabend.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigikamFsTest {
    private val server = Server("nas")

    /** A fake server: folder URL to its subfolder and photo names. */
    private class Tree(val folders: Map<String, List<String>>, val photos: Map<String, List<String>> = emptyMap()) {
        val listed = ArrayList<String>()

        fun list(url: String): Listing {
            listed += url
            return Listing(
                folders.getOrDefault(url, emptyList()).map { Entry(it, "$url$it/") },
                photos.getOrDefault(url, emptyList()).map { Entry(it, "$url$it") },
            )
        }
    }

    private fun resolve(tree: Tree, url: String, prefs: DigikamPrefs = DigikamPrefs()): Resolved =
        DigikamFs.resolve(server, url, tree.list(url), prefs, tree::list)

    private val names = { r: Resolved -> r.listing.folders.map { it.name } }

    @Test
    fun recognisesStarFolders() {
        assertEquals(3, DigikamFs.starLevel("3Sterne"))
        assertEquals(4, DigikamFs.starLevel("4sterne"))
        assertEquals(null, DigikamFs.starLevel("Urlaub"))
        assertEquals(null, DigikamFs.starLevel("3Sterne extra"))
    }

    @Test
    fun skipsProfileFolderOfShareHoldingAllProfiles() {
        val tree = Tree(mapOf("smb://nas/Fotos/" to listOf("Original", "QHD", "Web"), "smb://nas/Fotos/QHD/" to listOf("2023", "2024")))
        val resolved = resolve(tree, "smb://nas/Fotos/")
        assertEquals(listOf("2023", "2024"), names(resolved))
        assertEquals("smb://nas/Fotos/QHD/", resolved.photosUrl)
        assertFalse(resolved.ratingFiltered)
    }

    @Test
    fun profileMatchIgnoresCaseAndSurroundingSpaces() {
        val tree = Tree(mapOf("smb://nas/Fotos/" to listOf("Original", "QHD"), "smb://nas/Fotos/QHD/" to listOf("2024")))
        assertEquals(listOf("2024"), names(resolve(tree, "smb://nas/Fotos/", DigikamPrefs(profile = " qhd "))))
    }

    @Test
    fun leavesShareAloneWithoutTheProfile() {
        val tree = Tree(mapOf("smb://nas/Fotos/" to listOf("Original", "Web")))
        assertEquals(listOf("Original", "Web"), names(resolve(tree, "smb://nas/Fotos/")))
        assertEquals(listOf("smb://nas/Fotos/"), tree.listed)
    }

    @Test
    fun blankProfileDisablesSkipping() {
        val tree = Tree(mapOf("smb://nas/Fotos/" to listOf("QHD")))
        assertEquals(listOf("QHD"), names(resolve(tree, "smb://nas/Fotos/", DigikamPrefs(profile = ""))))
    }

    @Test
    fun onlyLooksForTheProfileInShareRoots() {
        val tree = Tree(mapOf("smb://nas/Fotos/Urlaub/" to listOf("QHD")))
        assertEquals(listOf("QHD"), names(resolve(tree, "smb://nas/Fotos/Urlaub/")))
    }

    @Test
    fun doesNotSkipFolderOfShareWithPhotosInRoot() {
        val tree = Tree(mapOf("smb://nas/Fotos/" to listOf("QHD")), mapOf("smb://nas/Fotos/" to listOf("a.jpg")))
        val resolved = resolve(tree, "smb://nas/Fotos/")
        assertEquals(listOf("QHD"), names(resolved))
        assertEquals(listOf("a.jpg"), resolved.listing.photos.map { it.name })
    }

    @Test
    fun picksLowestStarFolderThatSatisfiesTheFilter() {
        val album = "smb://nas/Fotos/QHD/2024/Rom/"
        val tree = Tree(
            mapOf(album to listOf("3Sterne", "4Sterne")),
            mapOf(album + "3Sterne/" to listOf("a.jpg", "b.jpg"), album + "4Sterne/" to listOf("b.jpg")),
        )
        val three = resolve(tree, album, DigikamPrefs(minStars = 3))
        assertEquals(listOf("a.jpg", "b.jpg"), three.listing.photos.map { it.name })
        assertEquals(album + "3Sterne/", three.photosUrl)
        assertTrue(three.ratingFiltered)

        // Below the lowest folder there is nothing else to choose
        assertEquals(album + "3Sterne/", resolve(tree, album, DigikamPrefs(minStars = 1)).photosUrl)

        val four = resolve(tree, album, DigikamPrefs(minStars = 4))
        assertEquals(listOf("b.jpg"), four.listing.photos.map { it.name })
        assertEquals(album + "4Sterne/", four.photosUrl)
    }

    @Test
    fun noStarFolderGoodEnoughMeansNoPhotos() {
        val album = "smb://nas/Fotos/QHD/2024/Rom/"
        val tree = Tree(mapOf(album to listOf("3Sterne")), mapOf(album + "3Sterne/" to listOf("a.jpg")))
        val resolved = resolve(tree, album, DigikamPrefs(minStars = 5))
        assertTrue(resolved.listing.photos.isEmpty() && resolved.listing.folders.isEmpty())
        assertTrue(resolved.ratingFiltered)
        assertEquals(listOf(album), tree.listed)
    }

    @Test
    fun keepsSubAlbumsNextToTheChosenStarFolder() {
        val album = "smb://nas/Fotos/QHD/2024/Rom/"
        val tree = Tree(mapOf(album to listOf("Forum", "3Sterne")), mapOf(album + "3Sterne/" to listOf("a.jpg")))
        val resolved = resolve(tree, album)
        assertEquals(listOf("Forum"), names(resolved))
        assertEquals(listOf("a.jpg"), resolved.listing.photos.map { it.name })
    }

    @Test
    fun skipsProfileAndStarFoldersTogether() {
        val tree = Tree(
            mapOf("smb://nas/Fotos/" to listOf("QHD"), "smb://nas/Fotos/QHD/" to listOf("3Sterne")),
            mapOf("smb://nas/Fotos/QHD/3Sterne/" to listOf("a.jpg")),
        )
        val resolved = resolve(tree, "smb://nas/Fotos/")
        assertEquals(listOf("a.jpg"), resolved.listing.photos.map { it.name })
        assertEquals("smb://nas/Fotos/QHD/3Sterne/", resolved.photosUrl)
    }

    @Test
    fun everyFilterFindsItsOwnFolderWhenAllLevelsAreExported() {
        val album = "smb://nas/Fotos/QHD/2024/Rom/"
        val levels = (1..5).map { "${it}Sterne" }
        val tree = Tree(mapOf(album to levels), levels.associate { "$album$it/" to listOf("$it.jpg") })
        for (min in 1..5) {
            val resolved = resolve(tree, album, DigikamPrefs(minStars = min))
            assertEquals("${min}Sterne", resolved.photosUrl.trimEnd('/').substringAfterLast('/'))
            assertEquals(listOf("${min}Sterne.jpg"), resolved.listing.photos.map { it.name })
        }
    }

    @Test
    fun filterAboveWhatIsExportedPicksNothingInsteadOfLowerRatedPhotos() {
        val album = "smb://nas/Fotos/QHD/2024/Rom/"
        val tree = Tree(mapOf(album to listOf("2Sterne", "3Sterne")), mapOf(album + "2Sterne/" to listOf("a.jpg"), album + "3Sterne/" to listOf("b.jpg")))
        assertTrue(resolve(tree, album, DigikamPrefs(minStars = 5)).listing.photos.isEmpty())
        assertEquals(album + "2Sterne/", resolve(tree, album, DigikamPrefs(minStars = 2)).photosUrl)
    }

    @Test
    fun hidesSharesOfOtherProfiles() {
        val tree = Tree(mapOf("smb://nas/" to listOf("DigikamFS-Original", "DigikamFS-QHD", "DigikamFS-Web", "Musik")))
        assertEquals(listOf("DigikamFS-QHD", "Musik"), names(resolve(tree, "smb://nas/")))
    }

    @Test
    fun keepsAllSharesIfTheWantedProfileHasNone() {
        val tree = Tree(mapOf("smb://nas/" to listOf("DigikamFS-Original", "DigikamFS-Web")))
        assertEquals(listOf("DigikamFS-Original", "DigikamFS-Web"), names(resolve(tree, "smb://nas/")))
    }
}
