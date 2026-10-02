package com.example.data.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NetworkLogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestampFormatted: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
    val method: String,
    val url: String,
    val statusCode: Int? = null,
    val durationMs: Long = 0,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val errorType: String? = null,
    val requestHeadersCount: Int = 0,
    val responseBodySnippet: String? = null
)

data class NetworkStatusInfo(
    val isConnected: Boolean = true,
    val targetBaseUrl: String = "https://api.gamenermayket.ir",
    val activeUrl: String = "https://api.gamenermayket.ir",
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val lastSuccessTimestamp: Long? = null,
    val lastErrorTimestamp: Long? = null,
    val lastErrorMessage: String? = null,
    val lastErrorType: String? = null
)

object NetworkLogger {
    private const val TAG = "NetworkLogger"
    private const val MAX_LOGS = 150

    private val _logs = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    val logs: StateFlow<List<NetworkLogEntry>> = _logs.asStateFlow()

    private val _status = MutableStateFlow(NetworkStatusInfo())
    val status: StateFlow<NetworkStatusInfo> = _status.asStateFlow()

    private val _latestErrorBanner = MutableStateFlow<String?>(null)
    val latestErrorBanner: StateFlow<String?> = _latestErrorBanner.asStateFlow()

    fun dismissErrorBanner() {
        _latestErrorBanner.value = null
    }

    fun updateActiveUrl(url: String) {
        _status.update { it.copy(activeUrl = url) }
    }

    fun recordLog(entry: NetworkLogEntry) {
        _logs.update { currentList ->
            (listOf(entry) + currentList).take(MAX_LOGS)
        }

        _status.update { current ->
            val total = current.totalRequests + 1
            val failed = !entry.isSuccess
            val transportFailure = entry.statusCode == null || entry.statusCode == -1
            val userMsg = if (failed) formatUserFriendlyErrorMessage(entry) else null

            // Every non-2xx response is a failed request and must increment the failure
            // counter. Connectivity is a separate signal: an HTTP 4xx/5xx proves that the
            // network path reached the server, so only transport failures make us Offline.
            current.copy(
                isConnected = if (transportFailure) false else true,
                totalRequests = total,
                successfulRequests = current.successfulRequests + if (entry.isSuccess) 1 else 0,
                failedRequests = current.failedRequests + if (failed) 1 else 0,
                lastSuccessTimestamp = if (entry.isSuccess) entry.id else current.lastSuccessTimestamp,
                lastErrorTimestamp = if (failed) entry.id else current.lastErrorTimestamp,
                lastErrorMessage = userMsg ?: current.lastErrorMessage,
                lastErrorType = if (failed) entry.errorType else current.lastErrorType
            )
        }

        if (!entry.isSuccess) {
            _latestErrorBanner.value = formatUserFriendlyErrorMessage(entry)
            Log.w(TAG, "Network Notice [${entry.method} ${entry.url}]: ${entry.errorMessage}")
        } else {
            _latestErrorBanner.value = null
            Log.d(TAG, "Network Success [${entry.method} ${entry.url}] (${entry.statusCode}) in ${entry.durationMs}ms")
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
        _latestErrorBanner.value = null
        _status.update { current ->
            NetworkStatusInfo(
                isConnected = current.isConnected,
                targetBaseUrl = current.targetBaseUrl,
                activeUrl = current.activeUrl
            )
        }
    }

    private fun formatUserFriendlyErrorMessage(entry: NetworkLogEntry): String {
        val path = try { java.net.URI(entry.url).path } catch (e: Exception) { entry.url }
        val errType = entry.errorType ?: "خطای شبکه"
        val code = if (entry.statusCode != null && entry.statusCode != -1) " (کد ${entry.statusCode})" else ""
        return when {
            entry.errorMessage?.contains("ECONNREFUSED", ignoreCase = true) == true ->
                "ارتباط با سرور برقرار نشد (Connection Refused)"
            entry.errorMessage?.contains("timeout", ignoreCase = true) == true || entry.errorType == "SocketTimeoutException" ->
                "مهلت زمانی فراخوانی سرور (https://api.gamenermayket.ir/) به پایان رسید."
            entry.statusCode == 404 ->
                "مسیر $path روی سرور پیدا نشد $code"
            entry.statusCode != null && entry.statusCode >= 500 ->
                "خطای سرور $code در پاسخ به $path"
            else ->
                "خطا در ارتباط با سرور: ${entry.errorMessage ?: errType}"
        }
    }

    fun createInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val startTime = System.currentTimeMillis()
            val urlStr = request.url.let { url -> "${url.scheme}://${url.host}${url.encodedPath}" }
            val method = request.method

            try {
                val response = chain.proceed(request)
                val duration = System.currentTimeMillis() - startTime
                val isSuccessful = response.isSuccessful
                val code = response.code

                // Never retain raw response bodies: they can contain customer/manager PII or tokens.
                // Peek only enough bytes to extract a safe machine-readable error/code field.
                val responseSnippet: String? = if (!isSuccessful) {
                    runCatching {
                        val raw = response.peekBody(32 * 1024).string()
                        val code = Regex("""code"\s*:\s*"([A-Za-z0-9_.-]{1,120})"""").find(raw)?.groupValues?.getOrNull(1)
                        val error = Regex("""error"\s*:\s*"([^"]{1,180})"""").find(raw)?.groupValues?.getOrNull(1)
                        listOfNotNull(code, error).joinToString(" | ").ifBlank { null }
                    }.getOrNull()
                } else null

                val logEntry = NetworkLogEntry(
                    method = method,
                    url = urlStr,
                    statusCode = code,
                    durationMs = duration,
                    isSuccess = isSuccessful,
                    errorMessage = if (isSuccessful) null else {
                        if (responseSnippet.isNullOrBlank()) "پاسخ HTTP $code از سرور دریافت شد"
                        else "پاسخ HTTP $code از سرور دریافت شد: $responseSnippet"
                    },
                    errorType = if (isSuccessful) null else "HTTP_$code",
                    requestHeadersCount = request.headers.size,
                    responseBodySnippet = responseSnippet
                )
                recordLog(logEntry)

                response
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                val errType = e.javaClass.simpleName
                val errMsg = e.message ?: e.toString()

                val logEntry = NetworkLogEntry(
                    method = method,
                    url = urlStr,
                    statusCode = -1,
                    durationMs = duration,
                    isSuccess = false,
                    errorMessage = errMsg,
                    errorType = errType,
                    requestHeadersCount = request.headers.size,
                    responseBodySnippet = null
                )
                recordLog(logEntry)
                throw e
            }
        }
    }
}
