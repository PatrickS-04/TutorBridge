package com.tutorbridge.app.Apiinterface

import com.tutorbridge.app.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Reference: Square, Inc. 2024. Retrofit (Version 2.11.0). [Computer program].
//   Available at: https://lysine.dev/retrofit/ [Accessed 21 September 2026].
/**
 * Everything the app talks to after sign-in. One interface rather than one per
 * route file: the screens read across all of them anyway, and Retrofit builds
 * a single implementation either way.
 *
 * The Firebase ID token is attached by AuthInterceptor, so nothing here takes
 * an Authorization argument.
 */
interface TutorBridgeApi {

    // ==================== PROFILE & SETTINGS ====================

    @GET("users/me")
    suspend fun me(): Response<UserDto>

    @PUT("users/me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): Response<UserDto>

    @GET("users/me/settings")
    suspend fun settings(): Response<UserSettingsDto>

    @PUT("users/me/settings")
    suspend fun updateSettings(@Body body: UpdateSettingsRequest): Response<UserSettingsDto>

    @DELETE("users/me")
    suspend fun scheduleAccountDeletion(): Response<Map<String, Any>>

    @GET("institutions/me")
    suspend fun institution(): Response<InstitutionDto>

    // ==================== REFERENCE DATA ====================

    @GET("reference/subjects-and-locations")
    suspend fun referenceData(): Response<ReferenceDataDto>

    // ==================== TUTORS ====================

    @GET("users/tutors")
    suspend fun tutors(): Response<PageResponse<UserDto>>

    // ==================== TUITION REQUESTS ====================

    /** [mine] = true for the student's own board; omitted for the tutor board's open requests. */
    @GET("tuition-requests")
    suspend fun tuitionRequests(@Query("mine") mine: Boolean? = null): Response<PageResponse<TuitionRequestDto>>

    @POST("tuition-requests")
    suspend fun createTuitionRequest(@Body body: CreateTuitionRequest): Response<Map<String, Any>>

    @GET("tuition-requests/{id}/applications")
    suspend fun requestApplications(@Path("id") id: String): Response<PageResponse<RequestApplicationDto>>

    @POST("tuition-requests/{id}/applications")
    suspend fun applyToRequest(@Path("id") id: String, @Body body: ApplyRequest): Response<Map<String, Any>>

    // ==================== APPLICATIONS ====================

    @GET("applications/mine")
    suspend fun myApplications(): Response<PageResponse<MyApplicationDto>>

    /** Student accepts or rejects a tutor's application. */
    @PUT("applications/{id}")
    suspend fun decideApplication(@Path("id") id: String, @Body body: DecisionRequest): Response<Map<String, Any>>

    // ==================== BOOKINGS ====================

    @GET("bookings")
    suspend fun bookings(): Response<PageResponse<BookingDto>>

    @POST("bookings/direct")
    suspend fun bookTutorDirectly(@Body body: DirectBookingRequest): Response<BookingAckDto>

    /** Books the session for a match the student already accepted. */
    @POST("bookings")
    suspend fun scheduleFromApplication(@Body body: ApplicationBookingRequest): Response<BookingAckDto>

    @PUT("bookings/{id}/confirm")
    suspend fun confirmBooking(@Path("id") id: String): Response<BookingAckDto>

    @PUT("bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: String, @Body body: CancelRequest): Response<BookingAckDto>

    /** Replaces the tutor's prep notes for one session - checklist and side notes together. */
    @PUT("bookings/{id}/notes")
    suspend fun saveSessionNotes(@Path("id") id: String, @Body body: TutorNotesDto): Response<TutorNotesDto>

    // ==================== NOTIFICATIONS ====================

    @GET("notifications")
    suspend fun notifications(): Response<PageResponse<NotificationEventDto>>

    @PUT("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): Response<Map<String, Any>>

    // ==================== TIMESHEETS / CLAIMS ====================

    @GET("timesheets/periods")
    suspend fun claimPeriods(@Query("count") count: Int = 3): Response<TimesheetPeriodsDto>

    // ==================== GOOGLE CALENDAR ====================

    @GET("calendar/link")
    suspend fun calendarLink(): Response<CalendarLinkDto>

    @POST("calendar/link")
    suspend fun linkCalendar(@Body body: LinkCalendarRequest): Response<CalendarLinkDto>

    @DELETE("calendar/link")
    suspend fun unlinkCalendar(): Response<Unit>

    // ==================== PUSH TOKENS ====================

    @POST("notification-tokens")
    suspend fun registerPushToken(@Body body: RegisterPushTokenRequest): Response<Map<String, Any>>

    @DELETE("notification-tokens/{token}")
    suspend fun unregisterPushToken(@Path("token") token: String): Response<Unit>

}
