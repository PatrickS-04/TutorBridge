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
- [Design Considerations](#design-considerations)
- [Setup](#setup)
- [Version Control/GitHub Workflows](#version-controlgithub-workflows)
- [AI Disclosure](#ai-disclosure)

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
* Biometric Scanner (in POE Part 3 Final Submission)
* Single sign-on 
* Payroll claim export 
* Two phase booking; rejection overlap 
* Google Calendar 
* Match relevance 
* Session materials pack 
* Institutional structural plan

---

### Tech Stack
* Kotlin
* Typescript
* MongoDB Atlas
* Firebase
* Google Cloud (Google Calendar)
* Jetpack Compose

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
custom APIs, a database and external APIs with the android application being our device, custom APIs being hosted by Azure App Service,
the database being a MongoDB atlas database and our external APIs being Firebase services as well as google workspace services. These choices
showcase the application needs as well as the group's best choices to make them.

---

## Structural Design Considerations
The project's structural design uses Kotlin native Android Studio Application UI (Jepack Compose) as Presentation Layer, 

---

## Setup

### Prerequisites
* Node.js 
* Android Studio
* Android Emulator/Android Device
* Student Email/Password = tomnook@gmail.com/TNook12345
* Tutor Email/Password = dillonrinkwest@gmail.com/Dillon1234@

### Clone Repository
```bash
git clone <https://github.com/EMWCCN/prog7314-g2-2026-formative-2-part-2-st10438679.git>
```

### Setup Custom APIs
```bash
cd backend_api
npm install
```

### Create .env file
```env
MONGODB_URI=mongodb+srv://<user>:<password>@<cluster>.mongodb.net/TutorBridge
GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/service-account.json
PORT=5000

# Only needed for the Google Calendar integration
CALENDAR_TOKEN_KEY=<64 hex characters>
GOOGLE_OAUTH_CLIENT_ID=<web client id>
GOOGLE_OAUTH_CLIENT_SECRET=<web client secret>
```

### Generate a calendar key with:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

### Start APIs
```bash
npm run dev
```

### Run the Android app

1. Open the **`android_app/`** folder (not the repo root) in Android Studio and let Gradle sync.
2. Select the **app** run configuration and press **Run**.

---

## Version Control/GitHub Workflows
```yml
name: CI

# Every push, every branch - the POE brief requires CI on every push.
on: [push, pull_request]

permissions:
  contents: read

jobs:
  backend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend_api
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: backend_api/package-lock.json
      - run: npm ci
      - run: npm run build
      - run: npm test

  android:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android_app
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: gradle
      - run: chmod +x gradlew
      - run: ./gradlew testDebugUnitTest assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: tutorbridge-debug-apk
          path: android_app/app/build/outputs/apk/debug/*.apk
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: unit-test-report
          path: android_app/app/build/reports/tests/
```
This is the GitHub workflow file for TutorBridge project solution. To summarize the automation and version control
implementation within GitHub actions the project has both its backend API and android application tested each time during
every published commit and has its success and fails recorded. These automated tests allows the project solution to be
properly mointored throughout its development stages with each commit and these actions keep the project's team informed
with each change and feature addition success and fails tracked through its commit history.

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
