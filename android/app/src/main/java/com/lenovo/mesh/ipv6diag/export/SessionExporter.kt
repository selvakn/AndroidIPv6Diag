package com.lenovo.mesh.ipv6diag.export

import com.lenovo.mesh.ipv6diag.data.model.DiagnosticSession
import com.lenovo.mesh.ipv6diag.data.model.TestStatus
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
private val prettyJson = Json { prettyPrint = true }

object SessionExporter {

    fun exportAsText(session: DiagnosticSession): String = buildString {
        appendLine("=== IPv6 Diagnostic Report ===")
        appendLine("Date     : ${dateFormat.format(Date(session.timestamp))}")
        appendLine("Status   : ${session.status}")
        appendLine("Server   : ${session.serverEndpoint.hostname}")
        session.abortReason?.let { appendLine("Aborted  : $it") }
        appendLine()

        appendLine("--- Network Info ---")
        val ni = session.networkInfo
        appendLine("Mobile data   : ${if (ni.mobileDataEnabled) "enabled" else "DISABLED"}")
        appendLine("Interface     : ${ni.cellularInterfaceName ?: "unknown"}")
        appendLine("IPv4 address  : ${ni.cellularIPv4Address ?: "none"}")
        appendLine("IPv6 addresses: ${ni.cellularIPv6Addresses.joinToString(", ").ifEmpty { "none" }}")
        appendLine("Native IPv6   : ${ni.hasNativeIPv6}")
        appendLine("CLAT / 464XLAT: ${if (ni.clatPresent) "present (${ni.clatInterfaceName})" else "not detected"}")
        ni.clatSyntheticIPv4?.let { appendLine("CLAT IPv4     : $it") }
        appendLine("DNS resolvers : ${ni.dnsServers.joinToString(", ").ifEmpty { "unknown" }}")
        if (ni.dnsServerNames.any { it.isNotEmpty() }) {
            appendLine("DNS names     : ${ni.dnsServerNames.filter { it.isNotEmpty() }.joinToString(", ")}")
        }
        appendLine("Android API   : ${ni.apiLevel}")
        appendLine()

        appendLine("--- Test Results ---")
        session.testResults.forEach { r ->
            val mark = when (r.status) {
                TestStatus.PASS -> "PASS"
                TestStatus.FAIL -> "FAIL"
                TestStatus.SKIPPED -> "SKIP"
                TestStatus.ABORTED -> "ABRT"
            }
            val latency = r.latencyMs?.let { "${it}ms" } ?: "-"
            val extra = buildList {
                r.resolvedAddress?.let { add("addr=$it") }
                r.serverConfirmedFamily?.let { add("server=$it") }
                r.packetLoss?.let { add("loss=${(it * 100).toInt()}%") }
                r.failureReason?.let { add("reason=$it") }
            }.joinToString(" ")
            appendLine("  [${r.testType}][${r.addressFamily}] $mark  latency=$latency  $extra")
        }

        val passed = session.testResults.count { it.status == TestStatus.PASS }
        val total = session.testResults.size
        appendLine()
        appendLine("Summary: $passed/$total tests passed")
        appendLine("==============================")
    }

    fun exportAsJson(session: DiagnosticSession): String =
        prettyJson.encodeToString(DiagnosticSession.serializer(), session)
}
