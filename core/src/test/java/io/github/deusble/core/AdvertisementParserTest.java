package io.github.deusble.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdvertisementParserTest {
    static byte[] frame(int target, int soil, int frame) {
        byte[] bytes = new byte[24];
        bytes[0] = 2; bytes[1] = 1; bytes[2] = 5;
        bytes[17] = (byte) target; bytes[19] = (byte) soil; bytes[20] = (byte) frame;
        bytes[23] = 42;
        return bytes;
    }
    static ParseResult parse(byte[] bytes) {
        return AdvertisementParser.parse(new Observation("AA", "Detector", -62, 1234, 5678, bytes));
    }
    @Test public void missingAndEveryShortLengthHaveExplicitFailures() {
        assertEquals(ParseResult.Failure.MISSING_DATA, parse(null).failure());
        for (int n = 0; n < 21; n++) assertEquals("length " + n, ParseResult.Failure.TOO_SHORT, parse(new byte[n]).failure());
    }
    @Test public void rejectsSignatureAndUnsignedTarget() {
        byte[] bytes = frame(99, 0, 0); bytes[2] = 6;
        assertEquals(ParseResult.Failure.BAD_SIGNATURE, parse(bytes).failure());
        for (int target = 100; target < 256; target++) assertEquals(ParseResult.Failure.INVALID_TARGET, parse(frame(target, 0, 0)).failure());
    }
    @Test public void parsesUnsignedBoundariesAndIntegerDivision() {
        for (int soil = 0; soil < 256; soil++) {
            Packet packet = parse(frame(99, soil, 127)).packet();
            assertEquals(99, packet.targetId());
            assertEquals(90 - soil / 4, packet.soil());
            assertEquals(127, packet.frame()); assertEquals(Packet.Mode.PUSH, packet.mode());
        }
        assertEquals(27, parse(frame(0, 255, 128)).packet().soil());
        assertEquals(Packet.Mode.AUTO, parse(frame(0, 0, 128)).packet().mode());
        assertEquals(255, parse(frame(0, 0, 255)).packet().frame());
    }
    @Test public void preservesMetadataTrailingBytesAndDefensiveCopies() {
        byte[] bytes = frame(45, 255, 255);
        Observation observation = new Observation("AA", null, -42, 123, 456, bytes);
        bytes[17] = 0;
        Packet packet = AdvertisementParser.parse(observation).packet();
        assertEquals(45, packet.targetId()); assertEquals(24, packet.observation().rawBytes().length);
        byte[] copy = packet.observation().rawBytes(); copy[23] = 0;
        assertEquals(42, packet.observation().rawBytes()[23]);
        assertEquals("AA", packet.observation().address()); assertNull(packet.observation().name());
        assertEquals(-42, packet.observation().rssi()); assertEquals(123, packet.observation().wallTimeMillis());
        assertEquals(456, packet.observation().monotonicNanos());
        assertNull(AdvertisementParser.parse(observation).failure());
    }
}
