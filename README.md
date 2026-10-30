# deus-ble

An independent Java 17 parser and Android BLE scan adapter for candidate Deus advertisements. Apache-2.0 licensed. It contains no proprietary code or research binaries.

**Hardware support is unverified.** The parser follows static observations of advertisement fields. Synthetic tests do not establish detector identity, full protocol coverage, or reliability with a real detector. The library has no GATT control API.

## Build and consume

Install JDK 17 and Android SDK 36. Set `ANDROID_HOME` or set `sdk.dir` in an untracked `local.properties` file.

```sh
./gradlew :core:test :android:testDebugUnitTest :android:lintRelease :core:assemble :android:assembleRelease publish
```

The development Maven repository is `build/maven`. Add its absolute path to your app's dependency repositories:

```groovy
maven { url = uri('/absolute/path/to/deus-ble/build/maven') }
```

Then add the adapter, which includes the core dependency:

```groovy
implementation 'io.github.deusble:deus-ble-android:0.1.0-SNAPSHOT'
// Java-only users:
implementation 'io.github.deusble:deus-ble-core:0.1.0-SNAPSHOT'
```

`publishToMavenLocal` also supports the user's Maven cache. These coordinates are provisional and are not a Maven Central release. No GitHub repository or release is implied by them.

## Core API

```java
Observation observation = new Observation(address, name, rssi,
    wallTimeMillis, monotonicNanos, completeAdvertisement);
ParseResult result = AdvertisementParser.parse(observation);
if (result.packet() != null) {
    Packet packet = result.packet();
    int target = packet.targetId();
    int soil = packet.soil();
}
```

`Observation` preserves address, nullable name, RSSI, wall-clock milliseconds, monotonic nanoseconds, and nullable raw bytes. It normalizes addresses to uppercase and copies byte arrays on input and output. `Packet` exposes `targetId()`, `soil()`, `frame()`, `mode()` and `observation()`. Each parse result holds exactly one packet or failure: `MISSING_DATA`, `TOO_SHORT`, `BAD_SIGNATURE`, or `INVALID_TARGET`.

The parser expects the complete advertisement, at least 21 bytes, starting with `02 01 05`. Byte 17 is an unsigned target ID below 100. Soil is `90 - unsigned(byte 19) / 4`, with integer division, so its range is 27–90. The full unsigned byte 20 is the frame value; 0–127 means `PUSH`, and 128–255 means `AUTO`. All unknown and trailing bytes remain in the observation. The generic prefix alone does not identify a detector.

`FrameDeduplicator(maxDevices, expiryNanos).accept(packet)` returns true for a distinct detection. It suppresses only consecutive equal frame values for each address. Frame changes and wraparound count as distinct. Expiry starts at the last observation, including a suppressed repeat. A backward timestamp starts a new epoch for that device. The least recently observed device is evicted at capacity. `reset()` clears all history. Repeated sweeps that reuse the same frame may remain indistinguishable; hardware testing must resolve this limit.

## Android API

Android API 26 or newer is required. The adapter discovers advertisements with service UUID `0x1812` and excludes names containing `RTKDeus`. These are candidate devices, not verified identities. Call `selectAddress(address)` after the user selects a device. Selection never changes automatically; null clears it.

```java
DetectorScanner scanner = new DetectorScanner(new AndroidScanBackend(context),
    new DetectorScanner.Listener() {
        @Override public void onDevice(Observation candidate) {
            // Add or update the candidate list.
        }
        @Override public void onObservation(Observation raw) {
            // Every eligible advertisement from the selected address.
        }
        @Override public void onDetection(Packet packet) {
            // Distinct frames from the selected address.
        }
        @Override public void onError(DetectorScanner.Error error, int platformCode) {
            // Show the error and let the user retry.
        }
    });
scanner.selectAddress(chosenAddress);
scanner.start();
// When the owning component ends scanning:
scanner.stop();
```

Listener callbacks run on the caller or backend callback thread, under the scanner's lock. Keep them short, do not throw, and post UI work to the main thread. Methods are serialized. `start()` and `stop()` are safe to repeat. Old-session callbacks are ignored. Stops and restarts clear deduplication history. The default deduplicator holds 64 devices with a five-second expiry; the three-argument constructor accepts a configured deduplicator.

`onState` reports `STOPPED`, `SCANNING`, or `ERROR`. `SCANNING` means the scan request was issued; it does not prove that Bluetooth delivered an advertisement. Errors are `MISSING_PERMISSION`, `BLUETOOTH_DISABLED`, `UNSUPPORTED`, and `SCAN_FAILED`. `platformCode` preserves Android's asynchronous scan failure code; zero denotes an availability check or synchronous failure. `onParseFailure` reports malformed selected-device observations without discarding the raw observation.

The app owns runtime permission requests and any foreground service. Grant `ACCESS_FINE_LOCATION` on every supported Android version. On Android 12+, also grant `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`. This is the explicit v0.1 permission policy: the library does not assert `neverForLocation` and requires fine location permission even if the host adds that flag. Device settings and location policy may also affect scan delivery. Location permission does not guarantee a GPS fix. GPS geotagging has separate fix-age and lifecycle requirements.

`AndroidScanBackend` provides the platform bridge. `ScanBackend` permits an injected backend for deterministic app or emulator tests. This does not simulate radio delivery or hardware identity. No background service or permission prompt is started by the library.

## Validation

Unit tests cover parsing, immutable data, bounded deduplication, lifecycle errors, device filtering, selection, and late callbacks. `./gradlew :android:connectedDebugAndroidTest` runs Android permission, missing-hardware, and injected-observation tests on a connected emulator with a fresh test installation. The `fixtures` directory contains only synthetic data. The `evidence` directory records local test-first runs.

Before claiming detector support, capture real advertisements, confirm identity and service/name filtering, compare target and soil with the detector display, test repeated sweeps, and test screen-off operation. No real-detector acceptance has been completed.
