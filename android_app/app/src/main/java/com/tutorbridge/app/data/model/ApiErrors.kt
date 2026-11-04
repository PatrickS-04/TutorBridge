package com.tutorbridge.app.data.model

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.tutorbridge.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What a failed Firebase sign-in or sign-up means to the person using the app, in their
 * language, rather than Firebase's own English message. The weak password comes first
 * because it is a kind of invalid credential.
 */
@StringRes
fun authErrorRes(error: Exception): Int = when (error) {
    is FirebaseAuthWeakPasswordException -> R.string.auth_error_weak_password
    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> R.string.auth_error_bad_credentials
    is FirebaseAuthUserCollisionException -> R.string.api_error_email_taken
    is FirebaseTooManyRequestsException -> R.string.auth_error_too_many
    is FirebaseNetworkException -> R.string.error_unreachable
    else -> R.string.auth_error_generic
}

/**
 * The words for an error the server reports by code. The server's own message is
 * English, so a code that means one thing wherever it is raised is shown from the
 * app's resources instead and so follows the language chosen on the phone.
 *
 * NOT_FOUND, VALIDATION_ERROR, INVALID_STATE and FORBIDDEN are left out on purpose:
 * each stands for many different problems, so the server's message is the only text
 * that says which one it was. The server words those in the app's language itself,
 * from the Accept-Language header RetrofitInstance sends.
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
    "WRONG_CODE" -> R.string.api_error_wrong_code
    "CODE_EXPIRED" -> R.string.api_error_code_expired
    "TOO_MANY_ATTEMPTS" -> R.string.api_error_too_many_attempts
    "CHECK_IN_CLOSED" -> R.string.api_error_check_in_closed
    "ALREADY_CHECKED_IN" -> R.string.api_error_already_checked_in
    else -> null
}

/**
 * The words for the two errors that name the weeks they are about: a series that clashes with
 * a confirmed session (SERIES_CONFLICT) or falls outside the tutor's hours (OUTSIDE_AVAILABILITY).
 * Each message has a place for the list of weeks, from [formatSeriesDates].
 */
@StringRes
fun seriesErrorMessageRes(code: String?): Int? = when (code) {
    "SERIES_CONFLICT" -> R.string.api_error_series_conflict
    "OUTSIDE_AVAILABILITY" -> R.string.api_error_outside_availability
    else -> null
}

/**
 * The start times the server named, as a short list a person can read ("Mon 16 Nov, Mon 30 Nov"):
 * each instant is shown on the day it falls in [zone], in [locale]. A time that cannot be read is
 * left out rather than shown as an error inside an error.
 */
fun formatSeriesDates(isoDates: List<String>, zone: ZoneId, locale: Locale): String {
    val format = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    return isoDates
        .mapNotNull { runCatching { Instant.parse(it).atZone(zone).format(format) }.getOrNull() }
        .joinToString(", ")
}
