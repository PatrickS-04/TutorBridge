package com.tutorbridge.app

import com.tutorbridge.app.data.model.errorMessageRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorsTest {

    @Test
    fun `codes that mean one thing are shown from the app's own text`() {
        listOf(
            "ALREADY_APPLIED", "ALREADY_DECIDED", "ALREADY_SCHEDULED", "SLOT_CONFLICT",
            "EMAIL_TAKEN", "GOOGLE_AUTH_FAILED", "UNVERIFIED", "NOT_REQUIRED"
        ).forEach { assertNotNull(it, errorMessageRes(it)) }
    }

    @Test
    fun `codes that cover many problems keep the server's message`() {
        listOf("NOT_FOUND", "VALIDATION_ERROR", "INVALID_STATE", "FORBIDDEN", "INTERNAL_ERROR", null)
            .forEach { assertNull(it, errorMessageRes(it)) }
    }

    @Test
    fun `each mapped code has its own text`() {
        val ids = listOf("ALREADY_APPLIED", "ALREADY_DECIDED", "SLOT_CONFLICT", "EMAIL_TAKEN").map { errorMessageRes(it) }
        assertEquals(ids.size, ids.toSet().size)
    }
}
