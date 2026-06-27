package selvakn.ipv6diag.diagnostic

import selvakn.ipv6diag.data.model.AddressFamily
import selvakn.ipv6diag.data.model.TestResult
import selvakn.ipv6diag.data.model.TestStatus
import selvakn.ipv6diag.data.model.TestType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private val RTT_REGEX = Regex("""rtt min/avg/max/mdev = [\d.]+/([\d.]+)/""")
private val LOSS_REGEX = Regex("""(\d+)% packet loss""")

suspend fun runIcmpTest(
    sessionId: String,
    targetIp: String,
    addressFamily: AddressFamily,
): TestResult = withContext(Dispatchers.IO) {
    val binary = if (addressFamily == AddressFamily.IPv6) "ping6" else "ping"
    val start = System.currentTimeMillis()

    try {
        val process = Runtime.getRuntime().exec(arrayOf(binary, "-c", "3", "-W", "5", targetIp))
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        val latency = System.currentTimeMillis() - start

        if (exitCode != 0) {
            val errOutput = process.errorStream.bufferedReader().readText()
            return@withContext TestResult(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                testType = TestType.ICMP,
                addressFamily = addressFamily,
                status = TestStatus.FAIL,
                latencyMs = latency,
                failureReason = "ping failed: ${errOutput.take(120).ifEmpty { "unreachable" }}",
                resolvedAddress = targetIp,
            )
        }

        val avgRtt = RTT_REGEX.find(output)?.groupValues?.get(1)?.toLongOrNull()
        val lossPercent = LOSS_REGEX.find(output)?.groupValues?.get(1)?.toFloatOrNull()?.div(100f)

        TestResult(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            testType = TestType.ICMP,
            addressFamily = addressFamily,
            status = if ((lossPercent ?: 0f) < 1f) TestStatus.PASS else TestStatus.FAIL,
            latencyMs = avgRtt ?: latency,
            packetLoss = lossPercent,
            resolvedAddress = targetIp,
            failureReason = if ((lossPercent ?: 0f) >= 1f) "100% packet loss" else null,
        )
    } catch (e: Exception) {
        val isMissing = e.message?.contains("No such file") == true ||
            e.message?.contains("not found") == true
        TestResult(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            testType = TestType.ICMP,
            addressFamily = addressFamily,
            status = if (isMissing) TestStatus.SKIPPED else TestStatus.FAIL,
            latencyMs = System.currentTimeMillis() - start,
            failureReason = if (isMissing) "ICMP binary ($binary) not available on this device"
                           else e.message ?: "exec failed",
            resolvedAddress = targetIp,
        )
    }
}
