package de.kempmobil.ktor.mqtt

import io.ktor.network.sockets.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerReferenceTest {

    @Test
    fun `empty server reference returns empty list`() {
        listOf(
            "", "   "
        ).forEach { str ->
            assertEquals(0, ServerReference(str).servers.size)
        }
    }

    @Test
    fun `single server is parse correctly`() {
        listOf(
            "myserver.xyz.org" to InetSocketAddress("myserver.xyz.org", 0),
            "myserver.xyz.org:8883" to InetSocketAddress("myserver.xyz.org", 8883),
            "10.10.151.22:8883" to InetSocketAddress("10.10.151.22", 8883),
            "[fe80::9610:3eff:fe1c]:1883" to InetSocketAddress("fe80::9610:3eff:fe1c", 1883)
        ).forEach {
            val servers = ServerReference(it.first).servers
            assertEquals(1, servers.size)
            assertEquals(it.second, servers[0])
        }
    }

    @Test
    fun `multiple servers are parse correctly`() {
        val str = "  myserver.xyz.org      myserver.xyz.org:8883 10.10.151.22:8883 [fe80::9610:3eff:fe1c]:1883  "
        val expected = listOf(
            InetSocketAddress("myserver.xyz.org", 0),
            InetSocketAddress("myserver.xyz.org", 8883),
            InetSocketAddress("10.10.151.22", 8883),
            InetSocketAddress("fe80::9610:3eff:fe1c", 1883)
        )
        val ref = ServerReference(str)

        assertEquals(expected, ref.servers)
    }

    @Test
    fun `fail gracefully when server reference cannot be parsed`() {
        assertEquals(0, ServerReference("a.b.c:not_a_number").servers.size)
        assertEquals(0, ServerReference("[fe80::9610").servers.size)
    }
}