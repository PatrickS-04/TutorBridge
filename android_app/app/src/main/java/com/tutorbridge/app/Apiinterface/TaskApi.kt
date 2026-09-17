package com.tutorbridge.app.Apiinterface

import com.tutorbridge.app.data.model.Task
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface TaskApi {
    @GET("api/tasks")
    suspend fun getTasks(): List<Task>

    @POST("api/tasks")
    suspend fun createTask(@Body task: Task): Task
}