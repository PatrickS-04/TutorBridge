package com.tutorbridge.app.viewmodel

import com.tutorbridge.app.data.model.*
import java.time.LocalDate

data class TutorBridgeUiState(
    val currentRoute: String = "splash",
    val role: UserRole? = null,
    val authMode: AuthMode = AuthMode.SIGN_IN,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val institutionName: String = "University of Cape Town (UCT)",
    val userName: String = "Liam Naidoo",
    val userEmail: String = "ndxlia003@myuct.ac.za",
    val userStaffId: String = "",
    val isDarkTheme: Boolean = false,
    val isOffline: Boolean = false,
    val selectedLanguage: String = "English",
    val bookingUpdatesEnabled: Boolean = true,
    val sessionRemindersEnabled: Boolean = true,
    val smsFallbackEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,

    // Calendar
    val selectedCalendarDate: LocalDate = LocalDate.of(2026, 10, 16),
    val calendarFilterType: String = "all",
    val calendarEvents: List<CalendarEventUi> = sampleEvents,

    // Tutors / Find
    val tutors: List<TutorUi> = sampleTutors,
    val selectedTutor: TutorUi? = sampleTutors.firstOrNull(),
    val selectedSlot: String = "10:30 AM",
    val selectedLocation: String = "Upper Campus — Leslie Social Sciences 2.10",
    val selectedBookingDate: String = "2026-10-16",

    // Student Requests (student view) / Requests board (tutor view)
    val myRequests: List<StudentRequestUi> = sampleMyRequests,
    val openRequests: List<StudentRequestUi> = sampleOpenRequests,
    val myApplications: List<StudentRequestUi> = sampleMyApplications,
    val selectedRequest: StudentRequestUi? = null,
    val requestDraft: RequestDraft = RequestDraft(),

    // Notifications
    val notifications: List<NotificationUi> = sampleNotifications,

    // Tutor: institutions / claims
    val institutions: List<InstitutionUi> = sampleInstitutions,
    val selectedInstitutionId: String = "uct",
    val selectedClaimPeriodId: String = "aug2026",

    // Tutor: availability
    val weeklyAvailability: List<DayWindowUi> = sampleWeeklyAvailability,
    val activeDays: Set<String> = setOf("M", "W", "F"),
    val oneOffSlots: List<OneOffSlotUi> = sampleOneOffSlots,
    val availabilityTab: String = "weekly", // "weekly" or "oneoff"

    // Session prep
    val sessionGoals: List<SessionGoalUi> = sampleSessionGoals,
    val studyMaterials: List<StudyMaterialUi> = sampleStudyMaterials,

    // Student: learning history
    val studentHealthIndexPercent: Int = 80,
    val lessonsTaught: Int = 5,
    val targetGrade: String = "A-",
    val learningHistory: List<LearningHistoryEntryUi> = sampleLearningHistory,

    // Profile (tutor edit profile)
    val tutorProfileCompleteness: Int = 83,
    val tutorLanguagesSpoken: String = "English, Afrikaans, isiXhosa",
    val tutorConsultationRoom: String = "Upper Campus — Leslie Social Sciences 2.10",

    // Booking decline note
    val lastDeclineNote: String = "Sorry, I have an unexpected faculty meeting at this hour. Please select an alternate slot tomorrow or next week!",

    // Delete account
    val deleteAccountReason: String = "Graduated / Completed Course",
    val deleteConfirmChecked: Boolean = false,
    val deleteConfirmText: String = ""
)

// ==================== TUTORS ====================

