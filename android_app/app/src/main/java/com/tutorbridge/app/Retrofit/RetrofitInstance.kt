package com.tutorbridge.app.Retrofit

import android.content.Context
import com.tutorbridge.app.Apiinterface.AuthApi
import com.tutorbridge.app.Apiinterface.TaskApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    // 10.0.2.2 = the host machine's localhost, as seen from the Android
    // emulator, matching backend_api running locally on :5000. Point this at
    // the deployed Azure App Service URL for a physical device / release build.
    private const val BASE_URL = "http://10.0.2.2:5000/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    lateinit var tokenManager: TokenManager
        private set

    private var initialized = false

    /**
     * Call once before touching [authApi] or [api] - TutorBridgeViewModel
     * does this in its init block via getApplication(). Safe to call more
     * than once.
     */
    fun init(context: Context) {
        if (initialized) return
        tokenManager = TokenManager(context.applicationContext)
        initialized = true
    }

    /** Client for auth.ts endpoints - these never send or need a Bearer token. */
    private val plainClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val plainRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(plainClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy { plainRetrofit.create(AuthApi::class.java) }

    /** Client for everything else - attaches the access token and auto-refreshes it on a 401. */
    private val authedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(AuthInterceptor(tokenManager))
            .authenticator(TokenAuthenticator(tokenManager, authApi))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val authedRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Kept for the existing (currently unused) Task stub. Real endpoints
    // (bookings, tuition-requests, etc.) get their own Retrofit interfaces
    // added here as screens are wired to the database in the next pass.
    val api: TaskApi by lazy { authedRetrofit.create(TaskApi::class.java) }
}
