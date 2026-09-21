package com.tutorbridge.app.data.model

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

// Reference: Android Developers. 2026. Show a biometric authentication dialog. [Online].
//   Available at: https://developer.android.com/identity/sign-in/biometric-auth [Accessed 21 September 2026].
class BiometricAuthentication(private val activity: FragmentActivity) {
    private val authenticatorsAllowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(): Boolean = BiometricManager.from(
        activity).canAuthenticate(authenticatorsAllowed) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val promptInformation = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock TutorBridge")
            .setSubtitle("Confirm it's you to continue with your session")
            .setAllowedAuthenticators(authenticatorsAllowed)
            .build()

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errString.toString())
            }
        )
        prompt.authenticate(promptInformation)
    }
}