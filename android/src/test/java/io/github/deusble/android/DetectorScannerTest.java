package io.github.deusble.android;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import io.github.deusble.core.*;

public class DetectorScannerTest {
    static class Backend implements ScanBackend {
        Availability available = Availability.READY;
        Callback callback;
        int starts, stops;
        RuntimeException startError;
        public Availability availability() { return available; }
        public void start(Callback callback) { this.callback = callback; starts++; if (startError != null) throw startError; }
        public void stop(Callback callback) { stops++; }
    }
    static class Events implements DetectorScanner.Listener {
        int devices, observations, detections, failures;
        List<DetectorScanner.State> states = new ArrayList<>();
        DetectorScanner.Error error; int code;
        public void onDevice(Observation o) { devices++; }
        public void onObservation(Observation o) { observations++; }
        public void onDetection(Packet p) { detections++; }
        public void onParseFailure(Observation o, ParseResult.Failure f) { failures++; }
        public void onState(DetectorScanner.State s) { states.add(s); }
        public void onError(DetectorScanner.Error e, int c) { error = e; code = c; }
    }
    static Observation observation(String address, String name, int frame) {
        byte[] bytes = new byte[21]; bytes[0]=2; bytes[1]=1; bytes[2]=5; bytes[17]=42; bytes[20]=(byte)frame;
        return new Observation(address, name, -55, 123, 1000, bytes);
    }
    @Test public void repeatStartStopAndLateCallbacksAreSafe() {
        Backend b = new Backend(); Events e = new Events(); DetectorScanner s = new DetectorScanner(b,e);
        s.start(); s.start(); assertEquals(1,b.starts); assertEquals(DetectorScanner.State.SCANNING,s.state());
        ScanBackend.Callback old = b.callback;
        s.stop(); s.stop(); assertEquals(1,b.stops);
        s.start(); old.onAdvertisement(observation("A",null,1),true); old.onFailure(9);
        assertEquals(0,e.devices); assertEquals(DetectorScanner.State.SCANNING,s.state());
        s.stop(); assertEquals(DetectorScanner.State.STOPPED,s.state());
    }
    @Test public void availabilityErrorsDoNotStartBackend() {
        for (ScanBackend.Availability a : ScanBackend.Availability.values()) {
            if (a == ScanBackend.Availability.READY) continue;
            Backend b = new Backend(); b.available=a; Events e=new Events(); DetectorScanner s=new DetectorScanner(b,e);
            s.start(); assertEquals(0,b.starts); assertEquals(DetectorScanner.State.ERROR,s.state());
            assertEquals(a.name(),e.error.name());
        }
    }
    @Test public void startExceptionAndAsynchronousFailureAreReportedAndRecoverable() {
        Backend b=new Backend(); Events e=new Events(); DetectorScanner s=new DetectorScanner(b,e);
        b.startError=new SecurityException(); s.start(); assertEquals(DetectorScanner.Error.MISSING_PERMISSION,e.error);
        b.startError=null; s.start(); b.callback.onFailure(4);
        assertEquals(DetectorScanner.Error.SCAN_FAILED,e.error); assertEquals(4,e.code);
        assertEquals(DetectorScanner.State.ERROR,s.state()); s.start(); assertEquals(DetectorScanner.State.SCANNING,s.state());
    }
    @Test public void failedStartIsCleanedUp() {
        Backend b=new Backend(); Events e=new Events(); DetectorScanner s=new DetectorScanner(b,e);
        b.startError=new IllegalStateException(); s.start(); assertEquals(DetectorScanner.Error.SCAN_FAILED,e.error);
        assertEquals(1,b.stops); assertEquals(DetectorScanner.State.ERROR,s.state());
    }
    @Test public void filtersDevicesAndNeverAutoSelects() {
        Backend b=new Backend(); Events e=new Events(); DetectorScanner s=new DetectorScanner(b,e); s.start();
        b.callback.onAdvertisement(observation("A","RTKDeus test",1),true);
        b.callback.onAdvertisement(observation("A",null,1),false);
        b.callback.onAdvertisement(observation("A",null,1),true);
        assertEquals(1,e.devices); assertEquals(0,e.observations); assertNull(s.selectedAddress());
        s.selectAddress("a"); assertEquals("A",s.selectedAddress());
        b.callback.onAdvertisement(observation("B",null,1),true); assertEquals(0,e.observations);
        b.callback.onAdvertisement(observation("A",null,1),true);
        b.callback.onAdvertisement(observation("A",null,1),true);
        b.callback.onAdvertisement(observation("A",null,2),true);
        assertEquals(3,e.observations); assertEquals(2,e.detections);
        s.selectAddress("B"); b.callback.onAdvertisement(observation("B",null,2),true); assertEquals(3,e.detections);
        s.selectAddress(null); b.callback.onAdvertisement(observation("B",null,2),true); assertEquals(3,e.detections);
    }
    @Test public void parseFailureStillDeliversRawObservation() {
        Backend b=new Backend(); Events e=new Events(); DetectorScanner s=new DetectorScanner(b,e);
        s.selectAddress("A"); s.start();
        b.callback.onAdvertisement(new Observation("A",null,-1,0,0,null),true);
        assertEquals(1,e.observations); assertEquals(1,e.failures); assertEquals(0,e.detections);
    }
    @Test public void permissionRevokedDuringDeliveryIsExplicit() {
        Backend b=new Backend(); Events e=new Events(); DetectorScanner s=new DetectorScanner(b,e);
        s.start(); b.callback.onPermissionDenied();
        assertEquals(DetectorScanner.Error.MISSING_PERMISSION,e.error);
        assertEquals(DetectorScanner.State.ERROR,s.state()); assertEquals(1,b.stops);
    }
    @Test public void listenerMayStopDuringDiscoveryWithoutLaterDelivery() {
        Backend b=new Backend(); final DetectorScanner[] owner=new DetectorScanner[1];
        Events e=new Events() { @Override public void onDevice(Observation o) { owner[0].stop(); } };
        owner[0]=new DetectorScanner(b,e); owner[0].selectAddress("A"); owner[0].start();
        b.callback.onAdvertisement(observation("A",null,1),true);
        assertEquals(0,e.observations); assertEquals(DetectorScanner.State.STOPPED,owner[0].state());
    }
    @Test public void restartResetsDeduplication() {
        Backend b=new Backend(); Events e=new Events(); DetectorScanner s=new DetectorScanner(b,e);
        s.selectAddress("A"); s.start(); b.callback.onAdvertisement(observation("A",null,1),true);
        s.stop(); s.start(); b.callback.onAdvertisement(observation("A",null,1),true);
        assertEquals(2,e.detections);
    }
}
