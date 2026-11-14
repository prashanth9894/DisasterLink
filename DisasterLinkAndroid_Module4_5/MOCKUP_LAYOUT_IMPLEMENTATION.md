# DisasterLink — Mockup Layout Implementation

This version uses the supplied mobile mockup as the visual reference for the coded Android UI.

## Kept unchanged
- The existing Home page artwork (`app/src/main/res/drawable-nodpi/home_background.jpg`) is retained as the Home hero image.
- SOS remains available from Home without login.

## Coded UI changes
- Citizen, Rescue Team and Authority dashboards use compact Android cards, metric tiles, status pills and bottom navigation.
- Authority analytics use coded bar/donut charts drawn with Jetpack Compose Canvas; the mockup image itself is not used as the dashboard UI.
- District-wise complaints and Department Performance are separate clickable screens.
- Emergency Command Center is a separate screen with case cards, assignment, status and start/end timing.
- Rescue Team task cards show date, start time, end time, status and citizen details.
- Task completion requires at least three selected evidence images.
- Feedback is a normal interactive form and remains available from the Citizen dashboard.
- Bluetooth and Location are requested through Android's system permission/settings flows when a dashboard is entered.
- Login and registration keep the fire/flood disaster backgrounds from the existing project.
- Aadhaar is one input field and displays as `1234-5678-9012` while storing 12 digits.
