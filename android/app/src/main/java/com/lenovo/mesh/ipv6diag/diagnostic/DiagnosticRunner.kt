package com.lenovo.mesh.ipv6diag.diagnostic

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.lenovo.mesh.ipv6diag.data.model.AddressFamily
import com.lenovo.mesh.ipv6diag.data.model.DiagnosticSession
import com.lenovo.mesh.ipv6diag.data.model.ServerEndpoint
import com.lenovo.mesh.ipv6diag.data.model.SessionStatus
import com.lenovo.mesh.ipv6diag.data.model.TestResult
import com.lenovo.mesh.ipv6diag.data.model.TestStatus
import com.lenovo.mesh.ipv6diag.data.model.TestType
import com.lenovo.mesh.ipv6diag.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class TestFilter { ALL, HTTP_HTTPS, ICMP, DNS }

class DiagnosticRunner(
    private val context: Context,
    private val repository: SessionRepository,
    private val networkInfoCollector: NetworkInfoCollector,
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun runTests(
        endpoint: ServerEndpoint,
        filter: TestFilter = TestFilter.ALL,
    ): DiagnosticSession {
        val binder = CellularNetworkBinder(context)
        return binder.withCellularNetwork { network ->
            executeTests(network, endpoint, filter)
        }
    }

    private suspend fun executeTests(
        network: Network,
        endpoint: ServerEndpoint,
        filter: TestFilter,
    ): DiagnosticSession = coroutineScope {
        val sessionId = UUID.randomUUID().toString()
        val networkInfo = networkInfoCollector.collect(network)
        val networkChanged = AtomicBoolean(false)
        val allResults = mutableListOf<TestResult>()

        // Register network change detector
        val changeCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(lost: Network) {
                if (lost == network) networkChanged.set(true)
            }
        }
        cm.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build(),
            changeCallback
        )

        try {
            // Resolve server addresses using system resolver
            val ipv4Addr = resolveAddress(endpoint.hostname, isIPv6 = false)
            val ipv6Addr = resolveAddress(endpoint.hostname, isIPv6 = true)

            // Run selected test types
            val testTypes = when (filter) {
                TestFilter.ALL -> TestType.entries.toList()
                TestFilter.HTTP_HTTPS -> listOf(TestType.HTTP, TestType.HTTPS)
                TestFilter.ICMP -> listOf(TestType.ICMP)
                TestFilter.DNS -> listOf(TestType.DNS)
            }

            for (testType in testTypes) {
                if (networkChanged.get()) break

                val results = when (testType) {
                    TestType.HTTP -> buildList {
                        if (ipv4Addr != null) add(runHttpTest(network, sessionId, ipv4Addr, endpoint.httpPort, AddressFamily.IPv4))
                        if (ipv6Addr != null) add(runHttpTest(network, sessionId, ipv6Addr, endpoint.httpPort, AddressFamily.IPv6))
                    }
                    TestType.HTTPS -> buildList {
                        if (ipv4Addr != null) add(runHttpsTest(network, sessionId, endpoint.hostname, ipv4Addr, endpoint.httpsPort, AddressFamily.IPv4))
                        if (ipv6Addr != null) add(runHttpsTest(network, sessionId, endpoint.hostname, ipv6Addr, endpoint.httpsPort, AddressFamily.IPv6))
                    }
                    TestType.ICMP -> buildList {
                        if (ipv4Addr != null) add(runIcmpTest(sessionId, ipv4Addr, AddressFamily.IPv4))
                        if (ipv6Addr != null) add(runIcmpTest(sessionId, ipv6Addr, AddressFamily.IPv6))
                    }
                    TestType.DNS -> runDnsTests(context, network, sessionId, endpoint.hostname)
                }

                if (networkChanged.get()) {
                    // Abort in-progress results and break
                    allResults.addAll(results.map { r ->
                        if (r.status == TestStatus.PASS) r
                        else r.copy(status = TestStatus.ABORTED, failureReason = "network changed during test")
                    })
                    break
                }
                allResults.addAll(results)
            }

            val status = if (networkChanged.get()) SessionStatus.ABORTED else SessionStatus.COMPLETED
            val session = DiagnosticSession(
                id = sessionId,
                timestamp = System.currentTimeMillis(),
                serverEndpoint = endpoint,
                networkInfo = networkInfo,
                testResults = allResults,
                status = status,
                abortReason = if (networkChanged.get()) "network changed during test" else null,
            )
            repository.saveSession(session)
            session
        } finally {
            runCatching { cm.unregisterNetworkCallback(changeCallback) }
        }
    }

    private suspend fun resolveAddress(hostname: String, isIPv6: Boolean): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                InetAddress.getAllByName(hostname)
                    .firstOrNull { addr ->
                        if (isIPv6) addr is java.net.Inet6Address && !addr.isLinkLocalAddress
                        else addr is java.net.Inet4Address
                    }?.hostAddress
            }.getOrNull()
        }
}
