package org.diaabend.smb

import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Properties

/** A host with an open SMB port. [name] is null until (and unless) a name could be looked up. */
data class Found(val host: String, val name: String?)

/** Finds SMB servers by probing port 445 on every address of the local /24 networks. */
object Discovery {
    private const val CONNECT_TIMEOUT_MS = 500
    private const val PARALLEL_PROBES = 48

    private val netbios by lazy {
        BaseContext(PropertyConfiguration(Properties().apply {
            put("jcifs.netbios.retryCount", "1")
            put("jcifs.netbios.retryTimeout", "800")
        }))
    }

    fun scan(port: Int = Server.DEFAULT_PORT): Flow<Found> = channelFlow {
        val gate = Semaphore(PARALLEL_PROBES)
        for (address in candidates()) {
            launch(Dispatchers.IO) {
                if (!gate.withPermit { isOpen(address, port) }) return@launch
                val host = address.hostAddress ?: return@launch
                send(Found(host, null))
                lookupName(address)?.let { send(Found(host, it)) }
            }
        }
    }

    /** All other hosts in the private IPv4 networks this device is attached to. */
    private fun candidates(): List<InetAddress> {
        val result = LinkedHashSet<InetAddress>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
        for (nic in interfaces) {
            if (runCatching { !nic.isUp || nic.isLoopback }.getOrDefault(true)) continue
            for (own in nic.interfaceAddresses.mapNotNull { it.address as? Inet4Address }) {
                // Never scan anything but private ranges, and never more than a /24
                if (!own.isSiteLocalAddress) continue
                val bytes = own.address
                for (last in 1..254) {
                    if (last == bytes[3].toInt() and 0xFF) continue
                    result += InetAddress.getByAddress(byteArrayOf(bytes[0], bytes[1], bytes[2], last.toByte()))
                }
            }
        }
        return result.toList()
    }

    private fun isOpen(address: InetAddress, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS) }
        true
    } catch (e: Exception) {
        false
    }

    private fun lookupName(address: InetAddress): String? {
        val ip = address.hostAddress ?: return null
        val dns = runCatching { InetAddress.getByAddress(address.address).canonicalHostName }.getOrNull()
        if (!dns.isNullOrEmpty() && dns != ip) return dns
        return runCatching {
            netbios.nameServiceClient.getNbtAllByAddress(ip).firstOrNull { it.nameType == 0x20 }?.hostName
        }.getOrNull()?.takeIf { it.isNotBlank() && it != ip }
    }
}
