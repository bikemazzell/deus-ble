package io.github.deusble.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import io.github.deusble.core.Observation;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Android bridge. The host must grant permissions before starting the scanner. */
public final class AndroidScanBackend implements ScanBackend {
    public static final ParcelUuid HUMAN_INTERFACE_SERVICE = new ParcelUuid(
        UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"));
    private final Context context;
    private final Map<Callback, Session> sessions = new IdentityHashMap<>();
    private static final class Session {
        final BluetoothLeScanner scanner;
        final ScanCallback callback;
        Session(BluetoothLeScanner scanner, ScanCallback callback) { this.scanner = scanner; this.callback = callback; }
    }
    public AndroidScanBackend(Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
    }
    private boolean granted(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }
    private BluetoothAdapter adapter() {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }
    @Override @SuppressLint("MissingPermission") public Availability availability() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (!granted(Manifest.permission.BLUETOOTH_SCAN) || !granted(Manifest.permission.BLUETOOTH_CONNECT))
                return Availability.MISSING_PERMISSION;
        }
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return Availability.MISSING_PERMISSION;
        BluetoothAdapter adapter = adapter();
        if (adapter == null) return Availability.UNSUPPORTED;
        if (!adapter.isEnabled()) return Availability.BLUETOOTH_DISABLED;
        return adapter.getBluetoothLeScanner() == null ? Availability.UNSUPPORTED : Availability.READY;
    }
    @Override @SuppressLint("MissingPermission") public synchronized void start(Callback callback) {
        Objects.requireNonNull(callback, "callback");
        if (sessions.containsKey(callback)) return;
        Availability status = availability();
        if (status == Availability.MISSING_PERMISSION) throw new SecurityException("Bluetooth scan permission is missing");
        if (status != Availability.READY) throw new IllegalStateException("Bluetooth scanner is not ready: " + status);
        BluetoothAdapter adapter = adapter();
        BluetoothLeScanner scanner = adapter == null ? null : adapter.getBluetoothLeScanner();
        if (scanner == null) throw new IllegalStateException("Bluetooth scanner became unavailable");
        ScanCallback platformCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) { deliver(result); }
            @Override public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult result : results) deliver(result);
            }
            private void deliver(ScanResult result) {
                ScanRecord record = result.getScanRecord();
                if (record == null) return;
                List<ParcelUuid> services = record.getServiceUuids();
                try {
                    Observation observation = new Observation(result.getDevice().getAddress(),
                        record.getDeviceName(), result.getRssi(), System.currentTimeMillis(),
                        result.getTimestampNanos(), record.getBytes());
                    callback.onAdvertisement(observation, services != null && services.contains(HUMAN_INTERFACE_SERVICE));
                } catch (SecurityException exception) {
                    callback.onPermissionDenied();
                }
            }
            @Override public void onScanFailed(int errorCode) { callback.onFailure(errorCode); }
        };
        sessions.put(callback, new Session(scanner, platformCallback));
        scanner.startScan(Collections.singletonList(new ScanFilter.Builder()
                .setServiceUuid(HUMAN_INTERFACE_SERVICE).build()),
            new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), platformCallback);
    }
    @Override @SuppressLint("MissingPermission") public synchronized void stop(Callback callback) {
        Session session = sessions.remove(callback);
        if (session != null) session.scanner.stopScan(session.callback);
    }
}
