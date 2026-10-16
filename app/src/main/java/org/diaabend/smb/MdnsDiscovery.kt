package org.diaabend.smb

import android.content.Context
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * Finds servers that announce themselves via mDNS (Samba with Avahi, macOS, most NAS boxes).
 * Complements [Discovery], mainly by providing proper names. [onFound] is called on a background thread.
 *
 * Deliberately not built on NsdManager: before Android 14 a service resolution can't be cancelled, and
 * resolving a service whose host has no address (a stale announcement, a server shut down meanwhile)
 * makes the system ask for that host over and over, hundreds of times a second, which can bring down a
 * whole WLAN. This asks three times, listens for a few seconds and is done, whatever the network does.
 */
class MdnsDiscovery(@Suppress("UNUSED_PARAMETER") context: Context, private val onFound: (host: String, name: String) -> Unit) {
    @Volatile
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (worker?.isAlive == true) return
        worker = thread(isDaemon = true, name = "mdns") { run() }
    }

    @Synchronized
    fun stop() {
        socket?.close()
        worker = null
    }

    private fun run() {
        try {
            // A source port other than 5353 makes responders answer us directly (RFC 6762, 6.7), so
            // neither joining the multicast group nor a multicast lock is needed
            val s = DatagramSocket().also { socket = it }
            s.use {
                val query = MdnsPacket.query(SERVICE)
                val group = InetAddress.getByName(GROUP)
                val records = MdnsRecords()
                val reported = HashSet<String>()
                val buffer = ByteArray(9000)
                val start = System.currentTimeMillis()
                var sent = 0
                while (true) {
                    val elapsed = System.currentTimeMillis() - start
                    if (elapsed >= LISTEN_MS) break
                    if (sent < SEND_AT_MS.size && elapsed >= SEND_AT_MS[sent]) {
                        it.send(DatagramPacket(query, query.size, group, PORT))
                        sent++
                    }
                    it.soTimeout = 200
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        it.receive(packet)
                        records.add(buffer, packet.length)
                        for ((host, name) in records.servers()) if (reported.add(name)) onFound(host, name)
                    } catch (e: SocketTimeoutException) {
                        // just the next round of the loop
                    }
                }
            }
        } catch (e: Exception) {
            // Closed by stop(), or no network: discovery is a convenience, nothing to report
        } finally {
            socket = null
        }
    }

    private companion object {
        const val SERVICE = "_smb._tcp.local"
        const val GROUP = "224.0.0.251"
        const val PORT = 5353
        const val LISTEN_MS = 5_000L
        val SEND_AT_MS = longArrayOf(0, 1_000, 3_000)
    }
}

/** The pieces of mDNS responses needed to turn "_smb._tcp" instances into (IPv4 address, name). */
class MdnsRecords {
    private val instances = LinkedHashSet<String>()
    private val targets = HashMap<String, String>()
    private val addresses = HashMap<String, String>()

    fun add(data: ByteArray, length: Int) {
        MdnsPacket.parse(data, length) { type, owner, rdata, offset ->
            when (type) {
                MdnsPacket.PTR -> if (owner.equals("_smb._tcp.local", ignoreCase = true)) {
                    MdnsPacket.readName(data, offset)?.let { instances += it.first }
                }
                MdnsPacket.SRV -> MdnsPacket.readName(data, offset + 6)?.let { targets[owner.lowercase()] = it.first.lowercase() }
                MdnsPacket.A -> if (rdata == 4) {
                    addresses[owner.lowercase()] = (0 until 4).joinToString(".") { (data[offset + it].toInt() and 0xFF).toString() }
                }
            }
        }
    }

    /** Instances whose host has been seen with an IPv4 address; those without one are left alone for good. */
    fun servers(): List<Pair<String, String>> = instances.mapNotNull { instance ->
        val address = targets[instance.lowercase()]?.let { addresses[it] } ?: return@mapNotNull null
        address to instance.substringBefore("._smb._tcp")
    }
}

/** Just enough DNS wire format for [MdnsRecords]. */
object MdnsPacket {
    const val A = 1
    const val PTR = 12
    const val SRV = 33

    /** A query for the PTR records of [name] (not asking for a unicast response, which legacy queries get anyway). */
    fun query(name: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in name.split('.')) {
            val bytes = label.toByteArray()
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(byteArrayOf(0, 0, PTR.toByte(), 0, 1))
        return out.toByteArray()
    }

    /** Calls [record] with type, owner name, rdata length and rdata offset for every record of a response. */
    fun parse(data: ByteArray, length: Int, record: (type: Int, owner: String, rdataLength: Int, rdataOffset: Int) -> Unit) {
        try {
            if (length < 12 || data[2].toInt() and 0x80 == 0) return // not a response
            var pos = 12
            repeat(u16(data, 4)) { pos = readName(data, pos)!!.second + 4 }
            repeat(u16(data, 6) + u16(data, 8) + u16(data, 10)) {
                val (owner, afterName) = readName(data, pos)!!
                val type = u16(data, afterName)
                val rdataLength = u16(data, afterName + 8)
                val rdata = afterName + 10
                if (rdata + rdataLength > length) return
                record(type, owner, rdataLength, rdata)
                pos = rdata + rdataLength
            }
        } catch (e: RuntimeException) {
            // Truncated or malformed packet: use what was read so far
        }
    }

    /** Reads a possibly compressed name at [start]; returns it with the offset just behind it, or null if malformed. */
    fun readName(data: ByteArray, start: Int): Pair<String, Int>? {
        val labels = ArrayList<String>()
        var pos = start
        var end = -1
        var jumps = 0
        while (true) {
            if (pos !in data.indices) return null
            val n = data[pos].toInt() and 0xFF
            when {
                n == 0 -> return labels.joinToString(".") to (if (end >= 0) end else pos + 1)
                n and 0xC0 == 0xC0 -> {
                    if (pos + 1 !in data.indices || ++jumps > 16) return null
                    if (end < 0) end = pos + 2
                    pos = ((n and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                }
                n and 0xC0 != 0 || pos + 1 + n > data.size -> return null
                else -> {
                    labels += String(data, pos + 1, n, Charsets.UTF_8)
                    pos += 1 + n
                }
            }
        }
    }

    private fun u16(data: ByteArray, at: Int) = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
}