val sampleTutors = listOf(
    TutorUi(
        id = "t1",
        name = "Prof. Clara Vance",
        initials = "CV",
        subject = "Chemistry & Biochemistry",
        courseCode = "CEM1000W / CEM2005W",
        rating = 4.95,
        reviewsCount = 42,
        hourlyRate = "R 220/hr",
        campus = "Upper Campus — Leslie Social Sciences 2.10",
        availableSlots = listOf("09:00 AM", "10:30 AM", "02:00 PM", "03:30 PM"),
        bio = "Faculty-appointed tutor for first-year engineering and mathematics students, specialising in mathematical modeling and graph intuition.",
        tagList = listOf("Exam Prep", "Problem Sets", "Past Papers"),
        matchPercent = 96,
        activeNow = true,
        staffOrTutorId = "TUT-UCT-2024-089",
        verified = true,
        department = "Faculty of Science",
        languages = listOf("English", "Afrikaans"),
        totalConsultations = 320,
        consultationVenue = "Upper Campus — Leslie Social Sciences 2.10"
    ),
    TutorUi(
        id = "t2",
        name = "Thabo Mokoena",
        initials = "TM",
        subject = "Mathematics & Calculus",
        courseCode = "MAM1000W / MAM1020F",
        rating = 5.0,
        reviewsCount = 38,
        hourlyRate = "R 220/hr",
        campus = "Upper Campus — Leslie Social Sciences 2.10",
        availableSlots = listOf("09:00 AM", "10:30 AM", "01:00 PM", "02:30 PM", "04:00 PM", "05:30 PM"),
        bio = "Postgraduate Mathematics Honours tutor. I specialize in MAM1000W differential calculus, linear algebra, vector projections, and helping first-years bridge the gap from matric NSC maths.",
        tagList = listOf("Limits", "Vector Proofs", "First Principles"),
        matchPercent = 94,
        activeToday = true,
        staffOrTutorId = "TUT-WIT-2025-014",
        verified = true,
        department = "School of Mathematics",
        languages = listOf("English", "isiZulu", "Sesotho"),
        totalConsultations = 450,
        consultationVenue = "Upper Campus Room 2.10"
    ),
    TutorUi(
        id = "t3",
        name = "Naledi Sithole",
        initials = "NS",
        subject = "Computer Science & Python",
        courseCode = "CSC1015F / CSC1016S",
        rating = 4.9,
        reviewsCount = 29,
        hourlyRate = "R 200/hr",
        campus = "Upper Campus — Computer Science Lab 3",
        availableSlots = listOf("11:00 AM", "01:30 PM", "04:00 PM"),
        bio = "Final year Computer Science honours student focusing on recursion, data structures, and algorithm complexity.",
        tagList = listOf("Recursion", "Lists", "Assignments"),
        matchPercent = 91,
        activeNow = true,
        staffOrTutorId = "TUT-UP-2025-112",
        verified = true,
        department = "Department of Computer Science",
        languages = listOf("English", "Setswana"),
        totalConsultations = 180,
        consultationVenue = "Chancellor's Building — Computer Lab B"
    ),
    TutorUi(
        id = "t4",
        name = "Sipho Dlamini",
        initials = "SD",
        subject = "Physics & Mechanics",
        courseCode = "PHY1004W",
        rating = 4.7,
        reviewsCount = 21,
        hourlyRate = "R 210/hr",
        campus = "Science Precinct — RW James Physics Lab 3",
        availableSlots = listOf("10:00 AM", "12:00 PM", "02:30 PM"),
        bio = "Mechanics and dynamics tutor supporting first-year engineering physics students.",
        tagList = listOf("Mechanics", "Problem Sets"),
        matchPercent = 87,
        activeToday = true,
        staffOrTutorId = "TUT-UCT-2025-071",
        verified = true,
        department = "Faculty of Science",
        languages = listOf("English", "siSwati"),
        totalConsultations = 96,
        consultationVenue = "Science Precinct — RW James Physics Lab 3"
    )
)

// ==================== CALENDAR ====================

val sampleEvents = listOf(
    CalendarEventUi(
        id = "ev1",
        title = "MAM1000W: Calculus & Vector Proofs",
        subject = "MAM1000W Calculus",
        date = "2026-08-22",
        startTime = "10:30 AM",
        endTime = "11:30 AM",
        type = CalendarEventType.LESSON,
        participantName = "Thabo Mokoena",
        participantRole = "tutor",
        location = "Upper Campus",
        status = EventStatus.CONFIRMED,
        forRole = "both"
    ),
    CalendarEventUi(
        id = "ev2",
        title = "CEM1000W: Organic Chemistry Tut Session",
        subject = "CEM1000W Chemistry",
        date = "2026-08-23",
        startTime = "02:00 PM",
        endTime = "03:30 PM",
        type = CalendarEventType.TUTORIAL,
        participantName = "Prof. Clara Vance",
        participantRole = "tutor",
        location = "Science Precinct",
        status = EventStatus.CONFIRMED,
        forRole = "both"
    )
)

