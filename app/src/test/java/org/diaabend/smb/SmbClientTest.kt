package org.diaabend.smb

import jcifs.smb.SmbAuthException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Runs against a real SMB server and is skipped unless one is configured:
 *
 *   SMB_TEST_SERVER   host[:port]
 *   SMB_TEST_DIR      local directory the server exports as guest-readable share "photos"
 *   SMB_TEST_USER / SMB_TEST_PASSWORD   account allowed to read the non-guest share "private"
 */
class SmbClientTest {
    private val extensions = setOf("jpg", "png")
    private lateinit var guest: Server
    private lateinit var localDir: File

    @Before
    fun setUp() {
        val address = System.getenv("SMB_TEST_SERVER")
        assumeTrue("SMB_TEST_SERVER not set", address != null)
        guest = Server.parse(address!!)!!
        localDir = File(System.getenv("SMB_TEST_DIR")!!)
    }

    private fun user() = guest.copy(user = System.getenv("SMB_TEST_USER")!!, password = System.getenv("SMB_TEST_PASSWORD")!!)

    @Test
    fun guestSeesSharesButNoHiddenOnes() {
        val listing = SmbClient.list(guest, guest.rootUrl, extensions)
        assertEquals(listOf("photos", "private"), listing.folders.map { it.name })
        assertEquals(emptyList<Entry>(), listing.photos)
    }

    @Test
    fun listsFoldersAndPhotosInNaturalOrder() {
        val share = SmbClient.list(guest, guest.rootUrl, extensions).folders.first { it.name == "photos" }
        val listing = SmbClient.list(guest, share.url, extensions)
        assertEquals(listOf("Urlaub 2024", "Ünïcödé #1 & 100%"), listing.folders.map { it.name })
        assertEquals(listOf("img2.jpg", "IMG10.JPG", "img100.png"), listing.photos.map { it.name })
    }

    @Test
    fun readsFilesWithAwkwardNames() {
        val share = SmbClient.list(guest, guest.rootUrl, extensions).folders.first { it.name == "photos" }
        val folder = SmbClient.list(guest, share.url, extensions).folders.first { it.name.startsWith("Ünï") }
        val photos = SmbClient.list(guest, folder.url, extensions).photos
        assertEquals(listOf("a b#c%20d.jpg", "größe.jpg"), photos.map { it.name })
        for (photo in photos) {
            val expected = File(localDir, "${folder.name}/${photo.name}").readBytes()
            assertArrayEquals(expected, SmbClient.read(guest, photo.url))
        }
    }

    @Test
    fun readStopsWhenCancelled() {
        val share = SmbClient.list(guest, guest.rootUrl, extensions).folders.first { it.name == "photos" }
        val photo = SmbClient.list(guest, share.url, extensions).photos.first()
        assertNull(SmbClient.read(guest, photo.url) { false })
    }

    @Test
    fun privateShareNeedsLogin() {
        val url = guest.rootUrl + "private/"
        assertThrows(SmbAuthException::class.java) { SmbClient.list(guest, url, extensions) }
        assertThrows(SmbAuthException::class.java) { SmbClient.list(guest.copy(user = "nobody", password = "wrong"), url, extensions) }
        assertEquals(listOf("secret.jpg"), SmbClient.list(user(), url, extensions).photos.map { it.name })
    }
}
