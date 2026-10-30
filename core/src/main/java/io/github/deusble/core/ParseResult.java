package io.github.deusble.core;

/** Exactly one of packet() and failure() is non-null. */
public final class ParseResult {
    public enum Failure { MISSING_DATA, TOO_SHORT, BAD_SIGNATURE, INVALID_TARGET }
    private final Packet packet;
    private final Failure failure;
    private ParseResult(Packet packet, Failure failure) { this.packet = packet; this.failure = failure; }
    static ParseResult success(Packet packet) { return new ParseResult(packet, null); }
    static ParseResult failure(Failure failure) { return new ParseResult(null, failure); }
    public Failure failure() { return failure; }
    public Packet packet() { return packet; }
}
