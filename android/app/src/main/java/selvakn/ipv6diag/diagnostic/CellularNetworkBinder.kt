package selvakn.ipv6diag.diagnostic

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.telephony.TelephonyManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CellularNetworkUnavailableException(message: String) : Exception(message)

class CellularNetworkBinder(private val context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isMobileDataEnabled(): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.isDataEnabled
    }

    /**
     * Acquires the cellular Network and runs [block] with it.
     * Throws [CellularNetworkUnavailableException] if mobile data is off or no cellular
     * network is available within [timeoutMs].
     */
    suspend fun <T> withCellularNetwork(timeoutMs: Long = 10_000L, block: suspend (Network) -> T): T {
        if (!isMobileDataEnabled()) {
            throw CellularNetworkUnavailableException("Mobile data is disabled — enable it to run tests")
        }

        val network = acquireCellularNetwork(timeoutMs)
        return block(network)
    }

    private suspend fun acquireCellularNetwork(timeoutMs: Long): Network =
        withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resume(network)
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) cont.resumeWithException(
                            CellularNetworkUnavailableException("No cellular network available")
                        )
                    }
                }

                cm.requestNetwork(request, callback, timeoutMs.toInt())

                cont.invokeOnCancellation {
                    cm.unregisterNetworkCallback(callback)
                }
            }
        }
}
