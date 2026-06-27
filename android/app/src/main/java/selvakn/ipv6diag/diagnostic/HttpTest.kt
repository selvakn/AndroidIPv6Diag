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

private const val TIMEOUT_SECONDS = 10L

fun buildHttpClient(network: Network): OkHttpClient =
    OkHttpClient.Builder()
        .socketFactory(network.socketFactory)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

suspend fun runHttpTest(
    network: Network,
    sessionId: String,
    serverIp: String,
    port: Int,
    addressFamily: AddressFamily,
): TestResult = withContext(Dispatchers.IO) {
    val client = buildHttpClient(network)
    val urlIp = if (addressFamily == AddressFamily.IPv6) "[$serverIp]" else serverIp
    val url = "http://$urlIp:$port/diag"

    val start = System.currentTimeMillis()
    try {
        val request = okhttp3.Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val latency = System.currentTimeMillis() - start
            if (!response.isSuccessful) {
                return@withContext TestResult(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    testType = TestType.HTTP,
                    addressFamily = addressFamily,
                    status = TestStatus.FAIL,
                    latencyMs = latency,
                    failureReason = "unexpected server response: ${response.code}",
                )
            }
            val body = response.body?.string() ?: ""
            val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            val confirmedFamily = json?.get("address_family")?.jsonPrimitive?.content

            TestResult(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                testType = TestType.HTTP,
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
            testType = TestType.HTTP,
            addressFamily = addressFamily,
            status = TestStatus.FAIL,
            latencyMs = System.currentTimeMillis() - start,
            failureReason = e.message ?: "connection failed",
            resolvedAddress = serverIp,
        )
    }
}
