package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.network.CheckTrialRequest
import com.example.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * LicenseViewModel: Dedicated ViewModel managing device licensing,
 * authoritative 24-hour trial checks, and real-time live expiration.
 */
class LicenseViewModel(application: Application) : AndroidViewModel(application) {

    private val _accessState = MutableStateFlow<AppAccessState>(AppAccessState.Checking)
    val accessState: StateFlow<AppAccessState> = _accessState.asStateFlow()

    private val _isServerConnected = MutableStateFlow(true)
    val isServerConnected: StateFlow<Boolean> = _isServerConnected.asStateFlow()

    private var expirationJob: Job? = null
    @Volatile private var stableDeviceId: String? = null

    val deviceId: String
        get() = stableDeviceId ?: getHardwareDeviceId()

    private suspend fun resolveStableDeviceId(): String = withContext(Dispatchers.IO) {
        stableDeviceId?.let { return@withContext it }
        val appSetId = runCatching {
            val info = com.google.android.gms.appset.AppSet.getClient(getApplication<Application>()).appSetIdInfo
            com.google.android.gms.tasks.Tasks.await(info).id
        }.getOrNull()?.trim().orEmpty()
        if (appSetId.isNotBlank()) {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(appSetId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            stableDeviceId = "DEV_APPSET_$digest"
        }
        stableDeviceId ?: getHardwareDeviceId()
    }

    fun getHardwareDeviceId(): String {
        val context = getApplication<Application>()
        val rawId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val finalDeviceId = if (rawId.isNotBlank() && rawId != "UNKNOWN_DEVICE") {
            "DEV_SEC_" + rawId.lowercase()
        } else {
            "DEV_SEC_" + "${Build.BOARD}_${Build.BRAND}_${Build.DEVICE}".hashCode().toString().replace("-", "")
        }
        return finalDeviceId
    }

    // 2. Background Live Runtime Expiration Check
    fun scheduleExpiration(expiresAt: Long?) {
        expirationJob?.cancel()
        if (expiresAt == null) return
        val delayMs = expiresAt - System.currentTimeMillis()
        if (delayMs <= 0) {
            _accessState.value = AppAccessState.Denied("مهلت تست ۲۴ ساعته به پایان رسید.")
            return
        }
        expirationJob = viewModelScope.launch(Dispatchers.Main) {
            delay(delayMs)
            _accessState.value = AppAccessState.Denied("مهلت تست ۲۴ ساعته به پایان رسید.")
        }
    }

    /**
     * Authoritative check with server for 24h trial and active subscriptions
     */
    fun checkTrialAndLicenseStatus(serverUrl: String = "https://api.gamenermayket.ir/") {
        viewModelScope.launch(Dispatchers.IO) {
            _accessState.value = AppAccessState.Checking
            val currentDeviceId = resolveStableDeviceId()
            try {
                val api = NetworkClient.getApi(serverUrl)
                _isServerConnected.value = true
                val trialCheck = api.checkTrialStatus(
                    CheckTrialRequest(
                        deviceId = currentDeviceId,
                        altDeviceId = currentDeviceId,
                        deviceName = Build.MODEL ?: "Unknown"
                    )
                )

                if (trialCheck.isExpired) {
                    _accessState.value = AppAccessState.Denied(
                        trialCheck.responseMessage.ifBlank { "مهلت تست ۲۴ ساعته به پایان رسید." }
                    )
                } else {
                    val serverNow = trialCheck.serverTime ?: System.currentTimeMillis()
                    val expiresAt = trialCheck.expiresAt ?: (serverNow + trialCheck.remainingMilliseconds)
                    _accessState.value = AppAccessState.Allowed(expiresAt, "TRIAL")
                    scheduleExpiration(expiresAt)
                }
            } catch (e: Exception) {
                _isServerConnected.value = false
                // امنیت قطعی: در زمان قطعی ارتباط به هیچ وجه نباید ترایال لوکال فعال شود
                _accessState.value = AppAccessState.Denied(
                    "عدم برقراری ارتباط با سرور اعتبارسنجی. لطفاً اتصال اینترنت خود را بررسی نمایید."
                )
            }
        }
    }

    fun resetAccessState() {
        _accessState.value = AppAccessState.Checking
    }

    override fun onCleared() {
        super.onCleared()
        expirationJob?.cancel()
    }
}
