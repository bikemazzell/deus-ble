package io.github.deusble.android;

import io.github.deusble.core.AdvertisementParser;
import io.github.deusble.core.FrameDeduplicator;
import io.github.deusble.core.Observation;
import io.github.deusble.core.Packet;
import io.github.deusble.core.ParseResult;
import java.util.Locale;
import java.util.Objects;

/** Serialized scan lifecycle. Listener calls run on the caller or backend callback thread. */
public final class DetectorScanner {
    public enum State { STOPPED, SCANNING, ERROR }
    public enum Error { MISSING_PERMISSION, BLUETOOTH_DISABLED, UNSUPPORTED, SCAN_FAILED }
    public interface Listener {
        default void onDevice(Observation observation) {}
        default void onObservation(Observation observation) {}
        default void onDetection(Packet packet) {}
        default void onParseFailure(Observation observation, ParseResult.Failure failure) {}
        default void onState(State state) {}
        default void onError(Error error, int platformCode) {}
    }
    private final ScanBackend backend;
    private final Listener listener;
    private final FrameDeduplicator deduplicator;
    private State state = State.STOPPED;
    private String selectedAddress;
    private ScanBackend.Callback active;

    public DetectorScanner(ScanBackend backend, Listener listener) {
        this(backend, listener, new FrameDeduplicator(64, 5_000_000_000L));
    }
    public DetectorScanner(ScanBackend backend, Listener listener, FrameDeduplicator deduplicator) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.deduplicator = Objects.requireNonNull(deduplicator, "deduplicator");
    }
    public synchronized void selectAddress(String address) {
        String normalized = address == null ? null : address.toUpperCase(Locale.ROOT);
        if (!Objects.equals(selectedAddress, normalized)) deduplicator.reset();
        selectedAddress = normalized;
    }
    public synchronized String selectedAddress() { return selectedAddress; }
    public synchronized State state() { return state; }

    public synchronized void start() {
        if (active != null) return;
        ScanBackend.Availability availability;
        try { availability = backend.availability(); }
        catch (SecurityException exception) { fail(Error.MISSING_PERMISSION, 0); return; }
        catch (IllegalStateException exception) { fail(Error.SCAN_FAILED, 0); return; }
        if (availability != ScanBackend.Availability.READY) {
            fail(Error.valueOf(availability.name()), 0);
            return;
        }
        deduplicator.reset();
        ScanBackend.Callback callback = new ScanBackend.Callback() {
            @Override public void onAdvertisement(Observation observation, boolean hasService) {
                synchronized (DetectorScanner.this) {
                    if (active != this || !hasService || observation == null) return;
                    if (observation.name() != null && observation.name().contains("RTKDeus")) return;
                    listener.onDevice(observation);
                    if (active != this || !observation.address().equals(selectedAddress)) return;
                    listener.onObservation(observation);
                    if (active != this || !observation.address().equals(selectedAddress)) return;
                    ParseResult result = AdvertisementParser.parse(observation);
                    if (result.failure() != null) listener.onParseFailure(observation, result.failure());
                    else if (deduplicator.accept(result.packet())) listener.onDetection(result.packet());
                }
            }
            @Override public void onPermissionDenied() {
                synchronized (DetectorScanner.this) {
                    if (active == this) fail(Error.MISSING_PERMISSION, 0);
                }
            }
            @Override public void onFailure(int platformCode) {
                synchronized (DetectorScanner.this) {
                    if (active == this) fail(Error.SCAN_FAILED, platformCode);
                }
            }
        };
        active = callback;
        setState(State.SCANNING);
        if (active != callback) return;
        try { backend.start(callback); }
        catch (SecurityException exception) { if (active == callback) fail(Error.MISSING_PERMISSION, 0); }
        catch (IllegalStateException | IllegalArgumentException exception) {
            if (active == callback) fail(Error.SCAN_FAILED, 0);
        }
    }
    public synchronized void stop() {
        ScanBackend.Callback previous = active;
        active = null;
        deduplicator.reset();
        if (previous != null) {
            try { backend.stop(previous); }
            catch (SecurityException exception) { fail(Error.MISSING_PERMISSION, 0); return; }
            catch (IllegalStateException exception) { fail(Error.SCAN_FAILED, 0); return; }
        }
        setState(State.STOPPED);
    }
    private void fail(Error error, int code) {
        ScanBackend.Callback previous = active;
        active = null;
        if (previous != null) {
            try { backend.stop(previous); }
            catch (SecurityException | IllegalStateException ignored) {
                // The primary error is retained; callbacks from this session are already invalid.
            }
        }
        setState(State.ERROR);
        listener.onError(error, code);
    }
    private void setState(State newState) {
        if (state == newState) return;
        state = newState;
        listener.onState(newState);
    }
}
