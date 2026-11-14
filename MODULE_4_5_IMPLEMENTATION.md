# DisasterLink — Module 4 + Module 5

This package is based directly on the tested Module 3 project. Module 2 email OTP and Module 3 BLE/LoRa offline SOS remain included.

## Module 4 — Persistent Sessions

### Implemented
- Login, registration and real email-OTP login now return a long-lived opaque refresh token in addition to the 30-day access JWT.
- Refresh sessions are stored server-side as SHA-256 hashes; raw refresh tokens are never stored in SQLite.
- Android stores the refresh token in the existing session `SharedPreferences` and keeps the local session until the user explicitly logs out.
- App startup selects `dashboard` when a local session exists, so killing/reopening the app does not return the user to login.
- App startup and WorkManager attempt an access-token refresh when connectivity is available. If the device is offline, the local session is not cleared.
- Explicit Logout revokes the server refresh session when possible and always clears the Android local session.

### New API
```text
POST /api/auth/refresh
{ "refreshToken": "..." }

POST /api/auth/logout
Authorization: Bearer <access-token>
{ "refreshToken": "..." }
```

## Module 5 — Authority & Rescue Operations

### Authority workflow
```text
Incoming emergency
        ↓
Review reporter + location
        ↓
Select rescue team
        ↓
Create / reassign task
        ↓
Monitor task status
        ↓
Verify completion evidence
```

### Rescue workflow
```text
Receive assigned task
        ↓
Accept task
        ↓
IN_PROGRESS
        ↓
Upload minimum 3 evidence images
        ↓
Complete task
        ↓
Authority can monitor completion
```

### Implemented improvements
- One-tap operational synchronization from the Authority and Rescue dashboards.
- Local Room remains the operational cache, so dashboards continue displaying previously synchronized emergencies, teams and tasks while offline.
- Offline-created authority tasks receive a local client ID and synchronize idempotently when the configured LAN/backend becomes reachable.
- Rescue accept/reject/complete actions made while offline are retained locally and synchronized later.
- Rescue teams can share their current location from the dashboard through the existing backend team-location API.
- Task response audit events are recorded server-side for creation, assignment, acceptance, rejection and completion/evidence submission.
- Rescue task details display the response audit trail when the backend is reachable.
- Existing authority tools for team management, task assignment, authority invites, district analysis, performance and command center remain available.
- Backend dashboard endpoints provide operational summary/activity data for future web dashboard integration.

## Offline/LAN behavior

"Offline" means no internet connection is required for the citizen-to-field-node SOS path. For Authority/Rescue phones to exchange operational records through the existing backend without internet, the phones and backend computer must still have a reachable local LAN/hotspot. The backend remains the source of truth for shared authority/rescue data.

## Files changed

### Android
- `AppDatabase.kt` — Room v4 migration for task client IDs.
- `Models.kt` — task `clientId`.
- `AppDao.kt` — task deletion needed for idempotent replacement.
- `AuthApi.kt` — refresh/logout APIs and refresh-token response.
- `Repository.kt` — persistent-session refresh, explicit logout, operational sync and team-location update.
- `Sync.kt` — task synchronization, task events and reporter identity synchronization.
- `SyncWorker.kt` — refresh + emergency/task synchronization.
- `MainActivity.kt` — migration registration.
- `DisasterLinkApp.kt` — session-aware startup, operational sync cards, rescue location sharing, task audit trail.

### Backend
- `db/db.js` — `refresh_sessions`, `tasks.client_id`, and `task_events`.
- `middleware/auth.js` — refresh-session creation/rotation/revocation.
- `routes/auth.js` — refresh/logout endpoints.
- `routes/emailOtp.js` — refresh token on successful OTP login.
- `routes/emergencies.js` — preserves reporter identity on offline sync.
- `routes/tasks.js` — idempotent task creation and task audit events.
- `routes/dashboard.js` — operational summary/activity endpoints.
- `server.js` — dashboard route registration.

## Test sequence

1. Build/install the Android app.
2. Log in once.
3. Force-close the app and reopen it: it should open the dashboard without asking for login.
4. Turn off internet and open the app: local session and local dashboard data should remain available.
5. Turn LAN/backend connectivity on and use **Refresh operations**.
6. As Authority, create/assign a task.
7. As Rescue, refresh operations and accept it.
8. Complete it with at least 3 images.
9. Open task details and verify the audit trail.
10. Repeat the task flow with connectivity temporarily unavailable, then restore LAN and press Refresh operations. The local changes should synchronize without creating duplicate tasks.


## Final authentication decision
- Login is now email + one-time verification code for Citizen, Rescue and Authority.
- Aadhaar/government ID + password login has been retired at the backend and removed from the Android login UI/API client.
- Government/employee identity fields remain in registration and local/session data because rescue-team membership, authority permissions, audit data and offline SOS identity still use them.
- Email OTP remains real SMTP-backed; the OTP is never returned to the Android client.
