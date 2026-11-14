# DisasterLink Final Setup — Task Verification + Offline BLE Evidence

## 1. Backend

```powershell
cd disasterlink-backend_Module4_5
npm install
Copy-Item .env.example .env
```

Edit `.env` and set:
- `JWT_SECRET`
- `ADMIN_BOOTSTRAP_KEY`
- `GATEWAY_KEY`
- SMTP values for email OTP

Start:

```powershell
npm start
```

Health check:

```text
http://localhost:8080/health
```

## 2. Android

Open `DisasterLinkAndroid_Module4_5` in Android Studio.

Set the backend LAN URL in:

```text
app/src/main/java/com/example/disasterlink/data/AppConfig.kt
```

Example:

```kotlin
const val API_BASE_URL = "http://192.168.1.42:8080/"
```

The Android phone and backend PC must be on the same LAN for normal operational sync.

The project includes a small Gradle bootstrap wrapper JAR because the original package did not contain `gradle-wrapper.jar`. The wrapper downloads the pinned Gradle 8.9 distribution on the first build.

## 3. Evidence workflow

### Rescue phone — online path

1. Open **Rescue Tasks → Active Assigned Tasks**.
2. Accept the task.
3. Select at least 3 evidence images.
4. Tap **Upload Evidence**.
5. When the backend is reachable over operational Wi-Fi/LAN, the actual JPEGs are uploaded to the backend.
6. Tap **Submit for Authority Verification**.

### Rescue phone — offline fallback

1. Connect the phone to `DisasterLink Field Node` using the existing BLE screen.
2. Select at least 3 evidence images.
3. Tap **Upload Evidence (Wi-Fi / ESP32 fallback)**.
4. If Wi-Fi/backend is unavailable, the app transfers the actual JPEGs to the field ESP32.
5. Tap **Submit for Authority Verification**.

The Rescue app never completes the task.

### ESP32

The field node stores evidence in LittleFS:

```text
/evidence/<task-id>/<index>.jpg
```

Image bytes use BLE only. LoRa is not used for photographs.

### Authority phone

1. Open **Verify Evidence**.
2. Select the task.
3. Tap **Load Actual Evidence**.
4. The app first retrieves the actual images from the backend over Wi-Fi/LAN. If that path is unavailable and the Authority phone is connected to the field ESP32, it automatically falls back to BLE.
5. Review the received images.
6. Only after at least 3 readable images are locally available does **Complete Task** become enabled.
7. Tap **Complete Task**.

The backend then performs an atomic:

```text
PENDING_VERIFICATION → COMPLETED
Emergency → RESOLVED
```

Only an authenticated `AUTHORITY` can call the verification endpoint.

## 4. Rejection workflow

Authority may select **Reject**:

```text
PENDING_VERIFICATION → IN_PROGRESS
```

The Rescue team can correct the evidence and submit again.

## 5. Strict task segregation

Rescue task navigation is split into three separate pages:

- Active Assigned Tasks
- Pending Verification
- Completed Tasks

A completed task never appears in the Active page, and a verification-pending task never appears in the Completed page.

## 6. ESP32 wiring

For the current SX1278/Ra-02 SPI hardware:

| LoRa | ESP32 |
|---|---:|
| VCC | 3.3V |
| GND | GND |
| SCK | GPIO 18 |
| MISO | GPIO 19 |
| MOSI | GPIO 23 |
| NSS/CS | GPIO 5 |
| RST | GPIO 14 |
| DIO0 | GPIO 26 |

Never power the Ra-02/SX1278 from 5V.

## 7. Validation performed

Backend:

```powershell
npm install
npm test
```

The QA suite verifies:
- Rescue cannot directly complete a task.
- Rescue can submit evidence only from `IN_PROGRESS`.
- Minimum 3 evidence references are required.
- Rescue cannot call authority verification.
- `PENDING_VERIFICATION` cannot be reassigned.
- Authority can reject verification.
- Authority can verify and complete the task.
- An emergency cannot be directly resolved before task verification.
- Verification is atomic for task + emergency state.
- Duplicate verification is rejected.
- Existing LoRa and gateway tests continue to pass.

The ESP32 firmware must still be compiled/flashed on your actual Arduino/ESP32 hardware because this environment does not contain the Arduino ESP32 toolchain.

## 8. Online Wi-Fi evidence workflow

When the Rescue phone and backend are reachable on the operational Wi-Fi/LAN, the Rescue app uploads the actual compressed JPEG evidence to the backend:

```text
Rescue Android
   ↓ Wi-Fi/LAN
POST /api/tasks/:id/evidence
   ↓
Backend evidence storage
   ↓
Protected image reference stored on task
```

The Authority verification screen uses the task's protected evidence references to retrieve the actual JPEG bytes over Wi-Fi/LAN:

```text
Authority Android
   ↓ Wi-Fi/LAN + Bearer token
GET /api/tasks/:id/evidence/:index
   ↓
Actual JPEG
   ↓
Authority image preview
```

The image endpoint is authenticated; evidence is not exposed through an unauthenticated static folder.

If the backend/Wi-Fi path is unavailable, the existing ESP32/BLE path remains available as the offline fallback. The Authority screen automatically tries the backend first and then BLE when a connected field ESP32 is available.

## 9. Completed-task reassignment protection

Completed tasks are terminal. The Authority assignment screen now hides the **Reassign task** action for `COMPLETED` tasks and displays an informational message instead. The backend independently rejects:

```text
PATCH /api/tasks/:id/reassign
```

with HTTP `409` for completed tasks. Verification-pending and rejected tasks are also protected from reassignment through the dedicated reassign endpoint.
