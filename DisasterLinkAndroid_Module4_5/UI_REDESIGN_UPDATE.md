# DisasterLink UI redesign update

This version keeps the supplied `home_background.jpg` as the Home artwork and removes app-added slogan/HUD text from over the image. The artwork is displayed with `ContentScale.Crop` so it fills portrait Android screens without distortion.

## UI changes
- Home: supplied artwork, clean presentation, Login/Register actions, SOS in the top-right.
- Login: disaster/fire visual background.
- Register: flood/water visual background.
- Aadhaar: one input field, automatically formatted `1234-5678-9012` while storing 12 digits.
- Citizen, rescue and authority dashboards use compact cards, metrics and action tiles instead of a text-only layout.
- Feedback form is always available to a logged-in citizen.
- Rescue dashboard requests Bluetooth permission and the system Bluetooth-enable dialog, and requests location permission when entering the dashboard.
- Authority dashboard has four primary boxes: Rescue Teams, Assign Task, View Complaints, Emergency Command Center.
- Authority sections open as dedicated screens with separate cards for metrics, district complaints, department performance, citizen complaints and command-center cases.
- Rescue tasks retain assigned/pending/completed counts and show task creation, start and end times.
- Added authority team removal.

## Permissions behavior
The app no longer uses a custom in-app Bluetooth/location dialog for dashboard access. It requests Android permissions directly. If Bluetooth is off, it launches Android's system enable-Bluetooth prompt. If location services are off after permission is granted, it opens Android's Location settings.

## Validation
The updated Kotlin source was syntax-checked with the Kotlin compiler in this environment. A complete Android/Gradle build cannot be performed here because the supplied project does not contain `gradle-wrapper.jar` and this environment has no Gradle installation/network dependency cache.
