# Prototype-style Android UI implementation

This build replaces the earlier generic dashboard presentation with a phone-first Compose layout based on the supplied prototype reference.

## Visual structure
- Home: full-screen existing `home_background.jpg`, centered DisasterLink branding, bottom Login/Register/SOS panel.
- Login: fire image with a bottom white sign-in sheet.
- Registration: flood image header with rounded white form sheet and a single formatted Aadhaar field (`1234-5678-9012`).
- Citizen: compact top bar + SOS pill, Bluetooth/location access cards, two action tiles, full-width Send SOS card, recent emergencies, bottom navigation.
- Rescue: three compact metrics, Assigned/Pending/Completed tabs, task cards with date/start/end time and View/Accept actions.
- Task Details: citizen details, task timing, evidence slots and Submit Task.
- Authority: four compact metric cards, four separate action boxes, live operations map.
- Authority analytics: district-wise complaints and department performance open as dedicated screens.
- Emergency Command Center opens as a dedicated screen with task/location/team/status/timing rows.

## Behavior retained
- Login-free SOS.
- Native Android Bluetooth permission/enable flow.
- Native Android location permission and location-services flow.
- Feedback form remains accessible.
- Rescue completion requires at least 3 images.
- Existing Room/Repository data and offline-first SOS storage are retained.
