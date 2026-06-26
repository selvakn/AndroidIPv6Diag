package com.lenovo.mesh.ipv6diag.diagnostic

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.telephony.TelephonyManager
import com.lenovo.mesh.ipv6diag.data.model.NetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

class NetworkInfoCollector(private val context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun collect(network: Network): NetworkInfo = withContext(Dispatchers.IO) {
        val linkProps = cm.getLinkProperties(network)
        val netCaps = cm.getNetworkCapabilities(network)

        val allAddresses = linkProps?.linkAddresses?.map { it.address } ?: emptyList()
        val allInterfaces = linkProps?.interfaceName

        // Separate IPv4 and IPv6 addresses; exclude link-local IPv6 (fe80::/10)
        val ipv4Addr = allAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress
        val globalIPv6 = allAddresses
            .filter { it is java.net.Inet6Address && !it.isLinkLocalAddress }
            .map { it.hostAddress ?: "" }
            .filter { it.isNotEmpty() }

        // CLAT detection: look for interface names starting with "clat" or "v4-"
        val allIfaces = try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val clatIface = allIfaces.firstOrNull { iface ->
            iface.name.startsWith("clat", ignoreCase = true) ||
                iface.name.startsWith("v4-", ignoreCase = true)
        }
        val clatSyntheticIPv4 = clatIface?.inetAddresses?.toList()
            ?.firstOrNull { it is java.net.Inet4Address }?.hostAddress

        // DNS servers from link properties
        val dnsServers = linkProps?.dnsServers?.map { it.hostAddress ?: "" }
            ?.filter { it.isNotEmpty() } ?: emptyList()

        // Best-effort reverse DNS for server names (2s timeout)
        val dnsNames = dnsServers.map { ip ->
            try {
                withContext(Dispatchers.IO) {
                    InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip } ?: ""
                }
            } catch (_: Exception) { "" }
        }

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        NetworkInfo(
            cellularInterfaceName = allInterfaces,
            cellularIPv4Address = ipv4Addr,
            cellularIPv6Addresses = globalIPv6,
            hasNativeIPv6 = globalIPv6.isNotEmpty(),
            clatPresent = clatIface != null,
            clatInterfaceName = clatIface?.name,
            clatSyntheticIPv4 = clatSyntheticIPv4,
            clatIPv6Prefix = null, // Populated separately if detectable via DNS64 prefix
            dnsServers = dnsServers,
            dnsServerNames = dnsNames,
            mobileDataEnabled = tm.isDataEnabled,
            apiLevel = Build.VERSION.SDK_INT,
        )
    }
}
