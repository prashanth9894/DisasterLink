# Gradle fix for DisasterLink Android

This project is configured for:
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin 2.0.21
- Java/JDK 17

The wrapper distribution URL uses the Aliyun Gradle mirror instead of services.gradle.org because the latter was timing out on the original setup.

## Important
The original ZIP did not contain the official Gradle wrapper JAR. If Android Studio reports that `gradle-wrapper.jar` is missing, install Gradle 8.9 locally once and run from this project directory:

    gradle wrapper --gradle-version 8.9

That will generate `gradle/wrapper/gradle-wrapper.jar`.

Alternatively, in Android Studio set Gradle distribution to a local Gradle 8.9 installation.

## Android Studio
1. Extract this ZIP.
2. Open the `DisasterLinkAndroid` folder.
3. Use JDK 17.
4. Sync Gradle.
5. Run on a real Android phone for BLE/ESP32 testing.
