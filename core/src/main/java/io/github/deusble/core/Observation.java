package io.github.deusble.core;

import java.util.Locale;
import java.util.Objects;

/** One advertisement and its receive metadata. Timestamps are supplied by the receiver. */
public final class Observation {
    private final String address;
    private final String name;
    private final int rssi;
    private final long wallTimeMillis;
    private final long monotonicNanos;
    private final byte[] rawBytes;

    public Observation(String address, String name, int rssi, long wallTimeMillis,
                       long monotonicNanos, byte[] rawBytes) {
        this.address = Objects.requireNonNull(address, "address").toUpperCase(Locale.ROOT);
        this.name = name;
        this.rssi = rssi;
        this.wallTimeMillis = wallTimeMillis;
        this.monotonicNanos = monotonicNanos;
        this.rawBytes = rawBytes == null ? null : rawBytes.clone();
    }
    public String address() { return address; }
    public String name() { return name; }
    public int rssi() { return rssi; }
    public long wallTimeMillis() { return wallTimeMillis; }
    public long monotonicNanos() { return monotonicNanos; }
    public byte[] rawBytes() { return rawBytes == null ? null : rawBytes.clone(); }
}
