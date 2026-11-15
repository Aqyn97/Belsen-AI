# Product vision

## Purpose

Belsen-AI helps people plan daily actions and understand whether they are staying consistent. Its tagline is “Plan better. Stay consistent. Grow.” The product is a web application for people seeking simple personal organization, including those unfamiliar with productivity tools.

The original first-MVP target was November 6, 2026; the complete MVP has not been released. The authentication, goals, tasks and habit backends are implemented. The authenticated analytics dashboard API is also implemented. Account, goal/progress/planning, task and habit interfaces are implemented. Dashboard/analytics interfaces, immutable closed-report finalization and coaching remain planned; Google browser login and deployment remain unverified. The table below describes the target MVP.

## Primary journey

1. Register with email/password or sign in with Google, then confirm a timezone.
2. Create a measurable goal with a target, unit and optional deadline.
3. Plan weekly goal units, create tasks and select habit weekdays.
4. Record goal progress, complete tasks and check off daily habits.
5. Review weekly metrics and optionally select “Analyze my progress.”

## MVP scope

| Area | Planned behavior |
| --- | --- |
| Accounts | Registration, login, Google sign-in, secure sessions, logout and a private timezone-aware profile |
| Goals | Create/edit, target and unit, optional deadline, explicit progress entries, weekly targets and archive |
| Tasks | Create/edit, due dates, optional duration and goal association, complete/reopen, reschedule and archive |
| Habits | Create/edit, active weekdays, daily completion, planned rest days and history |
| Analytics | Task completion, strict habit consistency, weekly goal progress and understandable summaries |
| AI coach | User-triggered analysis and up to three practical suggestions based on stored progress |

Social features, payments, leaderboards, calendar integrations, mobile applications and AI-generated success probabilities are outside the MVP.

## What the percentages mean

### Task completion

Task completion is **tasks originally due in the reporting period that are complete at the reporting cutoff ÷ all tasks originally due in that period × 100**.

The original due date is the first due date assigned to a task. Moving the current due date does not change this reporting date. Reopening a task before the cutoff makes it incomplete for that report. Completing it after the cutoff does not change a closed report. Undated tasks do not enter the denominator until a first due date is assigned.

### Habit consistency

Habit consistency is **eligible days on which every scheduled habit is complete ÷ all eligible days × 100**.

An eligible day has at least one scheduled habit. If reading and walking are both scheduled on Monday, completing only reading does not make Monday successful. If two of three eligible days are fully complete, consistency is 66.7%, even if many individual habits were completed on the third day. This measures complete daily routines, so the interface must show both the percentage and successful/eligible day counts.

A habit-specific planned rest removes only that habit’s obligation. Other scheduled habits still determine the day’s success. If no obligations remain, the day is excluded. Rest must be planned and cancelled before the day starts, so incomplete work cannot be erased afterward. Full-day account exclusions are deferred. Schedule changes and archive take effect tomorrow or later and preserve earlier obligations.

### Weekly goal progress

Weekly goal progress is **completed weekly target units ÷ planned weekly target units × 100**, capped at 100%. Show each goal separately because units such as pages and kilometres cannot be meaningfully added. Preserve actual units even when they exceed the target. Task completion never automatically increases goal progress.

### Time and missing data

Weeks run from Monday 00:00 to the following Monday 00:00, exclusive, in the reporting timezone. The current week's cutoff is the current instant; closed weeks use their ending boundary. Future habit days in the current week are excluded until they start; today's result remains provisional.

If a denominator is zero, display “No scheduled activity” or “No weekly target” with no percentage. A new user should receive guidance to create an activity, not a fabricated score. Archive actions preserve historical reports. Frozen weeks retain their reporting timezone; timezone changes apply prospectively as defined in the architecture. Immutable closed results remain a finalization-stage goal. The current dashboard labels closed periods CLOSED_UNFINALIZED because late commits may still change them.

## AI coaching boundaries

The coach offers suggestions, not verified measurements. Actual metrics remain visibly separate. Requests are explicit, limited and handled on the backend. A provider failure must leave the rest of the application usable. The coach never changes user records automatically.

## Visual direction

The display identity is **belsen.** Use primary yellow `#FACC15`, background `#FAFAF7`, dark text `#171717`, and white surfaces `#FFFFFF`.

Use dark text on yellow primary actions, readable typography, consistent spacing, visible focus states and responsive layouts. Verify contrast for actual component combinations. Avoid decorative gradients and unnecessary animation. Provide clear loading, validation, success, error and empty states. No interface is part of Day 1.

## Main risks

Historical reporting and authentication require careful tests. Google credentials, hosting access and AI budget are external dependencies. The seven-day schedule leaves limited room for integration failures; reduce visual polish before weakening security or changing the approved formulas.
