package io.github.deusble.android;
import io.github.deusble.core.Observation;
/** Platform boundary. A backend must keep callback ownership separate for each start. */
public interface ScanBackend {
    enum Availability { READY, MISSING_PERMISSION, BLUETOOTH_DISABLED, UNSUPPORTED }
    interface Callback {
        void onAdvertisement(Observation observation, boolean hasHumanInterfaceService);
        void onFailure(int platformCode);
        default void onPermissionDenied() { onFailure(0); }
    }
    Availability availability();
    void start(Callback callback);
    void stop(Callback callback);
}
