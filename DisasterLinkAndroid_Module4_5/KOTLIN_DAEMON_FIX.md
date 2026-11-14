# Kotlin daemon startup fix

If Android Studio previously showed:

> The daemon has terminated unexpectedly on startup attempt #1 with error code: 0.
> Kotlin compile daemon is ready

this project now forces Kotlin compilation **in-process**, disables the Gradle daemon for this project, and disables Kotlin incremental compilation. This removes the dependency on a separate Kotlin compile daemon.

## Android Studio settings
Use **JDK 17** for Gradle:

`File > Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK`

Select an installed JDK 17 (Android Studio's bundled JDK 17 is fine if available).

Then:
1. Close Android Studio.
2. Delete the project's `.gradle` directory (if present).
3. Reopen the `DisasterLinkAndroid` folder.
4. Click **Sync Project with Gradle Files**.
5. Run the app.

## Why this was changed
Kotlin's separate compiler daemon can terminate immediately on some Windows/JDK/memory combinations. `kotlin.compiler.execution.strategy=in-process` makes the Kotlin compiler run inside the Gradle process instead, avoiding that daemon startup failure.

If Gradle itself still stops, reduce/raise `org.gradle.jvmargs` according to available RAM. The current setting is intentionally conservative at 1536 MB. If Android Studio is configured to use JDK 21, switch the project Gradle JDK to JDK 17 before syncing.
