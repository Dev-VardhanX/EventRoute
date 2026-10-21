package com.eventroute.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class ErrorInterceptor : Interceptor {
    private val json = Json { ignoreUnknownKeys = true }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response: Response
        try {
            response = chain.proceed(chain.request())
        } catch (e: IOException) {
            throw ApiException("NETWORK", e.message ?: "No connection. Check your internet and retry.", 0)
        }

        if (!response.isSuccessful) {
            val code = response.code
            val bodyString = try {
                response.peekBody(1024 * 64).string()
            } catch (_: Exception) {
                null
            }

            val errorBody = if (!bodyString.isNullOrBlank()) {
                runCatching { json.decodeFromString<ApiErrorBody>(bodyString) }.getOrNull()
            } else null

            val errorCode = errorBody?.code ?: when (code) {
                401 -> "UNAUTHENTICATED"
                403 -> "FORBIDDEN"
                404 -> "NOT_FOUND"
                429 -> "RATE_LIMITED"
                else -> "HTTP_$code"
            }
            val errorMessage = errorBody?.message ?: "Request failed with HTTP status $code"
            throw ApiException(errorCode, errorMessage, code)
        }
        return response
    }
}
