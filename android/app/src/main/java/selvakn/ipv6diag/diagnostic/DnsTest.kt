package selvakn.ipv6diag.diagnostic

import android.annotation.SuppressLint
import android.content.Context
import android.net.DnsResolver
import android.net.Network
import android.os.Build
import selvakn.ipv6diag.data.model.AddressFamily
import selvakn.ipv6diag.data.model.TestResult
import selvakn.ipv6diag.data.model.TestStatus
import selvakn.ipv6diag.data.model.TestType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume

private const val DNS_TIMEOUT_MS = 5_000L

/**
 * Queries A (IPv4) and AAAA (IPv6) records for [hostname] using the system/carrier
 * resolver bound to [network].
 *
 * On API 29+: uses DnsResolver for explicit record-type queries.
 * On API 26–28: falls back to InetAddress.getAllByName after binding to network.
 */
suspend fun runDnsTests(
    context: Context,
    network: Network,
    sessionId: String,
    hostname: String,
): List<TestResult> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        listOf(
            queryDnsRecord(context, network, sessionId, hostname, isIPv6 = false),
            queryDnsRecord(context, network, sessionId, hostname, isIPv6 = true),
        )
    } else {
        fallbackDnsTests(network, sessionId, hostname)
    }
}

@SuppressLint("NewApi")
private suspend fun queryDnsRecord(
    context: Context,
    network: Network,
    sessionId: String,
    hostname: String,
    isIPv6: Boolean,
): TestResult = withContext(Dispatchers.IO) {
    val family = if (isIPv6) AddressFamily.IPv6 else AddressFamily.IPv4
    val type = if (isIPv6) DnsResolver.TYPE_AAAA else DnsResolver.TYPE_A
    val start = System.currentTimeMillis()

    val resolved = withTimeoutOrNull(DNS_TIMEOUT_MS) {
        suspendCancellableCoroutine<List<InetAddress>?> { cont ->
            DnsResolver.getInstance().query(
                network, hostname, type, DnsResolver.FLAG_EMPTY,
                Executors.newSingleThreadExecutor(),
                null,
                object : DnsResolver.Callback<List<InetAddress>> {
                    override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                        cont.resume(answer)
                    }
                    override fun onError(error: DnsResolver.DnsException) {
                        cont.resume(null)
                    }
                }
            )
        }
    }

    val latency = System.currentTimeMillis() - start
    if (resolved.isNullOrEmpty()) {
        TestResult(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            testType = TestType.DNS,
            addressFamily = family,
            status = TestStatus.FAIL,
            latencyMs = latency,
            failureReason = if (resolved == null) "DNS timeout" else "no ${if (isIPv6) "AAAA" else "A"} records returned",
        )
    } else {
        TestResult(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            testType = TestType.DNS,
            addressFamily = family,
            status = TestStatus.PASS,
            latencyMs = latency,
            resolvedAddress = resolved.first().hostAddress,
        )
    }
}

private suspend fun fallbackDnsTests(
    network: Network,
    sessionId: String,
    hostname: String,
): List<TestResult> = withContext(Dispatchers.IO) {
    val start = System.currentTimeMillis()
    val results = mutableListOf<TestResult>()

    try {
        // Bind a temp socket to the network so InetAddress uses the carrier resolver
        val socket = java.net.DatagramSocket()
        network.bindSocket(socket)
        socket.close()

        val addresses = InetAddress.getAllByName(hostname)
        val latency = System.currentTimeMillis() - start

        val ipv4 = addresses.firstOrNull { it is java.net.Inet4Address }
        val ipv6 = addresses.firstOrNull { it is java.net.Inet6Address && !it.isLinkLocalAddress }

        results += TestResult(
            id = UUID.randomUUID().toString(), sessionId = sessionId,
            testType = TestType.DNS, addressFamily = AddressFamily.IPv4,
            status = if (ipv4 != null) TestStatus.PASS else TestStatus.FAIL,
            latencyMs = latency, resolvedAddress = ipv4?.hostAddress,
            failureReason = if (ipv4 == null) "no A record returned" else null,
        )
        results += TestResult(
            id = UUID.randomUUID().toString(), sessionId = sessionId,
            testType = TestType.DNS, addressFamily = AddressFamily.IPv6,
            status = if (ipv6 != null) TestStatus.PASS else TestStatus.FAIL,
            latencyMs = latency, resolvedAddress = ipv6?.hostAddress,
            failureReason = if (ipv6 == null) "no AAAA record returned (may indicate DNS64 synthesis)" else null,
        )
    } catch (e: Exception) {
        val latency = System.currentTimeMillis() - start
        for (family in listOf(AddressFamily.IPv4, AddressFamily.IPv6)) {
            results += TestResult(
                id = UUID.randomUUID().toString(), sessionId = sessionId,
                testType = TestType.DNS, addressFamily = family,
                status = TestStatus.FAIL, latencyMs = latency,
                failureReason = e.message ?: "DNS resolution failed",
            )
        }
    }
    results
}
