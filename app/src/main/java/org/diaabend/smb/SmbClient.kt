package org.diaabend.smb

import jcifs.CIFSContext
import jcifs.SmbConstants
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.dcerpc.DcerpcHandle
import jcifs.dcerpc.msrpc.MsrpcShareEnum
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import java.io.IOException
import java.io.Serializable
import java.util.Properties

data class Server(
    val host: String,
    val port: Int = DEFAULT_PORT,
    val name: String = host,
    val user: String = "",
    val password: String = "",
    val domain: String = "",
) : Serializable {
    val id: String get() = if (port == DEFAULT_PORT) host else "$host:$port"
    val rootUrl: String get() = "smb://$id/"
    val hasCredentials: Boolean get() = user.isNotEmpty()

    companion object {
        const val DEFAULT_PORT = 445

        /** Accepts "host", "host:port" and "smb://host/". */
        fun parse(input: String): Server? {
            val address = input.trim().removePrefix("smb://").removePrefix("\\\\").trim('/', '\\')
            if (address.isEmpty() || address.any { it.isWhitespace() || it == '/' }) return null
            val host = address.substringBefore(':')
            val port = if (':' in address) address.substringAfter(':').toIntOrNull() ?: return null else DEFAULT_PORT
            return if (host.isEmpty()) null else Server(host, port)
        }
    }
}

data class Entry(val name: String, val url: String)

class Listing(val folders: List<Entry>, val photos: List<Entry>)

object SmbClient {
    private const val MAX_PHOTO_BYTES = 64L * 1024 * 1024

    private val contexts = HashMap<Server, CIFSContext>()

    /** The most recent listing, so the slideshow doesn't have to list its folder a second time. */
    @Volatile
    var lastListing: Pair<String, Listing>? = null
        private set

    @Synchronized
    private fun context(server: Server): CIFSContext = contexts.getOrPut(server.copy(name = "")) {
        val properties = Properties().apply {
            put("jcifs.smb.client.minVersion", "SMB202")
            put("jcifs.smb.client.maxVersion", "SMB311")
            put("jcifs.smb.client.dfs.disabled", "true")
            put("jcifs.resolveOrder", "DNS")
            put("jcifs.smb.client.connTimeout", "5000")
            put("jcifs.smb.client.responseTimeout", "15000")
            // Guest sessions can't sign, so enforcing it would make share listing fail for them
            put("jcifs.smb.client.ipcSigningEnforced", server.hasCredentials.toString())
        }
        val base = BaseContext(PropertyConfiguration(properties))
        if (server.hasCredentials) {
            base.withCredentials(NtlmPasswordAuthenticator(server.domain, server.user, server.password))
        } else {
            base.withGuestCrendentials()
        }
    }

    /** Lists the shares of the server (for its root URL) or the subfolders and photos of a folder. */
    fun list(server: Server, url: String, photoExtensions: Set<String>): Listing {
        val folders = ArrayList<Entry>()
        val photos = ArrayList<Entry>()
        if (url == server.rootUrl) {
            folders += shares(server)
        } else {
            SmbFile(url, context(server)).use { dir ->
                for (file in dir.listFiles()) {
                    val name = file.name.trimEnd('/')
                    val entry = Entry(name, file.url.toString())
                    when {
                        name.startsWith(".") || name.startsWith("@") || name.startsWith("#") || name.startsWith("$") -> {}
                        file.isHidden -> {}
                        file.isDirectory -> folders += entry
                        name.substringAfterLast('.', "").lowercase() in photoExtensions -> photos += entry
                    }
                }
            }
        }
        val byName = compareBy<Entry, String>(NaturalOrder) { it.name }
        return Listing(folders.sortedWith(byName), photos.sortedWith(byName)).also { lastListing = url to it }
    }

    /**
     * The disk shares of a server, without administrative ones (C$, ...). Asks the server directly
     * instead of listing "smb://host/", because jcifs ignores a non-default port when doing that.
     */
    private fun shares(server: Server): List<Entry> {
        val request = MsrpcShareEnum(server.host)
        DcerpcHandle.getHandle("ncacn_np:${server.id}[\\PIPE\\srvsvc]", context(server)).use { it.sendrecv(request) }
        if (request.retval != 0) throw SmbException(request.retval, true)
        return request.entries
            .filter { it.type == SmbConstants.TYPE_SHARE && !it.name.endsWith("$") }
            .map { Entry(it.name, "${server.rootUrl}${it.name}/") }
    }

    /** Reads a whole file; returns null if [keepGoing] turned false before the download finished. */
    fun read(server: Server, url: String, keepGoing: () -> Boolean = { true }): ByteArray? =
        SmbFile(url, context(server)).use { file ->
            val length = file.length()
            if (length > MAX_PHOTO_BYTES) throw IOException("File too large: $length bytes")
            val bytes = ByteArray(length.toInt())
            file.openInputStream().use { input ->
                var offset = 0
                while (offset < bytes.size) {
                    if (!keepGoing()) return null
                    val n = input.read(bytes, offset, bytes.size - offset)
                    if (n < 0) throw IOException("Unexpected end of file")
                    offset += n
                }
            }
            bytes
        }
}

/** Case-insensitive ordering that sorts embedded numbers by value: "img2" before "img10". */
val NaturalOrder = Comparator<String> { a, b ->
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        if (a[i].isDigit() && b[j].isDigit()) {
            val startA = i
            val startB = j
            while (i < a.length && a[i].isDigit()) i++
            while (j < b.length && b[j].isDigit()) j++
            val numberA = a.substring(startA, i).trimStart('0')
            val numberB = b.substring(startB, j).trimStart('0')
            val c = if (numberA.length != numberB.length) numberA.length - numberB.length else numberA.compareTo(numberB)
            if (c != 0) return@Comparator c
        } else {
            val c = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
            if (c != 0) return@Comparator c
            i++
            j++
        }
    }
    val rest = (a.length - i) - (b.length - j)
    if (rest != 0) rest else a.compareTo(b)
}
