package com.eventroute.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val access = tokens.access
        val out = if (access != null && req.header("Authorization") == null) {
            req.newBuilder().header("Authorization", "Bearer $access").build()
        } else {
            req
        }
        return chain.proceed(out)
    }
}
