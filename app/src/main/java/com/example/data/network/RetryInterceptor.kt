package com.example.data.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException

class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var tryCount = 0
        var exception: Exception? = null
        
        val isSyncEndpoint = request.url.encodedPath.contains("sync", ignoreCase = true)
        val actualMaxRetries = if (isSyncEndpoint) maxRetries + 2 else maxRetries
        
        var sleepTime = 2000L // 2 seconds

        while (tryCount < actualMaxRetries) {
            try {
                if (response != null) {
                    response.close()
                }
                response = chain.proceed(request)
                if (response.isSuccessful || (response.code in 400..499 && response.code != 408 && response.code != 429)) {
                    return response
                }
            } catch (e: Exception) {
                exception = e
                if (e !is IOException) {
                    throw e
                }
            }
            
            tryCount++
            if (tryCount < actualMaxRetries) {
                try {
                    Thread.sleep(sleepTime)
                    sleepTime = (sleepTime * 1.5).toLong() // Exponential backoff
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException()
                }
            }
        }
        
        return response ?: Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(503)
            .message("Network Error: ${exception?.message ?: "Unknown"}")
            .body("{\"error\": \"Network timeout or connection drop after $actualMaxRetries retries\"}".toResponseBody("application/json".toMediaType()))
            .build()
    }
}