// ==================== STUDENT REQUESTS ====================

val sampleMyRequests = listOf(
    StudentRequestUi(
        id = "r1",
        subject = "Mathematics 1",
        courseCode = "MAM1000W",
        topic = "Calculus catching up",
        description = "Struggling with differentiation from first principles, epsilon-delta limits, and curve sketching for the upcoming September Class Test.",
        level = "Undergraduate 1st Year",
        timeWindow = "Weekday afternoons (Wed/Fri)",
        studentName = "Liam Naidoo",
        studentInitials = "LN",
        status = RequestStatus.OPEN,
        timestamp = "2 hours ago",
        preferredVenue = "Upper Campus — Leslie Social Sciences 2.10",
        applicants = listOf(
            TutorApplicant(
                id = "a1",
                name = "Thabo Mokoena",
                initials = "TM",
                verified = true,
                note = "I am available Wednesday afternoons. We can work through past class tests from 2022–2025 and build solid limit proofs."
            ),
            TutorApplicant(
                id = "a2",
                name = "Prof. Clara Vance",
                initials = "CV",
                verified = true,
                note = "Happy to support mathematical modeling and graph intuition before midterms."
            )
        )
    ),
    StudentRequestUi(
        id = "r2",
        subject = "Chemistry",
        courseCode = "CEM1000W",
        topic = "Stereochemistry & Enantiomers",
        description = "Need help distinguishing R/S configurations and building confidence with Fischer projections.",
        level = "Undergraduate 1st Year",
        timeWindow = "Weekend mornings",
        studentName = "Liam Naidoo",
        studentInitials = "LN",
        status = RequestStatus.MATCHED,
        timestamp = "Yesterday",
        preferredVenue = "Science Precinct — RW James Physics Lab 3",
        matchedTutorName = "Prof. Clara Vance"
    ),
    StudentRequestUi(
        id = "r3",
        subject = "Computer Science",
        courseCode = "CSC1015F",
        topic = "Recursion & Lists",
        description = "Would like to go through recursive list traversal and base-case design.",
        level = "Undergraduate 1st Year",
        timeWindow = "Thursday afternoons",
        studentName = "Liam Naidoo",
        studentInitials = "LN",
        status = RequestStatus.OPEN,
        timestamp = "3 days ago",
        preferredVenue = "Chancellor's Building — Computer Lab B"
    )
)

// Tutor-facing: open requests to browse & apply to
val sampleOpenRequests = listOf(
    StudentRequestUi(
        id = "or1",
        subject = "MAM1000W Calculus",
        courseCode = "MAM1000W",
        topic = "Taylor series & Class Test prep",
        studentName = "Liam Naidoo",
        studentInitials = "LN",
        status = RequestStatus.OPEN,
        timestamp = "2 hours ago",
        timeWindow = "Wed / Fri afternoons",
        preferredVenue = "Upper Campus — Leslie Social 2.10"
    ),
    StudentRequestUi(
        id = "or2",
        subject = "CEM1000W Chemistry",
        courseCode = "CEM1000W",
        topic = "Thermodynamics stoichiometry",
        studentName = "Zola Khumalo",
        studentInitials = "ZK",
        status = RequestStatus.OPEN,
        timestamp = "Yesterday",
        timeWindow = "Tue evenings",
        preferredVenue = "Science Precinct — Lab 3"
    )
)

// Tutor-facing: applications already submitted
val sampleMyApplications = listOf(
    StudentRequestUi(
        id = "app1",
        subject = "CEM1000W Chemistry",
        courseCode = "CEM1000W",
        topic = "",
        studentName = "Zola Khumalo",
        studentInitials = "ZK",
        status = RequestStatus.OFFERED,
        timestamp = "",
        preferredVenue = ""
    )
)

// ==================== NOTIFICATIONS ====================

