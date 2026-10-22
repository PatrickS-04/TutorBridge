package com.tutorbridge.app.data.model

import androidx.annotation.StringRes
import com.tutorbridge.app.R

/**
 * The words for an error the server reports by code. The server's own message is
 * English, so a code that means one thing wherever it is raised is shown from the
 * app's resources instead and so follows the language chosen on the phone.
 *
 * NOT_FOUND, VALIDATION_ERROR, INVALID_STATE and FORBIDDEN are left out on purpose:
 * each stands for many different problems, so the server's message is the only text
 * that says which one it was.
 */
@StringRes
fun errorMessageRes(code: String?): Int? = when (code) {
    "ALREADY_APPLIED" -> R.string.api_error_already_applied
    "ALREADY_DECIDED" -> R.string.api_error_already_decided
    "ALREADY_SCHEDULED" -> R.string.api_error_already_scheduled
    "SLOT_CONFLICT" -> R.string.api_error_slot_conflict
    "EMAIL_TAKEN" -> R.string.api_error_email_taken
    "GOOGLE_AUTH_FAILED" -> R.string.api_error_google_auth_failed
    "UNVERIFIED" -> R.string.api_error_unverified
    "NOT_REQUIRED" -> R.string.api_error_not_required
    else -> null
}
