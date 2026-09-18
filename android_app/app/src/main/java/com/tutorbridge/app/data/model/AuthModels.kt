package com.tutorbridge.app.data.model

// ==================== Requests ====================

data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val institutionId: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class SsoRequest(
    val idToken: String,
    // Only read by the backend the first time this email signs in via SSO
    // (first-time Google sign-in doubles as sign-up). Ignored for returning users.
    val institutionId: String? = null,
    val fullName: String? = null
)

data class RefreshRequest(val refreshToken: String)

data class LogoutRequest(val refreshToken: String)

// ==================== Responses ====================

data class RegisterResponse(
    val id: String,
    val institutionId: String,
    val role: String,
    val verificationStatus: String,
    val profileComplete: Int
)

data class UserSummary(
    val id: String,
    val role: String,
    val verificationStatus: String,
    val profileComplete: Int
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val user: UserSummary
)

data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

data class InstitutionSummary(
    val id: String,
    val name: String
)

data class ApiErrorBody(val error: ApiErrorDetail?)

data class ApiErrorDetail(
    val code: String?,
    val message: String?,
    val field: String?
)
