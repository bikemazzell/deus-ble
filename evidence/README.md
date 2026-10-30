# Local validation

Validation date: 2026-10-30. The latest complete run is `acceptance-validation.log`.

- Core: 10 tests passed (4 parser, 6 deduplication).
- Android scanner JVM tests: 9 passed.
- Android emulator: 4 tests passed on API 36. These cover missing runtime permission, missing fine location permission, an unavailable Bluetooth service, and injected raw/distinct observations with late-callback suppression.
- Release lint: zero errors, one warning for the cached AndroidX test runner version (1.3.0).
- Core JAR, Android AAR, and source JARs assembled. Both publications were written to `build/maven`.

The runs used Gradle 8.12, Android Gradle Plugin 8.9.1, Java source/release level 17, and host JDK 21. CI uses JDK 17. Local JDK 17 CI execution and GitHub-hosted CI have not yet been observed.

`parser-red.log`, `deduper-red.log`, and `scanner-red.log` show failing tests against API stubs before implementation. `backend-red.log` shows the emulator permission failure before the Android bridge implementation. `permission-revocation-red.log` and `location-permission-red.log` show failing regressions before the corresponding fixes. Later green logs retain the check results at each stage.

Run the current acceptance checks with:

```sh
./gradlew :core:test :android:testDebugUnitTest :android:connectedDebugAndroidTest :android:lintRelease :core:assemble :android:assembleRelease publish
```

The emulator tests use synthetic observations or controlled Android contexts. They do not validate real BLE radio delivery or a detector. No real-detector acceptance test has run.
