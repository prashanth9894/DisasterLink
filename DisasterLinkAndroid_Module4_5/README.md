# DisasterLink — Tamil Nadu Disaster Management Android App

Android Studio project using Jetpack Compose, Room, WorkManager, BLE and offline-first storage.

## Open and run
1. Extract the ZIP.
2. Open the **DisasterLinkAndroid** folder in Android Studio.
3. Set the Gradle JDK to **JDK 17**.
4. Sync Gradle.
5. Run on an Android device/emulator.

## Kotlin daemon startup fix
This project is configured with:

`kotlin.compiler.execution.strategy=in-process`

This avoids the separate Kotlin compile daemon and fixes the common startup error:

`The daemon has terminated unexpectedly on startup attempt #1 with error code: 0.`

If Android Studio cached the old build state, close Android Studio and delete the project's `.gradle` directory before syncing again.

## Gradle wrapper
The original supplied project did not include `gradle/wrapper/gradle-wrapper.jar`. Android Studio can use its configured Gradle installation/distribution, but the command-line `gradlew` script requires that JAR. If you need the wrapper from the command line, generate it once with a local Gradle 8.9 installation:

`gradle wrapper --gradle-version 8.9`

## Notes
- Target platform: Tamil Nadu.
- Home background is included at `app/src/main/res/drawable-nodpi/home_background.jpg`.
- BLE/ESP32 testing should be done on a physical Android device.

## Module 4 + Module 5 update

This build also contains the next implementation modules:

- **Module 4:** persistent login sessions with server refresh sessions, offline-safe session restoration, and explicit logout revocation.
- **Module 5:** operational Authority/Rescue dashboard improvements, offline task queue synchronization, rescue-team location sharing, and server-side task audit history.

See `../MODULE_4_5_IMPLEMENTATION.md` in the combined delivery package for the exact workflow and test sequence.
