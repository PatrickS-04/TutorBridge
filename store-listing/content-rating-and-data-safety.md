# Content rating and data safety answers

Answers to enter in Google Play Console, based on the app's code as of 5 November 2026. Play may word its questions differently, and it works out the final rating itself.

## App content

| Question | Answer |
|---|---|
| App or game | App |
| Free or paid | Free |
| Contains ads | No |
| Target audience | 13 to 15, 16 to 17, and 18 and over. Not under 13 |
| Account needed | Yes. Give reviewers a test student and test tutor login in Play Console, not in this repository |
| News, health, government or financial services app | No |

## Content rating questionnaire

**Category:** All other app types

| Question | Answer |
|---|---|
| Violence or blood | No |
| Sexual content or nudity | No |
| Profanity or crude humour in the app's own content | No |
| Drugs, alcohol or tobacco | No |
| Gambling or simulated gambling | No |
| Content that may frighten or disturb | No |
| Users can interact or exchange content | **Yes**, through one-to-one chat |
| Shares a user's physical location with others | No. There is no location permission. Users may type a meeting venue |
| Digital purchases in the app | No. Tutors' rates are shown, but payment does not happen in the app |

**Expected result:** a low age rating, with a notice that users interact.

## Young users

Under-18s can chat one-to-one with tutors, so these points matter before a real launch:

| Area | Current state |
|---|---|
| Minimum age and parent or guardian permission | Required tick box when creating an account, with the time recorded. It is the user's own declaration and is not verified |
| Reporting a user | In the chat menu. Reports go to institution admins with a copy of the latest 50 messages |
| Blocking a user | In the chat menu. Neither person can then send in that chat, but bookings and their notifications continue |
| Reviewing reports | Through the API only, because admins have no app screen and are not alerted to new reports. A real launch needs someone checking this queue |
| Tutor vetting | Institutions add tutors and review their verification documents |
| Play child safety standards | Check whether these apply, because the app has user-to-user chat |

## Data safety

### Overview

| Question | Answer |
|---|---|
| Collects or shares required user data types | Yes |
| All user data encrypted in transit | Yes. The hosted API and Google services use HTTPS |
| Users can request deletion | Yes, in Settings or by email. Play also asks for a web link, so use the privacy policy's deletion section |
| Data shared with third parties | No. Google, Azure and MongoDB act as service providers, and transfers between users happen at the user's request |

### Data collected

None of this is sold or used for advertising. "Required" means the feature needs it, and "Optional" means the user can leave it out.

| Play data type | What it is in TutorBridge | Required? | Purpose |
|---|---|---|---|
| Name | Full name | Required | App functionality, account management |
| Email address | Sign-in and contact email | Required | App functionality, account management |
| User IDs | Firebase user ID and account ID | Required | App functionality, account management |
| Other personal info | Bio, department, staff number, subjects, campus area, level | Optional | App functionality |
| Other financial info | Tutors' rates, hours and earnings for claims | Optional (tutors) | App functionality |
| Other in-app messages | Chat messages | Optional | App functionality |
| Calendar events | Session events added to a linked Google Calendar | Optional | App functionality |
| Other user-generated content | Requests, venues, tutors' session notes, and reports with their copied messages | Optional | App functionality, and reports for fraud prevention, security and compliance |
| Other personal info | Age confirmation | Required | Compliance |
| App interactions | Firebase Analytics usage events | Required | Analytics |
| Other actions | Bookings and attendance check-ins | Optional | App functionality |
| Device or other IDs | Push token and Firebase app instance ID | Required | App functionality, analytics |

**Not collected:** precise or approximate location from the device, contacts, photos or videos, audio, files, health data, payment details, and web browsing history. The app has no location, contacts, camera or storage permission.

Biometric data stays on the device, so it is not declared.
