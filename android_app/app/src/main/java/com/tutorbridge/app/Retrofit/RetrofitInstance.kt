package com.tutorbridge.app.Retrofit

import com.tutorbridge.app.Apiinterface.TaskApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    val api: TaskApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:5000/") // 10.0.2.2 = localhost, for emulator
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TaskApi::class.java)
    }
}