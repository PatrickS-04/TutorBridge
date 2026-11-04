package com.tutorbridge.app.data.model

import android.os.Build
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tutorbridge.app.R

/** Whether this phone can show the unlock prompt and, if not, what to tell the user. */
enum class BiometricStatus(@StringRes val messageRes: Int) {
    AVAILABLE(R.string.biometric_unlock),
    NO_HARDWARE(R.string.biometric_no_hardware),
    NOT_ENROLLED(R.string.biometric_none_enrolled),
    UNAVAILABLE(R.string.biometric_unavailable)
}

fun biometricStatusOf(code: Int): BiometricStatus = when (code) {
    BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
    else -> BiometricStatus.UNAVAILABLE
}

// Reference: Android Developers. 2026. Show a biometric authentication dialog. [Online].
//   Available at: https://developer.android.com/identity/sign-in/biometric-auth [Accessed 21 September 2026].
class BiometricAuthentication(private val activity: FragmentActivity) {
    // Android 9 and 10 do not support STRONG combined with the screen lock.
    private val authenticatorsAllowed =
        (if (Build.VERSION.SDK_INT in Build.VERSION_CODES.P..Build.VERSION_CODES.Q) BiometricManager.Authenticators.BIOMETRIC_WEAK
        else BiometricManager.Authenticators.BIOMETRIC_STRONG) or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun status(): BiometricStatus = biometricStatusOf(BiometricManager.from(activity).canAuthenticate(authenticatorsAllowed))

    /** [onFailure] gets a message to show. The prompt itself offers the PIN or pattern. */
    fun authenticate(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val promptInformation = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(authenticatorsAllowed)
            .build()

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(
                    when (errorCode) {
                        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> activity.getString(R.string.biometric_locked_out)
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED ->
                            activity.getString(R.string.biometric_cancelled)
                        else -> errString.toString()
                    }
                )
            }
        )
        prompt.authenticate(promptInformation)
    }
}
