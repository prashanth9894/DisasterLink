# DisasterLink — Prototype UI Implementation

This version follows the supplied mobile prototype as the visual reference:
- portrait phone layout
- navy/blue header styling
- rounded white cards with subtle shadows
- separate metric cards
- citizen dashboard with Bluetooth/location access area, feedback, emergency history and SOS
- rescue dashboard with Assigned/Pending/Completed tabs and date/start/end timing
- task detail with citizen information and evidence upload
- authority dashboard with four operational modules and separate analytics cards
- district-wise complaints, department performance and Emergency Command Center open as separate screens

The existing home artwork (`home_background.jpg`) is retained rather than replacing it with the prototype screenshot.

The Android app implements native Android permission/enable flows for Bluetooth and location rather than drawing fake permission dialogs.
