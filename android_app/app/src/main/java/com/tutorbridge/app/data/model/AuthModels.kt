package com.tutorbridge.app.data.model

// ==================== Requests ====================

/**
 * Body for POST /auth/bootstrap. The Firebase ID token identifies the user,
 * so the only things the server needs are the bits Firebase doesn't hold.
 *
 * [institutionId] is only read the first time a brand-new account signs in;
 * it is ignored for returning users and for admin-provisioned profiles.
 */
data class BootstrapRequest(
    val institutionId: String? = null,
    val fullName: String? = null
)

/** Body for POST /auth/change-password. */
data class ChangePasswordRequest(val newPassword: String)

// ==================== Responses ====================

data class ProfileResponse(
    val id: String,
    val institutionId: String,
    val role: String,
    val fullName: String,
    val verificationStatus: String,
    val profileComplete: Int,
    /** An admin-provisioned tutor still on the temporary password they were issued. */
    val mustChangePassword: Boolean = false
)

data class InstitutionSummary(
    val id: String,
    val name: String
)

data class ApiErrorBody(val error: ApiErrorDetail?)

data class ApiErrorDetail(
    val code: String?,
    val message: String?,
    val field: String?,
    /** The start times (ISO-8601) a series error is about: the weeks that clash or fall outside the tutor's hours. */
    val dates: List<String>? = null
)
