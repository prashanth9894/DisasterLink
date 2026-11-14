# SOS + DeviceRow fix

## Fixed compilation error
`DisasterLinkApp.kt` referenced `DeviceRow(...)` but no composable with that name existed. A safe Material 3 `DeviceRow` was added for discovered Bluetooth devices.

## SOS without login
The Home screen now has a visible **SOS** button in the top-right corner. It opens the SOS screen without requiring a login.

An SOS can be saved immediately as an anonymous emergency (`citizenId = ANONYMOUS`). If a user is already logged in, the normal citizen ID and district are used.

The SOS action does **not** block sending while waiting for location permission. If a last-known location is already available it is attached; otherwise the emergency is saved immediately and location permission is requested afterward for future reports.

This is intentionally compatible with modern Android behavior: an app cannot silently enable Bluetooth; Android's system prompt/settings flow must be used.
