# PROG7314 POE Part 2
## TutorBridge Android Application
> By Dillon Rinkwest - ST10439147, Patrick Shutte - ST10440987, Liam Swile - ST10438679 and Uzair Essa - ST10440109

## Table of Contents
- [1. Overview](#1-overview)
- [1.1 Intended User Base](#11-intended-user-base)


---

## 1. Overview
TutorBridge is Tutoring Android Application that aims to provide institutions with a platform that connects
Students with institute availed Tutors with resources that assist in finding proper Tutors, connecting with
students with proper and chosen resources as well as organizing out of campus tutor sessions.

| Project | Type | Description |
|---|---|---|
| `android_app` | Kotlin native Android Studio Application | The android app frontend application that takes user input, calls apis and returns information and functionality in the application |
| `backend_api` | Typescript Custom APIs | Collection of project created custom APIs that create connection between database and android application |

> GitHub Submission File: TutorBridge

---

### 1.1 Intended User Base
- Insitutions such as Emeris
- Tutors availed by institutions
- Students of the institutions

---

### 1.2 Features
* Biometric Scanner (in POE Part 3 Final Submission)
* Single sign-on 
* Payroll claim export 
* Two phase booking; rejection overlap 
* Google Calendar 
* Match relevance 
* Session materials pack 
* Institutional structural plan

---

### 1.3 Tech Stack
* Kotlin
* Typescript
* MongoDB Atlas
* Firebase
* Google Cloud (Google Calendar)

---

## 2. Project Structure

```
TutorBridge/
├── .github/
│   └── workflows/
│       └── ci.yml                      # CI pipeline
├── android_app/                        # Native Android app (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── build.gradle.kts
│   │   ├── google-services.json
│   │   └── src/
│   │       ├── main/
│   │       │   ├── AndroidManifest.xml
│   │       │   ├── java/com/tutorbridge/app/
│   │       │   │   ├── MainActivity.kt
│   │       │   │   ├── TutorBridgeApp.kt
│   │       │   │   ├── Apiinterface/           # Retrofit API interfaces
│   │       │   │   │   ├── AuthApi.kt
│   │       │   │   │   └── TutorBridgeApi.kt
│   │       │   │   ├── Retrofit/               # HTTP client setup
│   │       │   │   │   ├── AuthInterceptor.kt
│   │       │   │   │   └── RetrofitInstance.kt
│   │       │   │   ├── data/
│   │       │   │   │   ├── model/              # API/domain models, preferences, auth helpers
│   │       │   │   │   └── repository/
│   │       │   │   │       └── TutorBridgeRepository.kt
│   │       │   │   ├── repository/
│   │       │   │   │   └── AuthRepository.kt
│   │       │   │   ├── push/                   # FCM push notifications
│   │       │   │   │   ├── NotificationChanels.kt
│   │       │   │   │   └── TutorBridgeMessagingService.kt
│   │       │   │   ├── viewmodel/              # State, routing and access control
│   │       │   │   │   ├── TutorBridgeViewModel.kt
│   │       │   │   │   ├── TutorBridgeUiState.kt
│   │       │   │   │   ├── DeepLinkRouter.kt
│   │       │   │   │   └── RouteAccess.kt
│   │       │   │   └── ui/
│   │       │   │       ├── components/         # Shared composables (VenueLink)
│   │       │   │       ├── navigation/
│   │       │   │       │   └── TutorBridgeNavGraph.kt
│   │       │   │       ├── theme/              # Color.kt, Theme.kt
│   │       │   │       └── screens/
│   │       │   │           ├── auth/           # Sign-in, biometric
│   │       │   │           ├── availability/   # Tutor availability
│   │       │   │           ├── booking/        # Booking flow
│   │       │   │           ├── calendar/
│   │       │   │           ├── claims/         # Claim export + voucher PDF
│   │       │   │           ├── find/           # Find tutors, tutor profile
│   │       │   │           ├── history/        # Learning history
│   │       │   │           ├── home/           # Student and tutor home
│   │       │   │           ├── notifications/
│   │       │   │           ├── offline/
│   │       │   │           ├── prep/           # Session prep
│   │       │   │           ├── profile/        # Edit tutor profile
│   │       │   │           ├── requests/       # Tuition requests + tutor board
│   │       │   │           ├── settings/       # Settings, edit profile, delete account
│   │       │   │           └── splash/
│   │       │   └── res/
│   │       │       ├── drawable/               # Icons and logo
│   │       │       ├── mipmap-anydpi-v26/      # Launcher icons
│   │       │       ├── values/                 # Default strings, colors, themes
│   │       │       ├── values-af/              # Afrikaans strings
│   │       │       ├── values-zu/              # isiZulu strings
│   │       │       └── xml/                    # FileProvider paths
│   │       └── test/java/com/tutorbridge/app/
│   │           ├── MappingTest.kt
│   │           └── RouteAccessTest.kt
│   ├── gradle/
│   │   ├── libs.versions.toml                  # Version catalog
│   │   └── wrapper/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   └── gradlew.bat
├── backend_api/                        # Node + TypeScript REST API
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
│       ├── index.ts                    # Server entry point
│       ├── db.ts                       # Database connection
│       ├── openapi.ts                  # OpenAPI spec
│       ├── seed.ts                     # Seed data
│       ├── seedTutors.ts               # Tutor seed data
│       ├── middleware/                 # auth.ts, errors.ts (+ tests)
│       ├── models/                     # User, Booking, TuitionRequest, Thread, Message,
│       │                               # Institution, Subject, Province, Area, CalendarLink,
│       │                               # NotificationEvent/Token, UserSettings,
│       │                               # VerificationDocument, Application, enums
│       ├── routes/                     # applications, auth, bookings, calendar, institutions,
│       │                               # notifications, notificationTokens, reference,
│       │                               # threads, timesheets, tuitionRequests, users
│       └── utils/                      # Google Calendar sync, push, notify, pagination,
│                                       # CRUD router factory, async handler,
│                                       # completePastSessions, purgeScheduledDeletions (+ tests)
├── firebase.json
├── .firebaserc
├── package.json
└── .gitignore
```

---

## 2.1 Architecture


---

## 3. Setup



---

## AI Disclosure
### Sections where AI was used:
* Project Structure
### AI Tool Used:
* Claude Sonnet 5
### Purpose/Intent:
* To gather the project structure quickly and easily in a well-format readme section
#### Dates used:
* 2026/09/21
### Link:
* https://claude.ai/share/e02a707f-cc7e-4a32-a913-9b3e5fbad1e0
### Action taken:
* The prompt answer was reviewed then copied onto the project structure heading of the readme
