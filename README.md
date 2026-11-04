# PROG7314 POE Part 2
## TutorBridge Android Application
> By Dillon Rinkwest - ST10439147, Patrick Shutte - ST10440987, Liam Swile - ST10438679 and Uzair Essa - ST10440109

## Table of Contents
- [Overview](#overviewpurpose)
- [Intended User Base](#intended-user-base)
- [Features](#featuresscope)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [Setup](#setup)
- [Version Control/GitHub Workflows](#version-controlgithub-workflows)
- [References](#references)
- [YouTube Video](#youtube-video-link)

---

## Overview/Purpose
TutorBridge is Tutoring Android Application that aims to provide institutions with a platform that connects
Students with institute availed Tutors with resources that assist in finding proper Tutors, connecting with
students with proper and chosen resources with the option out and off campus tutor sessions.

| Project | Type | Description |
|---|---|---|
| `android_app` | Kotlin native Android Studio Application | The android app frontend application that takes user input, calls apis and returns information and functionality in the application |
| `backend_api` | Typescript Custom APIs | Collection of project created custom APIs that create connection between database and android application |

> GitHub Submission File: TutorBridge

---

### Intended User Base
- Insitutions such as Emeris
- Tutors availed by institutions
- Students of the institutions

---

### Features/Scope
## Features/Scope
| Feature | Status | What it does |
|---|---|---|
| Biometric unlock | Done | Offered after sign-in and switched in Settings, it asks for a biometric scan (or the phone's screen lock) each time the app starts. Without it you simply stay signed in |
| Single sign-on | Done | Sign in with a Google account, or with email and password (Firebase Authentication). "Forgot password?" on the sign-in screen emails a reset link |
| Payroll claim export | Done | Tutors see their hours and earnings for each monthly claim period (up to 24 months back, loaded in steps), built from completed sessions, and export it as a PDF voucher or as the API's CSV |
| Reschedule a session | Done | Either person can move an upcoming session instead of cancelling it. A tutor's move stands; a student's move of a confirmed session goes back to the tutor to confirm. The other person is notified and the Google Calendar event follows |
| Manage your requests | Done | A student can edit an open request, or close it (sorted) or withdraw it (no longer wanted). Tutors who offered are told |
| Messaging | Done | A chat opens between a student and a tutor when an offer is accepted or a tutor is booked. Unread counts, push notification on a new message |
| Two-phase booking with overlap rejection | Done | A student requests a slot and the tutor confirms it. A slot that overlaps one of the tutor's confirmed sessions is rejected, both when it's requested and again when it's confirmed |
| Google Calendar | Done | Confirmed sessions go into the tutor's linked Google Calendar (or the student's, if only the student linked one), and the other person is invited |
| Match relevance | Done | The API scores each tutor against the student's own requests (shared subject, shared campus area, completed sessions). "Top Tutors For You" ranks on it and the "% match" badge shows it; with nothing to match on, the section is just "Tutors on Campus" |
| Session materials pack | Partly done | Tutors keep a private to-do list and notes for each session on the Session Prep screen |
| Institutional structure | Done | Each user belongs to an institution and only sees that institution's tutors. Institution admins add tutor accounts and review verification documents through the API; the app has no admin screens yet |

**Also in the app**
- Tuition request board: students post what they need, tutors apply with a rate, and the student accepts one and schedules the session
- Tutor availability: tutors publish weekly hours, and students pick a slot inside them
- Push notifications that open the relevant screen when tapped
- Account deletion with a 14-day grace period

---

### Tech Stack
* **Android app:** Kotlin, Jetpack Compose (Material 3), Retrofit and OkHttp, Credential Manager (Google sign-in), AndroidX Biometric
* **Backend API:** Node.js, Express, TypeScript, Mongoose, Swagger UI (OpenAPI docs)
* **Database:** MongoDB Atlas
* **Firebase:** Authentication, Cloud Messaging (push notifications)
* **Google Cloud:** Google Calendar API
* **CI:** GitHub Actions

---

## Project Structure

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

## Architecture
<img width="1760" height="968" alt="image" src="https://github.com/user-attachments/assets/e1ac94b2-4733-4815-852b-9ffbb6387b94" />

Looking at the above diagram the architectural choices made for this project centers around the android application, the project
custom APIs, a database and external APIs with the android application being our device, custom APIs being a Node.js API run locally,
the database being a MongoDB atlas database and our external APIs being Firebase services as well as google workspace services. These choices
showcase the application needs as well as the group's best choices to make them.

The system has four parts:
* **Android app:** the client that students and tutors use.
* **TutorBridge API:** a Node.js/Express REST API run locally on port 5000, with interactive docs at `/docs`. It was hosted on Azure App Service during the project and has since been taken down to save cost.
* **Database:** MongoDB Atlas, which only the API talks to.
* **External services:** Firebase Authentication (sign-in), Firebase Cloud Messaging (push notifications) and the Google Calendar API.

The app signs users in with Firebase and sends the Firebase ID token with every API call. The API checks that token before it touches the database, so the device never connects to the database directly.

---

## Structural Design Considerations
The project is split into layers, so each part has one job.

**Android app (MVVM)**
* **Presentation:** Jetpack Compose screens in `ui/screens/`, one folder per feature. `TutorBridgeNavGraph.kt` shows the screen for the current route, and `RouteAccess.kt` sends a student or tutor to their own home screen if they open a page meant for the other role.
* **State:** `TutorBridgeViewModel` keeps the app's state in a single `TutorBridgeUiState` (a `StateFlow`). Screens only display that state and pass user actions back to the ViewModel.
* **Data:** `TutorBridgeRepository` and `AuthRepository` call the API through Retrofit, and `AuthInterceptor` adds the signed-in user's Firebase ID token to every request.

**Backend API**
* **Routes** (`src/routes/`): one Express router per resource (bookings, tuition requests, timesheets and so on). The admin reference data (subjects, provinces, areas) shares one generic CRUD router.
* **Middleware** (`src/middleware/`): `auth.ts` verifies the Firebase ID token, loads the user's TutorBridge profile and checks their role. `errors.ts` turns any unhandled error into a standard JSON error response.
* **Models** (`src/models/`): Mongoose schemas for MongoDB Atlas. Users, tuition requests, applications and bookings carry an `institutionId`, and the API filters on it, so people only see data from their own institution.
* **Utilities** (`src/utils/`): Google Calendar sync, push notifications and pagination. Two hourly jobs mark sessions complete once their end time passes and delete accounts whose 14-day grace period is over.

---

## Offline Sync
Screens read what was last saved in Room, and anything done offline goes into an outbox table. WorkManager sends the outbox as soon as there is a connection, retries with a growing delay if the server cannot be reached, and runs every 15 minutes as a backup. A `GET /health` call wakes the API when the app starts.

**Conflicts:** changes are sent in the order they were made, and the last one to reach the server wins. Settings merge field by field, so a later value replaces an earlier one. If the server refuses a change it is marked as failed on the Sync status screen, where it can be retried or discarded.

---

## Setup

### Prerequisites
* Node.js 22 (the version CI uses)
* Android Studio (its bundled JDK 17 is all the Android build needs)
* An Android emulator, or an Android phone with USB debugging turned on
* A MongoDB connection string (your own MongoDB Atlas cluster works; see the seed step)
* The Firebase service-account key for the TutorBridge Firebase project (ask the team; it's not in the repo)

### Clone the repository
```bash
git clone https://github.com/EMWCCN/prog7314-g2-2026-formative-2-part-2-st10438679.git
cd prog7314-g2-2026-formative-2-part-2-st10438679
```

### Set up the API
```bash
cd backend_api
npm install
```

Create `backend_api/.env`:
```env
MONGODB_URI=mongodb+srv://<user>:<password>@<cluster>.mongodb.net/TutorBridge
GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/service-account.json
PORT=5000

# Only needed for the Google Calendar integration
CALENDAR_TOKEN_KEY=<64 hex characters>
GOOGLE_OAUTH_CLIENT_ID=<web client id>
GOOGLE_OAUTH_CLIENT_SECRET=<web client secret>
```

* `GOOGLE_APPLICATION_CREDENTIALS` is the path to the Firebase service-account key (Firebase console → Project settings → Service accounts → **Generate new private key**). It must come from the same Firebase project as `android_app/app/google-services.json`, or the API rejects every sign-in.
* Generate `CALENDAR_TOKEN_KEY` with:
  ```bash
  node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
  ```

### Seed the database (first run only)
```bash
npm run seed
```
This adds an institution, subjects, provinces and areas, plus a test tutor, an institution admin and a platform admin (`platform@tutorbridge.test`, the only role allowed to change the shared subject, province and area lists). Without it the app's pickers are empty and there is no one to book. To use the seeded tutor or admin, sign **up** in the app with the email the script prints, using any password.

### Start the API
```bash
npm run dev
```
The API runs at http://localhost:5000, and its interactive docs are at http://localhost:5000/docs.

### Run the Android app
1. Open the **`android_app/`** folder (not the repo root) in Android Studio and let Gradle sync.
2. Make sure the API is running (see above). There's nothing to change: out of the box the app calls `http://127.0.0.1:5000/`, and each build runs `adb reverse`, so an emulator or a USB-connected phone reaches the API on your computer. Rebuild if you reconnect the phone.
   To use a different address, set `API_BASE_URL` in `android_app/local.properties` (this file isn't committed), for example the emulator's alias for your computer:
   ```properties
   API_BASE_URL=http://10.0.2.2:5000/
   ```
3. Select the **app** run configuration and press **Run**.

> **Google sign-in** only works on builds whose signing key is registered in the Firebase project. On your own machine, sign in with email and password, or add the SHA-1 from `./gradlew signingReport` in Firebase console → Project settings → Your apps.

### Test accounts
These accounts are in the team's database.

| Role | Email | Password |
|---|---|---|
| Student | tomnook@gmail.com | `TNook12345` |
| Tutor | jonesdillon634@gmail.com | `Tom1234@` |

### Run the tests
Each from the repo root:
```bash
cd backend_api
npm test
```
```bash
cd android_app
./gradlew testDebugUnitTest
```
Use `.\gradlew` on Windows. Outside Android Studio, Gradle needs JDK 17+: if `JAVA_HOME` isn't set, point it at Android Studio's bundled JDK (the `jbr` folder in its install directory).
---

## Version Control/GitHub Workflows

**Branching:** each feature is built on its own `feat-*` branch (for example `feat-EditProfile` and `feat-AppLanguage`) and merged through a pull request.

**CI:** [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and pull request, on every branch:
* The **backend** job installs the API's dependencies, compiles the TypeScript and runs the backend tests.
* The **android** job runs the unit tests and builds a debug APK.
* A passing run uploads the APK as the `tutorbridge-debug-apk` artifact. A failing Android run uploads the unit-test report instead.

A failed job puts a red cross next to the commit on GitHub, so the team can see which change broke the build and fix it before merging.

---

## References

### Android app
- Android Developers. 2026. *About Sign in with Google*. [Online]. Available at: https://developer.android.com/identity/sign-in/credential-manager-siwg [Accessed 21 September 2026].
- Android Developers. 2026. *Android Debug Bridge (adb)*. [Online]. Available at: https://developer.android.com/tools/adb [Accessed 21 September 2026].
- Android Developers. 2026. *Android Emulator networking*. [Online]. Available at: https://developer.android.com/studio/run/emulator-networking [Accessed 21 September 2026].
- Android Developers. 2025. *Authorize access to Google user data*. [Online]. Available at: https://developer.android.com/identity/authorization [Accessed 21 September 2026].
- Android Developers. 2026. *Build local unit tests*. [Online]. Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
- Android Developers. 2026. *Create and manage notification channels*. [Online]. Available at: https://developer.android.com/develop/ui/compose/notifications/channels [Accessed 21 September 2026].
- Android Developers. n.d. *Download Android Studio & app tools*. [Online]. Available at: https://developer.android.com/studio [Accessed 21 September 2026].
- Android Developers. 2026. *Get started with Jetpack Compose*. [Online]. Available at: https://developer.android.com/develop/ui/compose/documentation [Accessed 21 September 2026].
- Android Developers. 2026. *Google Maps intents for Android*. [Online]. Available at: https://developer.android.com/guide/components/google-maps-intents [Accessed 21 September 2026].
- Android Developers. 2026. *Guide to app architecture*. [Online]. Available at: https://developer.android.com/topic/architecture [Accessed 21 September 2026].
- Android Developers. 2026. *Kotlin coroutines on Android*. [Online]. Available at: https://developer.android.com/kotlin/coroutines [Accessed 21 September 2026].
- Android Developers. 2026. *Localize your app*. [Online]. Available at: https://developer.android.com/guide/topics/resources/localization [Accessed 21 September 2026].
- Android Developers. 2026. *Material Design 3 in Compose*. [Online]. Available at: https://developer.android.com/develop/ui/compose/designsystems/material3 [Accessed 21 September 2026].
- Android Developers. 2026. *Notification runtime permission*. [Online]. Available at: https://developer.android.com/develop/ui/compose/notifications/notification-permission [Accessed 21 September 2026].
- Android Developers. 2026. *PdfDocument*. [Online]. Available at: https://developer.android.com/reference/android/graphics/pdf/PdfDocument [Accessed 21 September 2026].
- Android Developers. 2026. *Per-app language preferences*. [Online]. Available at: https://developer.android.com/guide/topics/resources/app-languages [Accessed 21 September 2026].
- Android Developers. 2026. *Save simple data with SharedPreferences*. [Online]. Available at: https://developer.android.com/training/data-storage/shared-preferences [Accessed 21 September 2026].
- Android Developers. 2025. *Setting up file sharing*. [Online]. Available at: https://developer.android.com/training/secure-file-sharing/setup-sharing [Accessed 21 September 2026].
- Android Developers. 2026. *Show a biometric authentication dialog*. [Online]. Available at: https://developer.android.com/identity/sign-in/biometric-auth [Accessed 21 September 2026].
- Android Developers. 2026. *StateFlow and SharedFlow*. [Online]. Available at: https://developer.android.com/kotlin/flow/stateflow-and-sharedflow [Accessed 21 September 2026].
- Android Developers. 2026. *ViewModel overview*. [Online]. Available at: https://developer.android.com/topic/libraries/architecture/viewmodel [Accessed 21 September 2026].
- Firebase. 2026. *Add Firebase to your Android project*. [Online]. Available at: https://firebase.google.com/docs/android/setup [Accessed 21 September 2026].
- Firebase. 2026. *Authenticate with Firebase using password-based accounts on Android*. [Online]. Available at: https://firebase.google.com/docs/auth/android/password-auth [Accessed 21 September 2026].
- Firebase. 2026. *Authenticate with Google on Android*. [Online]. Available at: https://firebase.google.com/docs/auth/android/google-signin [Accessed 21 September 2026].
- Firebase. 2026. *Get started with Firebase Cloud Messaging in Android apps*. [Online]. Available at: https://firebase.google.com/docs/cloud-messaging/android/get-started [Accessed 21 September 2026].
- Google. 2024. *Gson* (Version 2.11.0). [Computer program]. Available at: https://github.com/google/gson [Accessed 21 September 2026].
- Google for Developers. 2026. *Client authentication*. [Online]. Available at: https://developers.google.com/android/guides/client-auth [Accessed 21 September 2026].
- JetBrains. n.d. *Kotlin documentation*. [Online]. Available at: https://kotlinlang.org/docs/home.html [Accessed 21 September 2026].
- Square, Inc. 2023. *OkHttp* (Version 4.12.0). [Computer program]. Available at: https://lysine.dev/okhttp/ [Accessed 21 September 2026].
- Square, Inc. 2024. *Retrofit* (Version 2.11.0). [Computer program]. Available at: https://lysine.dev/retrofit/ [Accessed 21 September 2026].

### Backend API
- Express.js. n.d. *Error handling*. [Online]. Available at: https://expressjs.com/en/guide/error-handling/ [Accessed 21 September 2026].
- Express.js. n.d. *Routing*. [Online]. Available at: https://expressjs.com/en/guide/routing/ [Accessed 21 September 2026].
- Express.js. 2026. *cors* (Version 2.8.6). [Computer program]. Available at: https://github.com/expressjs/cors [Accessed 21 September 2026].
- Express.js. 2026. *Multer* (Version 2.4.0). [Computer program]. Available at: https://github.com/expressjs/multer [Accessed 21 September 2026].
- Firebase. 2026. *Add the Firebase Admin SDK to your server*. [Online]. Available at: https://firebase.google.com/docs/admin/setup [Accessed 21 September 2026].
- Firebase. 2026. *Manage users*. [Online]. Available at: https://firebase.google.com/docs/auth/admin/manage-users [Accessed 21 September 2026].
- Firebase. 2026. *Send a message using Firebase Admin SDK*. [Online]. Available at: https://firebase.google.com/docs/cloud-messaging/send/admin-sdk [Accessed 21 September 2026].
- Firebase. 2026. *Verify ID tokens*. [Online]. Available at: https://firebase.google.com/docs/auth/admin/verify-id-tokens [Accessed 21 September 2026].
- Google for Developers. 2026. *Events: delete*. [Online]. Available at: https://developers.google.com/workspace/calendar/api/v3/reference/events/delete [Accessed 21 September 2026].
- Google for Developers. 2026. *Events: insert*. [Online]. Available at: https://developers.google.com/workspace/calendar/api/v3/reference/events/insert [Accessed 21 September 2026].
- Google for Developers. 2026. *OpenID Connect*. [Online]. Available at: https://developers.google.com/identity/openid-connect/openid-connect [Accessed 21 September 2026].
- Google for Developers. 2026. *Using OAuth 2.0 for web server applications*. [Online]. Available at: https://developers.google.com/identity/protocols/oauth2/web-server [Accessed 21 September 2026].
- Microsoft. 2026. *The TypeScript handbook*. [Online]. Available at: https://www.typescriptlang.org/docs/handbook/intro.html [Accessed 21 September 2026].
- MongoDB. n.d. *Connection strings*. [Online]. Available at: https://www.mongodb.com/docs/manual/reference/connection-string/ [Accessed 21 September 2026].
- MongoDB. n.d. *MongoDB Atlas: multi-cloud database service*. [Online]. Available at: https://www.mongodb.com/docs/atlas/ [Accessed 21 September 2026].
- Mongoose. 2026. *Schemas* (Mongoose v9.10.1). [Online]. Available at: https://mongoosejs.com/docs/guide.html [Accessed 21 September 2026].
- Mongoose. 2026. *Using TypeScript with Mongoose* (Mongoose v9.10.1). [Online]. Available at: https://mongoosejs.com/docs/typescript.html [Accessed 21 September 2026].
- Motte, S. 2026. *dotenv* (Version 17.4.2). [Computer program]. Available at: https://github.com/motdotla/dotenv [Accessed 21 September 2026].
- Node.js. 2026. *Crypto* (Node.js v22.23.2 documentation). [Online]. Available at: https://nodejs.org/docs/latest-v22.x/api/crypto.html [Accessed 21 September 2026].
- Node.js. 2026. *Test runner* (Node.js v22.23.2 documentation). [Online]. Available at: https://nodejs.org/docs/latest-v22.x/api/test.html [Accessed 21 September 2026].
- OpenAPI Initiative. 2020. *OpenAPI Specification v3.0.3*. [Online]. Available at: https://spec.openapis.org/oas/v3.0.3.html [Accessed 21 September 2026].
- Osame, H. 2026. *tsx* (Version 4.23.13). [Computer program]. Available at: https://github.com/privatenumber/tsx [Accessed 21 September 2026].
- Scott, S. 2024. *swagger-ui-express* (Version 5.0.1). [Computer program]. Available at: https://github.com/scottie1984/swagger-ui-express [Accessed 21 September 2026].
- Sharp, R. 2026. *nodemon* (Version 3.1.14). [Computer program]. Available at: https://nodemon.io/ [Accessed 21 September 2026].
- Wirtz, D. 2025. *bcrypt.js* (Version 3.0.3). [Computer program]. Available at: https://github.com/dcodeIO/bcrypt.js [Accessed 21 September 2026].

### Hosting and CI
- GitHub. n.d. *Building and testing Java with Gradle*. [Online]. Available at: https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle [Accessed 21 September 2026].
- GitHub. n.d. *Building and testing Node.js*. [Online]. Available at: https://docs.github.com/en/actions/tutorials/build-and-test-code/nodejs [Accessed 21 September 2026].
- GitHub. n.d. *Store and share data with workflow artifacts*. [Online]. Available at: https://docs.github.com/en/actions/tutorials/store-and-share-data [Accessed 21 September 2026].

### README and version control
- GitHub. n.d. *Basic writing and formatting syntax*. [Online]. Available at: https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax [Accessed 21 September 2026].
- GitHub. n.d. *GitHub flow*. [Online]. Available at: https://docs.github.com/en/get-started/using-github/github-flow [Accessed 21 September 2026].
- GitHub. n.d. *Organizing information with tables*. [Online]. Available at: https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/organizing-information-with-tables [Accessed 21 September 2026].

### YouTube Video Link
- https://youtu.be/15W6me7PNA4
