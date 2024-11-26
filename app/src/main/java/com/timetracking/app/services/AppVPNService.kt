import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.timetracking.app.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

class AppVPNService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val maxReconnectAttempts = 3
    private var currentAttempt = 0
    private val connectionStateFlow = MutableStateFlow<VPNState>(VPNState.Disconnected)

    sealed class VPNState {
        object Connected : VPNState()
        object Disconnected : VPNState()
        data class Error(val message: String) : VPNState()
        object Connecting : VPNState()
    }

    override fun onCreate() {
        super.onCreate()
        initializeVPNConnection()
        monitorConnection()
    }

    private fun initializeVPNConnection() {
        scope.launch {
            try {
                connectionStateFlow.emit(VPNState.Connecting)
                if (!checkPPPDPermissions()) {
                    throw SecurityException("pppd permissions not granted")
                }
                setupVPNInterface()
                startPPTPConnection()
                scheduleConnectionCheck()
                connectionStateFlow.emit(VPNState.Connected)
            } catch (e: Exception) {
                connectionStateFlow.emit(VPNState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private fun setupVPNInterface() {
        val builder = Builder()
            .addAddress("192.168.2.1", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setSession("AppGrec")
            .setMtu(1500)
            .setBlocking(true)
            .setConfigureIntent(getPendingIntent())

        vpnInterface = builder.establish()
    }

    private suspend fun startPPTPConnection() {
        withTimeout(30_000) {
            val credentials = VPNConfig.getCredentials()
            val process = Runtime.getRuntime().exec(arrayOf(
                "pppd",
                "noauth",
                "refuse-eap",
                "nodeflate",
                "nobsdcomp",
                "nodefaultroute",
                "pty",
                "pptp ${VPNConfig.SERVER_ADDRESS} --nolaunchpppd",
                "user",
                credentials.username,
                "password",
                credentials.password,
                "mtu 1496",
                "mru 1496"
            ))
            process.waitFor()
        }
    }

    private fun monitorConnection() {
        scope.launch {
            connectionStateFlow.collect { state ->
                when (state) {
                    is VPNState.Error -> handleConnectionError(IOException(state.message))
                    is VPNState.Disconnected -> attemptReconnection()
                    is VPNState.Connecting -> startConnectionTimeout()
                    else -> Unit
                }
            }
        }
    }

    private fun attemptReconnection() {
        if (currentAttempt < maxReconnectAttempts) {
            currentAttempt++
            scope.launch {
                delay(exponentialBackoff(currentAttempt))
                initializeVPNConnection()
            }
        }
    }

    private fun exponentialBackoff(attempt: Int): Long {
        return (2_000L * (1L shl (attempt - 1))).coerceAtMost(30_000L)
    }

    private fun scheduleConnectionCheck() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive) {
                delay(60_000)
                if (!isConnectionActive()) {
                    connectionStateFlow.emit(VPNState.Disconnected)
                }
            }
        }
    }

    private fun startConnectionTimeout() {
        scope.launch {
            delay(30_000)
            if (connectionStateFlow.value == VPNState.Connecting) {
                connectionStateFlow.emit(VPNState.Error("Connection timeout"))
            }
        }
    }

    private fun handleConnectionError(error: Exception) {
        if (currentAttempt < maxReconnectAttempts) {
            currentAttempt++
            scope.launch {
                delay(5000)
                initializeVPNConnection()
            }
        } else {
            stopSelf()
        }
    }

    private fun checkPPPDPermissions(): Boolean {
        return try {
            Runtime.getRuntime().exec("which pppd").waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun isConnectionActive(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 ${VPNConfig.SERVER_ADDRESS}")
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        vpnInterface?.close()
        Runtime.getRuntime().exec("pkill pppd")
        scope.launch {
            connectionStateFlow.emit(VPNState.Disconnected)
        }
    }

    private fun getPendingIntent() = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )
}