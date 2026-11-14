# DisasterLink — How to Run

## Backend

1. Install Node.js 22.5+.
2. Open a terminal in `disasterlink-backend_Module4_5`.
3. Install packages:

```bash
npm install
```

4. Create `.env` from `.env.example` and set your own secrets.
5. Start the server:

```bash
npm start
```

6. Verify:

```text
http://localhost:8080/health
```

Expected response contains `"status":"ok"`.

## Android

1. Open `DisasterLinkAndroid_Module4_5` in Android Studio.
2. Use JDK 17.
3. Let Android Studio sync/download Gradle dependencies.
4. Set `API_BASE_URL` in `app/src/main/java/com/example/disasterlink/data/AppConfig.kt`.
5. Run on a physical Android phone for BLE/ESP32 testing.

For a real phone, use the backend PC's LAN IP. For the Android emulator, use `10.0.2.2`.

## Authority onboarding

Use the bootstrap key once to create the first District Collector invite. After that, District Collector+ users can issue invites from the app.

## Rescue team test

1. Register at least two RESCUE users with valid employee IDs such as `ABC1234XYZ`.
2. Sign in as District Collector+.
3. Open **Rescue Teams**.
4. Enter the rescue employee IDs.
5. Register the team.
6. Open **Assign Task**.
7. Select an emergency and the registered team.
8. The task must contain the team's UUID in `assignedTeamId`.
9. Sign in as a member of that team and confirm the task appears.
10. Accept, complete and submit at least three evidence references.

## Offline hardware test

```text
Android
  ↓ BLE
Field ESP32
  ↓ LoRa
Gateway ESP32
  ↓ LAN/Wi-Fi
Backend
  ↓
Authority
```

The field node and gateway each have an NVS queue. If the backend is unavailable, the gateway buffers the packet and forwards it when connectivity returns.

Copy:

```text
esp32/field-node/config.h.example → esp32/field-node/config.h
esp32/gateway/secrets.h.example → esp32/gateway/secrets.h
```

Never commit those generated secret files.

## Automated QA

```bash
npm run test:qa
```

The suite exercises the critical task assignment workflow, access control, idempotency, evidence validation, LoRa gateway ingestion and team deletion safety.
