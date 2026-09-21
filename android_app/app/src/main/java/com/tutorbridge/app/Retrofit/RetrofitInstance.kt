package com.tutorbridge.app.Retrofit

import com.tutorbridge.app.Apiinterface.AuthApi
import com.tutorbridge.app.Apiinterface.TutorBridgeApi
import com.tutorbridge.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    // 10.0.2.2 = the host machine's localhost, as seen from the Android
    // emulator, matching backend_api running locally on :5000. Point this at
    // the deployed Azure App Service URL for a physical device / release build.
    private val BASE_URL = BuildConfig.API_BASE_URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // One client for everything: AuthInterceptor attaches the Firebase ID
    // token when somebody is signed in and is a no-op when nobody is, so the
    // public endpoints (GET /reference/institutions) work through it too.
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(AuthInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }

    /** Everything the signed-in app reads and writes. */
    val tutorBridgeApi: TutorBridgeApi by lazy { retrofit.create(TutorBridgeApi::class.java) }
}
