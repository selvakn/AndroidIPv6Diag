package selvakn.ipv6diag.diagnostic

import android.net.Network
import selvakn.ipv6diag.data.model.AddressFamily
import selvakn.ipv6diag.data.model.TestResult
import selvakn.ipv6diag.data.model.TestStatus
import selvakn.ipv6diag.data.model.TestType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

suspend fun runHttpsTest(
    network: Network,
    sessionId: String,
    serverHostname: String,
    serverIp: String,
    port: Int,
    addressFamily: AddressFamily,
): TestResult = withContext(Dispatchers.IO) {
    // For HTTPS, use hostname in the URL so TLS SNI works, but force the IP via custom DNS.
    val client = OkHttpClient.Builder()
        .socketFactory(network.socketFactory)
        .connectTimeout(10L, TimeUnit.SECONDS)
        .readTimeout(10L, TimeUnit.SECONDS)
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> =
                listOf(java.net.InetAddress.getByName(serverIp))
        })
        .build()

    val url = "https://$serverHostname:$port/diag"
    val start = System.currentTimeMillis()
    try {
        val request = okhttp3.Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val latency = System.currentTimeMillis() - start
            if (!response.isSuccessful) {
                return@withContext TestResult(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    testType = TestType.HTTPS,
                    addressFamily = addressFamily,
                    status = TestStatus.FAIL,
                    latencyMs = latency,
                    failureReason = "unexpected server response: ${response.code}",
                    resolvedAddress = serverIp,
                )
            }
            val body = response.body?.string() ?: ""
            val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            val confirmedFamily = json?.get("address_family")?.jsonPrimitive?.content

            TestResult(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                testType = TestType.HTTPS,
                addressFamily = addressFamily,
                status = TestStatus.PASS,
                latencyMs = latency,
                serverConfirmedFamily = confirmedFamily,
                resolvedAddress = serverIp,
            )
        }
    } catch (e: Exception) {
        TestResult(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            testType = TestType.HTTPS,
            addressFamily = addressFamily,
            status = TestStatus.FAIL,
            latencyMs = System.currentTimeMillis() - start,
            failureReason = e.message ?: "connection failed",
            resolvedAddress = serverIp,
        )
    }
}
