/**
 * How well a tutor fits a student, 0-100, from what the student has actually
 * asked for. The home screen's "Top Tutors For You" ranks on this, and the
 * "% match" badge shows it, so it has to come from real signals:
 *
 *   subject  up to 55  the tutor teaches a subject the student has a live
 *                      (open or matched) request for; 40 if only a past one
 *   area     +25       the tutor is on a campus area the student asked for
 *   record   up to 20  completed sessions, 2 points each, capped at 10 sessions
 *
 * A tutor who shares neither a subject nor an area with the student scores 0,
 * however experienced: experience ranks tutors who fit, it does not make one
 * that does not fit look like a match.
 */

export interface StudentSignals {
  /** Subjects of requests that are still live. */
  liveSubjects: Set<string>;
  /** Subjects of requests that have ended. */
  pastSubjects: Set<string>;
  /** Areas of every request the student has posted. */
  areas: Set<string>;
}

export interface TutorFacts {
  subjectIds: string[];
  areaId?: string;
  completedSessions: number;
}

export const LIVE_SUBJECT_POINTS = 55;
export const PAST_SUBJECT_POINTS = 40;
export const AREA_POINTS = 25;
export const POINTS_PER_SESSION = 2;
export const MAX_SESSIONS_COUNTED = 10;

export function scoreTutor(tutor: TutorFacts, signals: StudentSignals): number {
  const live = tutor.subjectIds.some((id) => signals.liveSubjects.has(id));
  const past = tutor.subjectIds.some((id) => signals.pastSubjects.has(id));
  const subjectPoints = live ? LIVE_SUBJECT_POINTS : past ? PAST_SUBJECT_POINTS : 0;
  const areaPoints = tutor.areaId && signals.areas.has(tutor.areaId) ? AREA_POINTS : 0;

  if (subjectPoints === 0 && areaPoints === 0) return 0;

  const recordPoints = Math.min(tutor.completedSessions, MAX_SESSIONS_COUNTED) * POINTS_PER_SESSION;
  return Math.min(100, subjectPoints + areaPoints + recordPoints);
}

/** Turns a student's requests into the signals above. */
export function signalsFromRequests(
  requests: { subjectId: unknown; areaId: unknown; status: string }[]
): StudentSignals {
  const signals: StudentSignals = { liveSubjects: new Set(), pastSubjects: new Set(), areas: new Set() };
  for (const request of requests) {
    const live = request.status === "OPEN" || request.status === "MATCHED";
    (live ? signals.liveSubjects : signals.pastSubjects).add(String(request.subjectId));
    signals.areas.add(String(request.areaId));
  }
  return signals;
}
