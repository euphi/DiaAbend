package org.diaabend.smb

import org.junit.Assert.assertEquals
import org.junit.Test

/** Responses as Avahi really sent them to a "_smb._tcp.local" PTR query. */
class MdnsRecordsTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val withAddress = bytes(
        "000084000001000600000000045f736d62045f746370056c6f63616c00000c0001c00c000c00010000000a0008054941323136c00cc02d001000010000000a000100" +
            "c02d002100010000000a000e0000000001bd056961323136c016c054001c00010000000a0010fd7ad212312d0000000000000000ffe5" +
            "c054001c00010000000a0010fd7ad212312d00007887a8fffe02feb8c054000100010000000a0004c0a8000f"
    )

    // The host in the SRV record has no address at all: this is what made Android's NsdManager go berserk
    private val withoutAddress = bytes(
        "000084000001000300000000045f736d62045f746370056c6f63616c00000c0001c00c000c00010000000a000c09444947494b414d4653c00c" +
            "c02d001000010000000a000100c02d002100010000000a00110000000001bd086d6172746f6b2d39c016"
    )

    private fun servers(vararg packets: ByteArray) = MdnsRecords().apply { packets.forEach { add(it, it.size) } }.servers()

    @Test
    fun resolvesInstanceToIpv4AddressAndName() {
        assertEquals(listOf("192.168.0.15" to "IA216"), servers(withAddress))
    }

    @Test
    fun ignoresInstanceWhoseHostHasNoAddress() {
        assertEquals(emptyList<Pair<String, String>>(), servers(withoutAddress))
        assertEquals(listOf("192.168.0.15" to "IA216"), servers(withoutAddress, withAddress))
    }

    @Test
    fun survivesGarbage() {
        assertEquals(emptyList<Pair<String, String>>(), servers(ByteArray(0), ByteArray(40) { 0xFF.toByte() }, withAddress.copyOf(60)))
    }

    @Test
    fun queryAsksForSmbPointers() {
        assertEquals(
            "000000000001000000000000045f736d62045f746370056c6f63616c00000c0001",
            MdnsPacket.query("_smb._tcp.local").joinToString("") { "%02x".format(it) },
        )
    }
}
