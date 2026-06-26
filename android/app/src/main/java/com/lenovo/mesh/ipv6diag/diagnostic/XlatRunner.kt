package com.lenovo.mesh.ipv6diag.diagnostic

import android.content.Context
import android.net.Network
import com.lenovo.mesh.ipv6diag.data.model.NetworkInfo
import com.lenovo.mesh.ipv6diag.data.model.XlatChainStatus
import com.lenovo.mesh.ipv6diag.data.model.XlatDiagnosticSummary
import com.lenovo.mesh.ipv6diag.data.model.XlatSubTestStatus

suspend fun runXlatDiagnostics(
    context: Context,
    network: Network,
    networkInfo: NetworkInfo,
    sessionId: String,
    serverIPv4: String?,
    serverIPv6: String?,
    serverPort: Int,
): XlatDiagnosticSummary {
    // Short-circuit: no CLAT interface → ABSENT
    if (!networkInfo.clatPresent) {
        val skipped = com.lenovo.mesh.ipv6diag.data.model.NAT64PrefixResult(
            entries = emptyList(), preferredPrefix = null, status = XlatSubTestStatus.SKIPPED,
            failureReason = "no CLAT interface — 464XLAT not present"
        )
        return XlatDiagnosticSummary(
            sessionId = sessionId,
            nat64Prefix = skipped,
            dns64Validation = com.lenovo.mesh.ipv6diag.data.model.DNS64ValidationResult(
                queriedHostname = "ipv4only.arpa", rawAAAARecords = emptyList(),
                decodedEmbeddedIPv4 = null, synthesisTested = false, prefixMatches = false,
                status = XlatSubTestStatus.SKIPPED, failureReason = "no CLAT interface"
            ),
            clatQuality = com.lenovo.mesh.ipv6diag.data.model.ClatQualityResult(
                interfaceName = "", clatIPv4Address = null, interfaceMtu = null,
                effectiveIPv4Mtu = null, clatLatencyMs = null, nativeIPv6LatencyMs = null,
                latencyDeltaMs = null, status = XlatSubTestStatus.SKIPPED,
                failureReason = "no CLAT interface"
            ),
            platVerification = com.lenovo.mesh.ipv6diag.data.model.PlatVerificationResult(
                serverObservedIPv6Source = null, decodedEmbeddedIPv4 = null,
                matchesClatIPv4 = false, platIPv6Prefix = null, prefixMatchesDiscovered = false,
                status = XlatSubTestStatus.SKIPPED, failureReason = "no CLAT interface"
            ),
            overallStatus = XlatChainStatus.ABSENT,
        )
    }

    // Run all sub-tests sequentially (each builds on the previous result)
    val nat64 = discoverNat64Prefix(context, network)
    val dns64 = validateDns64(context, network, nat64)
    val clatQuality = assessClatQuality(context, network, networkInfo, serverIPv4, serverIPv6)
    val platVerif = verifyPlatPath(network, serverIPv4, serverPort, nat64, clatQuality)

    val overall = computeOverallStatus(nat64, dns64, clatQuality, platVerif)

    return XlatDiagnosticSummary(
        sessionId = sessionId,
        nat64Prefix = nat64,
        dns64Validation = dns64,
        clatQuality = clatQuality,
        platVerification = platVerif,
        overallStatus = overall,
    )
}

private fun computeOverallStatus(
    nat64: com.lenovo.mesh.ipv6diag.data.model.NAT64PrefixResult,
    dns64: com.lenovo.mesh.ipv6diag.data.model.DNS64ValidationResult,
    clat: com.lenovo.mesh.ipv6diag.data.model.ClatQualityResult,
    plat: com.lenovo.mesh.ipv6diag.data.model.PlatVerificationResult,
): XlatChainStatus {
    if (nat64.status == XlatSubTestStatus.SKIPPED) return XlatChainStatus.ABSENT
    if (nat64.status == XlatSubTestStatus.FAIL) return XlatChainStatus.BROKEN
    val allPass = dns64.status == XlatSubTestStatus.PASS &&
        clat.status == XlatSubTestStatus.PASS &&
        plat.status == XlatSubTestStatus.PASS
    return if (allPass) XlatChainStatus.WORKING else XlatChainStatus.PARTIAL
}
