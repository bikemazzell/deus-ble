package io.github.deusble.core;

import java.util.Objects;

/** Parses the complete advertisement, not just a manufacturer-data field. */
public final class AdvertisementParser {
    private AdvertisementParser() {}
    public static ParseResult parse(Observation observation) {
        Objects.requireNonNull(observation, "observation");
        byte[] bytes = observation.rawBytes();
        if (bytes == null) return ParseResult.failure(ParseResult.Failure.MISSING_DATA);
        if (bytes.length < 21) return ParseResult.failure(ParseResult.Failure.TOO_SHORT);
        if (bytes[0] != 2 || bytes[1] != 1 || bytes[2] != 5)
            return ParseResult.failure(ParseResult.Failure.BAD_SIGNATURE);
        int target = bytes[17] & 0xff;
        if (target >= 100) return ParseResult.failure(ParseResult.Failure.INVALID_TARGET);
        return ParseResult.success(new Packet(observation, target, 90 - (bytes[19] & 0xff) / 4, bytes[20] & 0xff));
    }
}
