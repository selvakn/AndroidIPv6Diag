package selvakn.ipv6diag.diagnostic

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import selvakn.ipv6diag.data.model.ClatQualityResult
import selvakn.ipv6diag.data.model.NetworkInfo
import selvakn.ipv6diag.data.model.XlatSubTestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

suspend fun assessClatQuality(
    context: Context,
    network: Network,
    networkInfo: NetworkInfo,
    serverIPv4: String?,
    serverIPv6: String?,
): ClatQualityResult = withContext(Dispatchers.IO) {
    if (!networkInfo.clatPresent || networkInfo.clatInterfaceName == null) {
        return@withContext ClatQualityResult(
            interfaceName = "",
            clatIPv4Address = null,
            interfaceMtu = null,
            effectiveIPv4Mtu = null,
            clatLatencyMs = null,
            nativeIPv6LatencyMs = null,
            latencyDeltaMs = null,
            status = XlatSubTestStatus.SKIPPED,
            failureReason = "no CLAT interface detected",
        )
    }

    val ifaceName = networkInfo.clatInterfaceName
    val clatIPv4 = networkInfo.clatSyntheticIPv4

    val mtu = readInterfaceMtu(context, network, ifaceName)
    val effectiveMtu = mtu?.let { it - 20 } // subtract IPv6 header overhead

    // Measure latency via CLAT (ping IPv4 address of server)
    val clatLatency = serverIPv4?.let { measurePingLatency(it, isIPv6 = false) }

    // Measure native IPv6 latency for comparison
    val ipv6Latency = serverIPv6?.let { measurePingLatency(it, isIPv6 = true) }

    val delta = if (clatLatency != null && ipv6Latency != null) clatLatency - ipv6Latency else null

    val status = when {
        mtu == null && clatLatency == null -> XlatSubTestStatus.FAIL
        else -> XlatSubTestStatus.PASS
    }

    ClatQualityResult(
        interfaceName = ifaceName,
        clatIPv4Address = clatIPv4,
        interfaceMtu = mtu,
        effectiveIPv4Mtu = effectiveMtu,
        clatLatencyMs = clatLatency,
        nativeIPv6LatencyMs = ipv6Latency,
        latencyDeltaMs = delta,
        status = status,
        failureReason = if (status == XlatSubTestStatus.FAIL) "could not read CLAT MTU or measure latency" else null,
    )
}

private fun readInterfaceMtu(context: Context, network: Network, ifaceName: String): Int? {
    // API 29+: LinkProperties.getLinkMtu()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(network)
        val mtu = lp?.mtu
        if (mtu != null && mtu > 0) return mtu
    }
    // Fallback: read /sys/class/net/<iface>/mtu (world-readable on Android)
    return runCatching {
        BufferedReader(FileReader(File("/sys/class/net/$ifaceName/mtu"))).use {
            it.readLine()?.trim()?.toInt()
        }
    }.getOrNull()
}

private fun measurePingLatency(target: String, isIPv6: Boolean): Long? = runCatching {
    val cmd = if (isIPv6) {
        arrayOf("ping6", "-c", "3", "-W", "5", target)
    } else {
        arrayOf("ping", "-c", "3", "-W", "5", target)
    }
    val proc = Runtime.getRuntime().exec(cmd)
    val output = proc.inputStream.bufferedReader().readText()
    proc.waitFor()
    // Parse "rtt min/avg/max/mdev = X/Y/Z/W ms"
    val rttRegex = Regex("""rtt\s+\S+\s+=\s+[\d.]+/([\d.]+)/""")
    rttRegex.find(output)?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()
}.getOrNull()
