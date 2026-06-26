package com.lenovo.mesh.ipv6diag.diagnostic

import android.net.Network
import com.lenovo.mesh.ipv6diag.data.model.ClatQualityResult
import com.lenovo.mesh.ipv6diag.data.model.NAT64PrefixResult
import com.lenovo.mesh.ipv6diag.data.model.PlatVerificationResult
import com.lenovo.mesh.ipv6diag.data.model.XlatSubTestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

suspend fun verifyPlatPath(
    network: Network,
    serverIPv4: String?,
    serverPort: Int,
    nat64Result: NAT64PrefixResult,
    clatResult: ClatQualityResult,
): PlatVerificationResult = withContext(Dispatchers.IO) {
    if (serverIPv4 == null) {
        return@withContext PlatVerificationResult(
            serverObservedIPv6Source = null,
            decodedEmbeddedIPv4 = null,
            matchesClatIPv4 = false,
            platIPv6Prefix = null,
            prefixMatchesDiscovered = false,
            status = XlatSubTestStatus.SKIPPED,
            failureReason = "no server IPv4 address available for PLAT path test",
        )
    }

    if (clatResult.status == XlatSubTestStatus.SKIPPED) {
        return@withContext PlatVerificationResult(
            serverObservedIPv6Source = null,
            decodedEmbeddedIPv4 = null,
            matchesClatIPv4 = false,
            platIPv6Prefix = null,
            prefixMatchesDiscovered = false,
            status = XlatSubTestStatus.SKIPPED,
            failureReason = "CLAT not present — PLAT verification skipped",
        )
    }

    // Send HTTP request to the server's IPv4 address, which will arrive via CLAT→PLAT
    val clientAddress = runCatching {
        val client = OkHttpClient.Builder()
            .socketFactory(network.socketFactory)
            .connectTimeout(10L, TimeUnit.SECONDS)
            .readTimeout(10L, TimeUnit.SECONDS)
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    listOf(InetAddress.getByName(serverIPv4))
            })
            .build()
        val request = Request.Builder().url("http://$serverIPv4:$serverPort/diag").get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            Json.parseToJsonElement(body).jsonObject["client_address"]?.jsonPrimitive?.content
        }
    }.getOrNull()

    if (clientAddress == null) {
        return@withContext PlatVerificationResult(
            serverObservedIPv6Source = null,
            decodedEmbeddedIPv4 = null,
            matchesClatIPv4 = false,
            platIPv6Prefix = null,
            prefixMatchesDiscovered = false,
            status = XlatSubTestStatus.FAIL,
            failureReason = "could not reach server via IPv4/CLAT path",
        )
    }

    // Check if the server saw an IPv6 source (confirming PLAT translation)
    val serverAddr = runCatching { InetAddress.getByName(clientAddress) }.getOrNull()
    if (serverAddr !is java.net.Inet6Address) {
        return@withContext PlatVerificationResult(
            serverObservedIPv6Source = clientAddress,
            decodedEmbeddedIPv4 = null,
            matchesClatIPv4 = false,
            platIPv6Prefix = null,
            prefixMatchesDiscovered = false,
            status = XlatSubTestStatus.FAIL,
            failureReason = "server observed IPv4 source ($clientAddress) — PLAT translation not occurring",
        )
    }

    // Decode embedded IPv4 using discovered NAT64 prefix
    val prefixLen = nat64Result.preferredPrefix?.substringAfter('/')?.toIntOrNull() ?: 96
    val ipv6Bytes = serverAddr.address
    val ipv4Bytes = when (prefixLen) {
        96 -> ipv6Bytes.sliceArray(12..15)
        64 -> ipv6Bytes.sliceArray(9..12)
        else -> ipv6Bytes.sliceArray(12..15)
    }
    val decodedIPv4 = runCatching { InetAddress.getByAddress(ipv4Bytes).hostAddress }.getOrNull()

    // Extract prefix from PLAT address for cross-check
    val platPrefixAddr = runCatching {
        java.net.Inet6Address.getByAddress(null, ipv6Bytes.copyOf(16).also { b ->
            // Zero out the IPv4 portion to get the prefix
            for (i in (prefixLen / 8) until 16) b[i] = 0
        }, null as java.net.NetworkInterface?).hostAddress
    }.getOrNull()
    val platPrefix = platPrefixAddr?.let { "$it/$prefixLen" }

    val matchesClatIPv4 = decodedIPv4 != null && decodedIPv4 == clatResult.clatIPv4Address
    val prefixMatchesDiscovered = platPrefix != null && platPrefix == nat64Result.preferredPrefix

    PlatVerificationResult(
        serverObservedIPv6Source = clientAddress,
        decodedEmbeddedIPv4 = decodedIPv4,
        matchesClatIPv4 = matchesClatIPv4,
        platIPv6Prefix = platPrefix,
        prefixMatchesDiscovered = prefixMatchesDiscovered,
        status = if (decodedIPv4 != null) XlatSubTestStatus.PASS else XlatSubTestStatus.FAIL,
        failureReason = if (decodedIPv4 == null) "could not decode embedded IPv4 from PLAT address" else null,
    )
}
