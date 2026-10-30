package io.github.deusble.android;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidBackendTest {
    @Test public void deniedLocationIsExplicitEvenWhenBluetoothPermissionsAreGranted() {
        Context original = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context noLocation = new ContextWrapper(original) {
            @Override public Context getApplicationContext() { return this; }
            @Override public int checkSelfPermission(String permission) {
                return Manifest.permission.ACCESS_FINE_LOCATION.equals(permission)
                    ? PackageManager.PERMISSION_DENIED : PackageManager.PERMISSION_GRANTED;
            }
            @Override public Object getSystemService(String name) { return null; }
        };
        assertEquals(ScanBackend.Availability.MISSING_PERMISSION, new AndroidScanBackend(noLocation).availability());
    }
    @Test public void injectedAdvertisementsReachRawAndDistinctListenersOnAndroid() {
        final ScanBackend.Callback[] receiver = new ScanBackend.Callback[1];
        ScanBackend backend = new ScanBackend() {
            public Availability availability() { return Availability.READY; }
            public void start(Callback callback) { receiver[0] = callback; }
            public void stop(Callback callback) {}
        };
        final int[] counts = new int[2];
        DetectorScanner scanner = new DetectorScanner(backend, new DetectorScanner.Listener() {
            public void onObservation(io.github.deusble.core.Observation observation) { counts[0]++; }
            public void onDetection(io.github.deusble.core.Packet packet) {
                assertEquals(42, packet.targetId()); assertEquals(27, packet.soil()); counts[1]++;
            }
        });
        byte[] bytes = new byte[21]; bytes[0]=2; bytes[1]=1; bytes[2]=5; bytes[17]=42; bytes[19]=(byte)255;
        io.github.deusble.core.Observation observation = new io.github.deusble.core.Observation(
            "AA:BB:CC:DD:EE:FF", "Synthetic", -45, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtimeNanos(), bytes);
        scanner.selectAddress(observation.address()); scanner.start();
        receiver[0].onAdvertisement(observation, true); receiver[0].onAdvertisement(observation, true);
        assertEquals(2, counts[0]); assertEquals(1, counts[1]);
        scanner.stop(); receiver[0].onAdvertisement(observation, true); assertEquals(2, counts[0]);
    }
    @Test public void unavailableBluetoothServiceIsExplicit() {
        Context original = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context noBluetooth = new ContextWrapper(original) {
            @Override public Context getApplicationContext() { return this; }
            @Override public int checkSelfPermission(String permission) { return PackageManager.PERMISSION_GRANTED; }
            @Override public Object getSystemService(String name) {
                return Context.BLUETOOTH_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        assertEquals(ScanBackend.Availability.UNSUPPORTED, new AndroidScanBackend(noBluetooth).availability());
    }
    @Test public void ungrantedRuntimePermissionProducesExplicitAvailability() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String permission = Build.VERSION.SDK_INT >= 31 ? Manifest.permission.BLUETOOTH_SCAN : Manifest.permission.ACCESS_FINE_LOCATION;
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission));
        assertEquals(ScanBackend.Availability.MISSING_PERMISSION, new AndroidScanBackend(context).availability());
    }
}
