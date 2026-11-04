package com.tutorbridge.app

import androidx.biometric.BiometricManager
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.tutorbridge.app.data.model.BiometricStatus
import com.tutorbridge.app.data.model.authErrorRes
import com.tutorbridge.app.data.model.biometricStatusOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** A failed sign-in reads as a sentence in the user's language, and an unusable sensor is told apart from a usable one. */
class AuthErrorsTest {

    @Test
    fun `a wrong password or unknown account gets the same plain message`() {
        assertEquals(R.string.auth_error_bad_credentials, authErrorRes(FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "raw")))
        assertEquals(R.string.auth_error_bad_credentials, authErrorRes(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "raw")))
    }

    @Test
    fun `a weak password is not reported as a wrong one`() {
        assertEquals(R.string.auth_error_weak_password, authErrorRes(FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "raw", "short")))
    }

    @Test
    fun `the other failures each say what happened`() {
        assertEquals(R.string.api_error_email_taken, authErrorRes(FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "raw")))
        assertEquals(R.string.auth_error_too_many, authErrorRes(FirebaseTooManyRequestsException("raw")))
        assertEquals(R.string.error_unreachable, authErrorRes(FirebaseNetworkException("raw")))
        assertEquals(R.string.auth_error_generic, authErrorRes(IllegalStateException("raw")))
    }

    @Test
    fun `each sensor state has its own message and only success unlocks`() {
        assertEquals(BiometricStatus.AVAILABLE, biometricStatusOf(BiometricManager.BIOMETRIC_SUCCESS))
        assertEquals(BiometricStatus.NO_HARDWARE, biometricStatusOf(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
        assertEquals(BiometricStatus.NOT_ENROLLED, biometricStatusOf(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        assertEquals(BiometricStatus.UNAVAILABLE, biometricStatusOf(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE))
        assertEquals(BiometricStatus.UNAVAILABLE, biometricStatusOf(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED))
    }
}
