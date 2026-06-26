package com.lenovo.mesh.ipv6diag.diagnostic

import android.content.Context
import android.net.DnsResolver
import android.net.Network
import android.os.Build
import android.os.CancellationSignal
import com.lenovo.mesh.ipv6diag.data.model.DNS64ValidationResult
import com.lenovo.mesh.ipv6diag.data.model.NAT64PrefixResult
import com.lenovo.mesh.ipv6diag.data.model.XlatSubTestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executor
import kotlin.coroutines.resume

private const val PROBE_HOSTNAME = "ipv4only.arpa"

// RFC 7050: known IPv4 addresses for ipv4only.arpa
private val KNOWN_IPV4_170 = byteArrayOf(192.toByte(), 0, 0, 170.toByte())
private val KNOWN_IPV4_171 = byteArrayOf(192.toByte(), 0, 0, 171.toByte())

suspend fun validateDns64(
    context: Context,
    network: Network,
    nat64Result: NAT64PrefixResult,
): DNS64ValidationResult = withContext(Dispatchers.IO) {
    val addresses: List<Inet6Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        queryAAAARecords_api29(context, network, PROBE_HOSTNAME)
    } else {
        queryAAAARecords_legacy(network, PROBE_HOSTNAME)
    }

    if (addresses.isEmpty()) {
        return@withContext DNS64ValidationResult(
            queriedHostname = PROBE_HOSTNAME,
            rawAAAARecords = emptyList(),
            decodedEmbeddedIPv4 = null,
            synthesisTested = true,
            prefixMatches = false,
            status = XlatSubTestStatus.FAIL,
            failureReason = "no AAAA records returned for $PROBE_HOSTNAME — DNS64 may not be active",
        )
    }

    val rawRecords = addresses.map { it.hostAddress ?: it.toString() }

    // Decode embedded IPv4 from the first synthesised address using the known IPv4
    val decodedIPv4 = addresses.firstNotNullOfOrNull { addr ->
        decodeEmbeddedIPv4(addr.address, nat64Result.preferredPrefix)
    }

    if (decodedIPv4 == null) {
        return@withContext DNS64ValidationResult(
            queriedHostname = PROBE_HOSTNAME,
            rawAAAARecords = rawRecords,
            decodedEmbeddedIPv4 = null,
            synthesisTested = true,
            prefixMatches = false,
            status = XlatSubTestStatus.FAIL,
            failureReason = "AAAA records returned but could not decode embedded IPv4 — prefix mismatch or native AAAA",
        )
    }

    // Verify the decoded IPv4 is one of the known ipv4only.arpa addresses
    val isKnownIPv4 = decodedIPv4 == "192.0.0.170" || decodedIPv4 == "192.0.0.171"
    val prefixMatches = nat64Result.preferredPrefix != null && isKnownIPv4

    DNS64ValidationResult(
        queriedHostname = PROBE_HOSTNAME,
        rawAAAARecords = rawRecords,
        decodedEmbeddedIPv4 = decodedIPv4,
        synthesisTested = true,
        prefixMatches = prefixMatches,
        status = if (prefixMatches) XlatSubTestStatus.PASS else XlatSubTestStatus.FAIL,
        failureReason = if (!prefixMatches) "decoded IPv4 ($decodedIPv4) does not match expected ipv4only.arpa addresses" else null,
    )
}

// Decode embedded IPv4 from IPv6 using the NAT64 prefix length
private fun decodeEmbeddedIPv4(ipv6Bytes: ByteArray, preferredPrefix: String?): String? {
    if (ipv6Bytes.size != 16) return null
    // Determine prefix length from preferredPrefix string
    val prefixLen = preferredPrefix?.substringAfter('/')?.toIntOrNull() ?: 96
    val ipv4Bytes = when (prefixLen) {
        96 -> ipv6Bytes.sliceArray(12..15)
        64 -> ipv6Bytes.sliceArray(9..12)
        56 -> byteArrayOf(ipv6Bytes[7], ipv6Bytes[9], ipv6Bytes[10], ipv6Bytes[11])
        48 -> byteArrayOf(ipv6Bytes[6], ipv6Bytes[7], ipv6Bytes[9], ipv6Bytes[10])
        40 -> byteArrayOf(ipv6Bytes[5], ipv6Bytes[6], ipv6Bytes[7], ipv6Bytes[9])
        32 -> ipv6Bytes.sliceArray(4..7)
        else -> return null
    }
    if (ipv4Bytes.size != 4) return null
    return InetAddress.getByAddress(ipv4Bytes).hostAddress
}

private suspend fun queryAAAARecords_api29(context: Context, network: Network, hostname: String): List<Inet6Address> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
    return suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        DnsResolver.getInstance().query(
            network, hostname, DnsResolver.TYPE_AAAA, DnsResolver.FLAG_EMPTY,
            Executor { it.run() }, signal,
            object : DnsResolver.Callback<List<InetAddress>> {
                override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                    cont.resume(answer.filterIsInstance<Inet6Address>().filter { !it.isLinkLocalAddress })
                }
                override fun onError(error: DnsResolver.DnsException) { cont.resume(emptyList()) }
            }
        )
    }
}

private fun queryAAAARecords_legacy(network: Network, hostname: String): List<Inet6Address> =
    runCatching {
        val socket = java.net.DatagramSocket()
        network.bindSocket(socket)
        socket.close()
        InetAddress.getAllByName(hostname).filterIsInstance<Inet6Address>().filter { !it.isLinkLocalAddress }
    }.getOrDefault(emptyList())
