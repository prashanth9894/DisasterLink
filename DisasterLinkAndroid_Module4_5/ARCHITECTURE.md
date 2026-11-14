# DisasterLink Android architecture

## Layers
- `ui/`: Jetpack Compose screens, validation, role dashboards, task workflow, map WebView.
- `data/`: Room entities/DAO/repository, offline emergency/task/team/feedback data.
- `bluetooth/`: BLE scanner/GATT client for ESP32.

## Offline-first flow
1. Citizen creates SOS.
2. Room stores it immediately with `synced=false`.
3. If a BLE ESP32 is connected, the app can send SOS/location packets.
4. Rescue teams receive assigned tasks and can accept/reject/complete them.
5. Completion requires at least three evidence images.
6. Citizen can submit feedback after completion.
7. WorkManager retries REST synchronization when internet is connected.

## Identity rules in this prototype
- Citizen login: registered email + email OTP. Registration still collects the government ID required by the current backend/data model.
- Rescue login/register ID: `ABC1234XYZ`.
- Authority login: registered email + email OTP. Registration still collects employee/government identity fields required by the current backend/data model.
- Phone: India `+91` + exactly 10 digits, unique locally.
- Email: valid email format and unique locally.
- Password: 8–30 chars, at least one uppercase, number and special character.

## Authority types
- VAO
- Tahsildar
- District Collector
- State Disaster Management Authority
- CM

The UI displays the authority scope and gates rescue-team registration to district/state senior authority levels in this local prototype. Production access control must be enforced by the central backend.

## Location and Bluetooth
Modern Android does not permit a normal application to silently enable Bluetooth. The app therefore shows an explicit confirmation and launches the system enable prompt.

When internet is available, the dashboard asks the user to enable location if location services are off. Rescue screens periodically update the registered team's local GPS position while active. When BLE connects and a location fix is available, a `LOCATION|lat|lon` packet is sent to the ESP32.

## Map
When internet is available, the authority dashboard renders geolocated emergencies with Leaflet/OpenStreetMap. When offline, it falls back to a stored coordinate list.

## Backend
The Android app does not connect directly to MySQL/PostgreSQL. Use:

`Android -> HTTPS REST API <- Website -> Central DB`

The included Retrofit/WorkManager layer is a prototype contract. Real email verification, authentication tokens, server-side duplicate protection, image upload storage, live team tracking and role authorization should be implemented in the backend before deployment.
