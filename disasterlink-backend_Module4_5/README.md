# DisasterLink Backend

Express + SQLite backend for DisasterLink — offline disaster communication and rescue coordination.

## Requirements

- Node.js 22.5+ (uses Node's built-in `node:sqlite`)
- Android client on the same LAN/hotspot for local-network operation
- Optional SMTP provider for email OTP

## First-time setup

```bash
npm install
copy .env.example .env
```

On Linux/macOS:

```bash
cp .env.example .env
```

Generate secrets:

```bash
npm run seed-admin-key
```

Put the generated values into `.env`. Configure SMTP if you want email OTP delivery.

## Run

```bash
npm start
```

Default URL: `http://localhost:8080`

Health check:

```text
GET /health
```

## Android configuration

Edit:

```text
DisasterLinkAndroid_Module4_5/app/src/main/java/com/example/disasterlink/data/AppConfig.kt
```

Set `API_BASE_URL` to the backend computer's LAN address, for example:

```text
http://192.168.1.42:8080/
```

For an Android emulator, use `http://10.0.2.2:8080/` when the backend runs on the development PC.

For a real phone, the phone and backend PC must be on the same Wi-Fi/hotspot LAN.

## Authority onboarding

The first District Collector invite can be generated with the bootstrap key:

```text
POST /api/invites
x-admin-key: <ADMIN_BOOTSTRAP_KEY>
```

After the first District Collector account exists, District Collector+ users can generate invites from the Android app.

## Critical rescue workflow

```text
Authority
  -> register rescue team using government/employee IDs
  -> backend verifies RESCUE users
  -> backend returns Team UUID
  -> authority assigns emergency to Team UUID
  -> rescue member sees assigned task
  -> accept
  -> in progress
  -> submit 3+ evidence references
  -> completed / emergency resolved
```

The backend rejects duplicate active tasks for the same emergency and prevents deletion of a team that still has an active task.

## Offline workflow

```text
Android
  -> BLE
  -> Field ESP32
  -> LoRa
  -> Gateway ESP32
  -> Backend when LAN/Wi-Fi is available
```

The field node keeps an NVS outbox when a gateway ACK is unavailable. The gateway keeps an NVS queue when the backend is unavailable. Retries use stable packet identity (`deviceId + sequence`) and backend idempotency.

## ESP32 setup

Do not commit physical secrets. Copy the templates:

```text
esp32/field-node/config.h.example -> config.h
esp32/gateway/secrets.h.example -> secrets.h
```

Correct path for the second line:

```text
esp32/gateway/secrets.h.example -> esp32/gateway/secrets.h
```

Keep the LoRa frequency, SPI pins and network key consistent between field node and gateway. Confirm the radio frequency is legal for your deployment.

## QA

Run the automated integration suite:

```bash
npm run test:qa
```

It covers:

- authority invite creation and registration
- authority rank enforcement
- rescue-team registration
- duplicate member protection
- emergency creation
- Team UUID task assignment
- duplicate active-task prevention
- rescue task visibility
- accept/reassign/re-accept
- completion evidence validation
- audit history
- team location validation
- LoRa gateway authentication
- LoRa SOS ingestion and deduplication
- active-team deletion safety

## Production notes

Use HTTPS, strong secrets, a managed database and rate limiting before public deployment. The current SQLite implementation is intentionally simple for the prototype/small deployment target.


## Wi-Fi Evidence Verification

Rescue evidence is compressed on the Android phone and uploaded one image at a time to `POST /api/tasks/:id/evidence` when the operational Wi-Fi/LAN is available. The backend stores the actual JPEG bytes under its private evidence directory and returns authenticated API references. Authorities retrieve the actual image bytes through `GET /api/tasks/:id/evidence/:index`; no unauthenticated static evidence directory is exposed.

If Wi-Fi/backend access is unavailable, the existing BLE-to-ESP32 evidence path remains available.
