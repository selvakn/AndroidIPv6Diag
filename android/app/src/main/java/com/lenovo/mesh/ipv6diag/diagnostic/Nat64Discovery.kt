package com.lenovo.mesh.ipv6diag.diagnostic

import android.content.Context
import android.net.Network
import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import com.lenovo.mesh.ipv6diag.data.model.NAT64PrefixResult
import com.lenovo.mesh.ipv6diag.data.model.Nat64DiscoveryMethod
import com.lenovo.mesh.ipv6diag.data.model.Nat64PrefixEntry
import com.lenovo.mesh.ipv6diag.data.model.XlatSubTestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executor
import kotlin.coroutines.resume

private const val IPV4ONLY_ARPA = "ipv4only.arpa"
private const val WELL_KNOWN_PREFIX = "64:ff9b::"

// RFC 7050 §4: ipv4only.arpa has A records 192.0.0.170 and 192.0.0.171
private val KNOWN_IPV4_BYTES = byteArrayOf(192.toByte(), 0, 0, 170.toByte())

suspend fun discoverNat64Prefix(context: Context, network: Network): NAT64PrefixResult =
    withContext(Dispatchers.IO) {
        val entries = mutableListOf<Nat64PrefixEntry>()

        // Method 1: Query ipv4only.arpa for AAAA (RFC 7050) — API 29+ uses DnsResolver
        val rfc7050Entries = queryRfc7050(context, network)
        entries.addAll(rfc7050Entries)

        // Method 2: Probe well-known prefix directly if not already found
        val hasWellKnown = entries.any { it.isWellKnown }
        if (!hasWellKnown) {
            val wellKnownFound = probeWellKnownPrefix(network)
            if (wellKnownFound) {
                entries.add(0, Nat64PrefixEntry(
                    prefix = "64:ff9b::/96",
                    prefixLengthBits = 96,
                    discoveryMethod = Nat64DiscoveryMethod.WELL_KNOWN_PROBE,
                    isWellKnown = true,
                ))
            }
        }

        if (entries.isEmpty()) {
            return@withContext NAT64PrefixResult(
                entries = emptyList(),
                preferredPrefix = null,
                status = XlatSubTestStatus.FAIL,
                failureReason = "no NAT64 prefix discovered via RFC7050 DNS or well-known probe",
            )
        }

        val preferred = entries.firstOrNull { it.isWellKnown }?.prefix ?: entries.first().prefix
        NAT64PrefixResult(entries = entries, preferredPrefix = preferred, status = XlatSubTestStatus.PASS)
    }

private suspend fun queryRfc7050(context: Context, network: Network): List<Nat64PrefixEntry> {
    val addresses: List<Inet6Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        queryAAAA_api29(context, network, IPV4ONLY_ARPA)
    } else {
        queryAAAA_legacy(network, IPV4ONLY_ARPA)
    }

    return addresses.mapNotNull { addr ->
        extractNat64Prefix(addr.address)?.let { (prefix, length) ->
            Nat64PrefixEntry(
                prefix = prefix,
                prefixLengthBits = length,
                discoveryMethod = Nat64DiscoveryMethod.RFC7050_DNS,
                isWellKnown = prefix == "64:ff9b::/96",
            )
        }
    }.distinctBy { it.prefix }
}

private suspend fun probeWellKnownPrefix(network: Network): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            // Attempt a non-blocking address creation for a well-known NAT64 address
            val testAddr = InetAddress.getByName("64:ff9b::c000:00aa") as? Inet6Address ?: return@runCatching false
            val socket = network.socketFactory.createSocket()
            socket.use {
                it.connect(java.net.InetSocketAddress(testAddr, 80), 3000)
            }
            true
        }.getOrDefault(false)
    }

// RFC 6052 §2.2: extract prefix from synthesised IPv6 address given known IPv4 192.0.0.170
private fun extractNat64Prefix(ipv6Bytes: ByteArray): Pair<String, Int>? {
    if (ipv6Bytes.size != 16) return null
    // Try /96: IPv4 at bytes [12..15]
    if (ipv6Bytes.sliceArray(12..15).contentEquals(KNOWN_IPV4_BYTES)) {
        val prefixBytes = ipv6Bytes.sliceArray(0..11) + ByteArray(4)
        return formatPrefix(prefixBytes, 96)
    }
    // Try /64: IPv4 at bytes [9..12], byte 8 must be 0
    if (ipv6Bytes[8] == 0.toByte() && ipv6Bytes.sliceArray(9..12).contentEquals(KNOWN_IPV4_BYTES)) {
        val prefixBytes = ipv6Bytes.sliceArray(0..7) + ByteArray(8)
        return formatPrefix(prefixBytes, 64)
    }
    return null
}

private fun formatPrefix(bytes: ByteArray, length: Int): Pair<String, Int> {
    val addr = Inet6Address.getByAddress(null, bytes, null)
    val compressed = addr.hostAddress ?: return Pair("unknown", length)
    // Strip trailing zeros for clean display
    val clean = compressed.trimEnd(':').let { if (it.endsWith(":")) "$it:" else it }
    return Pair("$clean/$length", length)
}

@Suppress("DEPRECATION")
private fun queryAAAA_legacy(network: Network, hostname: String): List<Inet6Address> =
    runCatching {
        val bound = java.net.NetworkInterface.getByName("") // workaround: use network.bindSocket
        val socket = java.net.DatagramSocket()
        network.bindSocket(socket)
        socket.close()
        InetAddress.getAllByName(hostname)
            .filterIsInstance<Inet6Address>()
            .filter { !it.isLinkLocalAddress }
    }.getOrDefault(emptyList())

private suspend fun queryAAAA_api29(context: Context, network: Network, hostname: String): List<Inet6Address> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
    return suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        val executor = Executor { it.run() }
        DnsResolver.getInstance().query(
            network,
            hostname,
            DnsResolver.TYPE_AAAA,
            DnsResolver.FLAG_EMPTY,
            executor,
            signal,
            object : DnsResolver.Callback<List<InetAddress>> {
                override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                    cont.resume(answer.filterIsInstance<Inet6Address>().filter { !it.isLinkLocalAddress })
                }
                override fun onError(error: DnsResolver.DnsException) {
                    cont.resume(emptyList())
                }
            }
        )
    }
}
