# DevTracker

Offline-first Android app (Kotlin, Compose, Material 3, Room, DataStore, WorkManager).

Build: open this folder in Android Studio (Koala or newer, JDK 17), let Gradle sync, run on Android 8.0+.
Tests: `./gradlew test` (unit) and `./gradlew connectedAndroidTest` (Room, needs a device or emulator).

Layout: `data/` (entities, DAO, repository, prefs, reminders, Codeforces API) and `ui/` (ViewModel, shared components, screens).
Network is used only by the optional Codeforces rating sync.
