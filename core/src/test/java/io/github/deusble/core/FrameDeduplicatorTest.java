package io.github.deusble.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameDeduplicatorTest {
    private Packet packet(String address, int frame, long time) {
        return AdvertisementParser.parse(new Observation(address, null, -50, 0, time,
            AdvertisementParserTest.frame(20, 0, frame))).packet();
    }
    @Test public void repeatsChangesWrapAndInterleaving() {
        FrameDeduplicator d = new FrameDeduplicator(2, 100);
        assertTrue(d.accept(packet("A", 255, 1)));
        assertTrue(d.accept(packet("B", 255, 2)));
        assertFalse(d.accept(packet("a", 255, 3)));
        assertTrue(d.accept(packet("A", 0, 4)));
        assertFalse(d.accept(packet("B", 255, 5)));
        assertTrue(d.accept(packet("A", 255, 6)));
    }
    @Test public void expiryMeasuresLastObservationAndAllowsBoundary() {
        FrameDeduplicator d = new FrameDeduplicator(2, 100);
        assertTrue(d.accept(packet("A", 0, 1)));
        assertFalse(d.accept(packet("A", 0, 99)));
        assertFalse(d.accept(packet("A", 0, 150)));
        assertTrue(d.accept(packet("A", 0, 250)));
    }
    @Test public void backwardTimestampStartsNewEpochAndResetClearsHistory() {
        FrameDeduplicator d = new FrameDeduplicator(2, 100);
        assertTrue(d.accept(packet("A", 0, 200)));
        assertTrue(d.accept(packet("A", 0, 100)));
        assertFalse(d.accept(packet("A", 0, 101)));
        d.reset(); assertTrue(d.accept(packet("A", 0, 102)));
    }
    @Test public void evictsLeastRecentlySeenDevice() {
        FrameDeduplicator d = new FrameDeduplicator(2, 100);
        d.accept(packet("A", 1, 1)); d.accept(packet("B", 1, 2)); d.accept(packet("A", 1, 3));
        d.accept(packet("C", 1, 4));
        assertFalse(d.accept(packet("A", 1, 5))); assertTrue(d.accept(packet("B", 1, 6)));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsZeroCapacity() { new FrameDeduplicator(0, 100); }
    @Test(expected = IllegalArgumentException.class) public void rejectsZeroExpiry() { new FrameDeduplicator(2, 0); }
}
