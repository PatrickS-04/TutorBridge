import { randomInt, createHash, timingSafeEqual } from "crypto";

/*
 * Attendance check-in rules, kept out of the route so they can be unit tested.
 *
 * The tutor's phone shows a short-lived 6-digit code (as a QR code and as text).
 * The student scans or types it on their own phone, which proves both phones were
 * together during the session - the claim voucher's "verified with student
 * attendance check-in" line is then backed by a real record instead of a label.
 */

 // The Check-in opens 15 minutes before the session starts
 export const CHECK_IN_OPENS_MINUTES_BEFORE = 15;

 // Codes are only valid for 5 minutes before a new one needs to be issued
 export const CODE_TTL_MS = 5 * 60 * 1000;

 // 5 Wrong code attempts before tutor has to show a new one
 export const MAX_FAILED_ATTEMPTS = 5;

 export function isCheckInOpen(start: Date, end: Date, now = new Date()): boolean {
     const opensAt = start.getTime() - CHECK_IN_OPENS_MINUTES_BEFORE * 60 * 1000;
     return now.getTime() >= opensAt && now.getTime() <= end.getTime();
 }

// Six digits from a cryptographically secure source, leading zeros kept
export function newCheckInCode(): string {
    return randomInt(0, 1_000_000).toString().padStart(6, "0");
}

// Only a hash is stored with the bookingId mixed in so one code's hash means nothing for another session
export function hashCheckInCode(bookingId: string, code: string): string {
    return createHash("sha256").update(`${bookingId}:${code}`).digest("hex");
}

export function checkInCodeMatches(bookingId: string, code: string, storedHash?: string | null): boolean {
    if (!storedHash || !/^\d{6}$/.test(code))
    {
        return false;
    }

    const given = Buffer.from(hashCheckInCode(bookingId, code), "hex");
    const stored = Buffer.from(storedHash, "hex");

    // Constant-time compare, so response timing does not leak how close a guess was
    return given.length === stored.length && timingSafeEqual(given, stored);
}

// What the QR code holds. The app checks the booking id against the session being checked in to
export function checkInQrPayload(bookingId: string, code: string): string {
    return `tutorbridge://checkin?booking=${bookingId}&code=${code}`;
}