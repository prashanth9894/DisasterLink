# DisasterLink Android – Testing Checklist

## Current backend address
The Android app is configured for the developer PC LAN address:
`http://192.168.31.222:8080/`

Login and registration use HTTP/REST only. Bluetooth and location permissions are not requested on the login/register screens.

## 1. Backend smoke test
1. Start the Node backend with `npm start`.
2. On the PC open `http://192.168.31.222:8080/health`.
3. Confirm the response contains `"status":"ok"`.

## 2. Android authentication
1. Connect the Android phone by USB debugging.
2. Keep phone and PC on the same LAN/Wi-Fi.
3. Install the debug APK from Android Studio.
4. Register a Citizen with a real 10-digit Indian mobile number (6–9 first digit), 12-digit Aadhaar, 6-digit PIN, district, and a password meeting the displayed policy.
5. Confirm registration succeeds.
6. Log out and log back in with registered email + email OTP + Citizen role.
7. Repeat with Rescue/Authority using a valid employee ID; Authority also needs a valid invite code.

## 3. Important phone-number rule
The registration form shows `+91` as a UI prefix, but the API now receives the 10-digit number only. This prevents a backend validator that expects exactly 10 digits from rejecting every registration.

## 4. Bluetooth/LoRa test
Bluetooth is deliberately isolated from authentication. Only open **ESP32 Connection** when you are ready to connect the field node.

Flow:
`Android → BLE → Field ESP32 → LoRa → Gateway ESP32 → Wi-Fi → Backend → SQLite`

## 5. Known environment limitation
This workspace cannot flash or electrically test the physical ESP32/Ra-02 hardware. Hardware verification must be done on your Arduino IDE + boards. The supplied hardware design is for 2 × ESP32-WROOM development boards and 2 × Ai-Thinker Ra-02/SX1278 433 MHz modules.
