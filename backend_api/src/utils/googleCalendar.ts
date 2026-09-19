import crypto from "crypto";
import { CalendarLink, User, IBooking, LessonMode } from "../models";

//Writes confirmed bookings into the tutor's Google Calendar. The tutor links
//their account once (POST /calendar/link) and we keep the refresh token; every
//confirm after that mints a short-lived access token and posts one event.
//Talking to the two Google endpoints directly keeps the googleapis SDK - and
//its dependency tree - out of the build for what is three fetch calls.

const TOKEN_URL = "https://oauth2.googleapis.com/token";

//Write events and read them back for the timesheet; nothing else.
export const CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events";

function eventsUrl(calendarId: string, eventId?: string) {
  const base = `https://www.googleapis.com/calendar/v3/calendars/${encodeURIComponent(calendarId)}/events`;
  return eventId ? `${base}/${encodeURIComponent(eventId)}` : base;
}

function encryptionKey() {
  const hex = process.env.CALENDAR_TOKEN_KEY;
  if (!hex) throw new Error("CALENDAR_TOKEN_KEY is not set");
  const key = Buffer.from(hex, "hex");
  if (key.length !== 32) throw new Error("CALENDAR_TOKEN_KEY must be 32 bytes of hex");
  return key;
}

//A Google refresh token is a standing key to the user's calendar, so it never
//sits in Mongo in the clear. Stored as iv:authTag:ciphertext.
export function encryptToken(plain: string): string {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", encryptionKey(), iv);
  const body = Buffer.concat([cipher.update(plain, "utf8"), cipher.final()]);
  return [iv.toString("hex"), cipher.getAuthTag().toString("hex"), body.toString("hex")].join(":");
}

export function decryptToken(stored: string): string {
  const [iv, tag, body] = stored.split(":");
  if (!iv || !tag || !body) throw new Error("Stored token is malformed");
  const decipher = crypto.createDecipheriv("aes-256-gcm", encryptionKey(), Buffer.from(iv, "hex"));
  decipher.setAuthTag(Buffer.from(tag, "hex"));
  return Buffer.concat([decipher.update(Buffer.from(body, "hex")), decipher.final()]).toString("utf8");
}

async function postForm(body: Record<string, string>) {
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(body),
  });
  const json: any = await response.json();
  if (!response.ok) {
    throw new Error(`Google token error: ${json.error_description ?? json.error ?? response.status}`);
  }
  return json;
}

function oauthClient() {
  const clientId = process.env.GOOGLE_OAUTH_CLIENT_ID;
  const clientSecret = process.env.GOOGLE_OAUTH_CLIENT_SECRET;
  if (!clientId || !clientSecret) throw new Error("GOOGLE_OAUTH_CLIENT_ID / _SECRET are not set");
  return { client_id: clientId, client_secret: clientSecret };
}

//The app hands us the one-time offline-access code; this trades it for the
//refresh token we keep. The id_token rides along, and its payload is the
//cheapest way to learn which Google account was just linked - it came straight
//from Google over TLS, so there is nothing to verify a second time.
export async function exchangeAuthCode(authCode: string) {
  const token = await postForm({
    ...oauthClient(),
    code: authCode,
    grant_type: "authorization_code",
    redirect_uri: "",
  });
  if (!token.refresh_token) {
    throw new Error("Google did not return a refresh token - the app must request offline access");
  }
  return {
    refreshToken: token.refresh_token as string,
    //Only present if the email scope came along; the caller falls back to the
    //signed-in account when it did not.
    email: readIdTokenEmail(token.id_token) ?? (await fetchAccountEmail(token.access_token)),
  };
}

async function fetchAccountEmail(accessToken?: string): Promise<string | undefined> {
  if (!accessToken) return undefined;
  try {
    const response = await fetch("https://www.googleapis.com/oauth2/v3/userinfo", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!response.ok) return undefined;
    return ((await response.json()) as any).email;
  } catch {
    return undefined;
  }
}

export function readIdTokenEmail(idToken?: string): string | undefined {
  const payload = idToken?.split(".")[1];
  if (!payload) return undefined;
  try {
    return JSON.parse(Buffer.from(payload, "base64url").toString("utf8")).email;
  } catch {
    return undefined;
  }
}

async function accessTokenFor(refreshToken: string) {
  const token = await postForm({
    ...oauthClient(),
    refresh_token: refreshToken,
    grant_type: "refresh_token",
  });
  return token.access_token as string;
}

type BookingEvent = Pick<IBooking, "subjectName" | "startTime" | "endTime" | "mode" | "location">;

//Times go out as UTC instants - an RFC3339 string with a Z offset is already
//unambiguous, so Google needs no separate timeZone field.
export function buildEvent(booking: BookingEvent, studentName: string, studentEmail?: string) {
  const event: Record<string, unknown> = {
    summary: `${booking.subjectName} with ${studentName}`,
    description: "Booked through TutorBridge.",
    start: { dateTime: booking.startTime.toISOString() },
    end: { dateTime: booking.endTime.toISOString() },
  };
  if (booking.mode !== LessonMode.ONLINE && booking.location) event.location = booking.location;
  //Inviting the student puts the same session on their calendar without us
  //needing them to link an account too.
  if (studentEmail) event.attendees = [{ email: studentEmail }];
  return event;
}

/**
 * Put a freshly confirmed booking on the tutor's calendar. Fire-and-forget in
 * the same spirit as notify(): Google being down must never fail the confirm
 * the tutor just made, so failures are recorded on the link and left there.
 */
export function syncBookingCreated(booking: IBooking) {
  createEvent(booking).catch((error) => console.error("calendar sync failed:", error));
}

async function createEvent(booking: IBooking) {
  const link = await CalendarLink.findOne({ userId: booking.tutorId, syncEnabled: true }).select("+refreshTokenEnc");
  if (!link) return;

  try {
    const accessToken = await accessTokenFor(decryptToken(link.refreshTokenEnc));
    const student = await User.findById(booking.studentId).select("fullName email").lean();

    const response = await fetch(`${eventsUrl(link.calendarId)}?sendUpdates=all`, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify(buildEvent(booking, student?.fullName ?? "your student", student?.email)),
    });
    const created: any = await response.json();
    if (!response.ok) throw new Error(created.error?.message ?? `Calendar API ${response.status}`);

    //Straight to the collection: the caller has already saved and responded.
    await booking.updateOne({ $set: { googleCalendarEventId: created.id } });
    await link.updateOne({ $set: { lastSyncedAt: new Date() }, $unset: { lastSyncError: "" } });
  } catch (error) {
    await link.updateOne({ $set: { lastSyncError: String(error) } });
    throw error;
  }
}

/**
 * Drop the event again when the booking is cancelled, so the calendar cannot
 * keep showing a session that is off.
 */
export function syncBookingCancelled(booking: IBooking) {
  if (!booking.googleCalendarEventId) return;
  deleteEvent(booking).catch((error) => console.error("calendar cleanup failed:", error));
}

async function deleteEvent(booking: IBooking) {
  const link = await CalendarLink.findOne({ userId: booking.tutorId }).select("+refreshTokenEnc");
  if (!link) return;

  const accessToken = await accessTokenFor(decryptToken(link.refreshTokenEnc));
  const response = await fetch(`${eventsUrl(link.calendarId, booking.googleCalendarEventId!)}?sendUpdates=all`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  //404/410 mean Google has already dropped it - same end state, not an error.
  if (!response.ok && response.status !== 404 && response.status !== 410) {
    throw new Error(`Calendar API ${response.status}`);
  }
  await booking.updateOne({ $unset: { googleCalendarEventId: "" } });
}
