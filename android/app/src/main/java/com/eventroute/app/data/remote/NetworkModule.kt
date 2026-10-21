package com.eventroute.app.data.remote

import com.eventroute.app.BuildConfig
import com.eventroute.app.data.remote.api.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class NetworkModule(
    val tokenStore: TokenStore,
    val sessionEvents: SessionEvents
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val contentType = "application/json".toMediaType()

    private val baseLoggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }
    }

    val plainOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(ErrorInterceptor())
            .addInterceptor(baseLoggingInterceptor)
            .build()
    }

    val authRefreshApi: AuthRefreshApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(plainOkHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AuthRefreshApi::class.java)
    }

    val authOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, { authRefreshApi }, sessionEvents))
            .addInterceptor(ErrorInterceptor())
            .addInterceptor(baseLoggingInterceptor)
            .build()
    }

    private val authRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(authOkHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    val authApi: AuthApi by lazy { authRetrofit.create(AuthApi::class.java) }
    val meApi: MeApi by lazy { authRetrofit.create(MeApi::class.java) }
    val collegesApi: CollegesApi by lazy { authRetrofit.create(CollegesApi::class.java) }
    val eventsApi: EventsApi by lazy { authRetrofit.create(EventsApi::class.java) }
    val registrationApi: RegistrationApi by lazy { authRetrofit.create(RegistrationApi::class.java) }
    val teamsApi: TeamsApi by lazy { authRetrofit.create(TeamsApi::class.java) }
    val journeyApi: JourneyApi by lazy { authRetrofit.create(JourneyApi::class.java) }
    val notificationsApi: NotificationsApi by lazy { authRetrofit.create(NotificationsApi::class.java) }
}
