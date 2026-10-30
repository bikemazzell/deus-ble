package io.github.deusble.core;

/** Parsed fields from a candidate advertisement. This does not prove device identity. */
public final class Packet {
    public enum Mode { PUSH, AUTO }
    private final Observation observation;
    private final int targetId;
    private final int soil;
    private final int frame;

    Packet(Observation observation, int targetId, int soil, int frame) {
        this.observation = observation;
        this.targetId = targetId;
        this.soil = soil;
        this.frame = frame;
    }
    public int targetId() { return targetId; }
    public int soil() { return soil; }
    public int frame() { return frame; }
    public Mode mode() { return frame < 128 ? Mode.PUSH : Mode.AUTO; }
    public Observation observation() { return observation; }
}
