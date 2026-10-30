package io.github.deusble.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Suppresses consecutive equal frame bytes per address, with bounded least-recently-seen state. */
public final class FrameDeduplicator {
    private final int maxDevices;
    private final long expiryNanos;
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private static final class Entry {
        final int frame;
        final long time;
        Entry(int frame, long time) { this.frame = frame; this.time = time; }
    }
    public FrameDeduplicator(int maxDevices, long expiryNanos) {
        if (maxDevices < 1 || expiryNanos < 1) throw new IllegalArgumentException("Capacity and expiry must be positive");
        this.maxDevices = maxDevices;
        this.expiryNanos = expiryNanos;
    }
    /** Expiry uses last observation time. A backward timestamp starts a new epoch for that device. */
    public synchronized boolean accept(Packet packet) {
        Objects.requireNonNull(packet, "packet");
        String address = packet.observation().address();
        long time = packet.observation().monotonicNanos();
        Entry previous = entries.put(address, new Entry(packet.frame(), time));
        if (entries.size() > maxDevices) entries.remove(entries.keySet().iterator().next());
        return previous == null || time < previous.time || time - previous.time < 0
            || time - previous.time >= expiryNanos || packet.frame() != previous.frame;
    }
    public synchronized void reset() { entries.clear(); }
}