val sampleNotifications = listOf(
    NotificationUi(
        id = "n1",
        emoji = "💰",
        title = "Monthly Claim Approved for Payroll",
        body = "Faculty Finance has approved your July claim of R3,960.00 (18.0 hrs). Ref #CLM-UCT-2026-07-882.",
        time = "1h ago",
        unread = true
    ),
    NotificationUi(
        id = "n2",
        emoji = "📅",
        title = "Upcoming Tutoring Session",
        body = "MAM1000W with Liam Naidoo starts in 30 minutes at Leslie Social 2.10.",
        time = "30m ago",
        unread = true
    ),
    NotificationUi(
        id = "n3",
        emoji = "📝",
        title = "New Student Tuition Request",
        body = "Zola Khumalo requested tutoring for PHY1004W Mechanics.",
        time = "2h ago",
        unread = true
    ),
    NotificationUi(
        id = "n4",
        emoji = "🏛️",
        title = "Semester 2 Timesheet Submissions Open",
        body = "August 2026 claims can now be exported and submitted to your Department Tutoring Coordinator.",
        time = "1d ago",
        unread = false
    )
)

// ==================== CLAIMS / TIMESHEETS ====================

val sampleInstitutions = listOf(
    InstitutionUi(
        id = "uct",
        shortName = "UCT",
        fullName = "University of Cape Town",
        faculty = "Faculty of Science",
        staffId = "TUT-UCT-2024-089",
        rate = 220.0,
        claimPeriods = listOf(
            ClaimPeriodUi(
                id = "aug2026",
                label = "August 2026",
                status = ClaimStatus.ACTIVE_DRAFT,
                totalHours = 11.0,
                sessionsCount = 6,
                claimValue = 2420.0,
                sessions = listOf(
                    ClaimSessionUi(
                        id = "cs1",
                        courseCode = "MAM1000W",
                        date = "2026-08-04",
                        timeRange = "10:30 - 12:00",
                        amount = "R330",
                        studentName = "Liam Naidoo",
                        studentIdTag = "NDXLIA003",
                        topic = "Limits, Continuity & Differentiation from First Principles",
                        hours = 1.5
                    )
                )
            ),
            ClaimPeriodUi(
                id = "jul2026",
                label = "July 2026",
                status = ClaimStatus.PAID,
                totalHours = 4.0,
                sessionsCount = 2,
                claimValue = 880.0,
                claimRef = "CLM-UCT-2026-07-882",
                sessions = listOf(
                    ClaimSessionUi(
                        id = "cs2",
                        courseCode = "MAM1000W",
                        date = "2026-07-15",
                        timeRange = "10:00 - 12:00",
                        amount = "R440",
                        studentName = "Liam Naidoo",
                        studentIdTag = "NDXLIA003",
                        topic = "",
                        hours = 2.0
                    )
                )
            ),
            ClaimPeriodUi(
                id = "jun2026",
                label = "June 2026",
                status = ClaimStatus.PAID,
                totalHours = 0.0,
                sessionsCount = 0,
                claimValue = 0.0
            )
        )
    )
)

// ==================== AVAILABILITY ====================

val sampleWeeklyAvailability = listOf(
    DayWindowUi("Mondays", "09:00 AM – 05:00 PM (Campus & Online)", true),
    DayWindowUi("Wednesdays", "09:00 AM – 05:00 PM (Campus & Online)", true),
    DayWindowUi("Fridays", "09:00 AM – 05:00 PM (Campus & Online)", true)
)

val sampleOneOffSlots = listOf(
    OneOffSlotUi("os1", "2026-10-16", "01:00 PM – 02:00 PM", "Extra Class Test prep slot")
)

// ==================== SESSION PREP ====================

val sampleSessionGoals = listOf(
    SessionGoalUi("g1", "Review MAM1000W Integration by Parts problem sets", false),
    SessionGoalUi("g2", "Check Taylor series convergence exercises", true),
    SessionGoalUi("g3", "Review past exam worksheets together", false)
)

val sampleStudyMaterials = listOf(
    StudyMaterialUi("sm1", "Chain_Rule_Practice_Set_2026.pdf", "1.4 MB")
)

// ==================== LEARNING HISTORY ====================

val sampleLearningHistory = listOf(
    LearningHistoryEntryUi(
        id = "lh1",
        title = "Lesson 4: Organic Chemistry Mechanisms",
        date = "Oct 2, 2026",
        summary = "Mastered electrophilic addition mechanisms. Quiz score: 92%."
    ),
    LearningHistoryEntryUi(
        id = "lh2",
        title = "Lesson 3: Acids, Bases & Buffers",
        date = "Sep 25, 2026",
        summary = "Worked through the Henderson-Hasselbalch equation and titration curves."
    )
)
