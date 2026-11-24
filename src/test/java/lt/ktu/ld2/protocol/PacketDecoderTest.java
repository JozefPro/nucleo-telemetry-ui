package lt.ktu.ld2.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PacketDecoderTest {
    static final String SAMPLE =
            "JB; EET=22:37:29; LAT=5453.9492 N; LON=2354.5332 E; SAT=11; AX=0.020 g; AY=-0.008 g; AZ=0.982 g";

    @Test
    void decodesTheLd1Sample() {
        Measurement measurement = PacketDecoder.decode(SAMPLE);
        var gps = measurement.gps().orElseThrow();

        assertEquals("22:37:29", gps.eet());
        assertEquals(54.899153333, gps.latitude(), 0.00000001);
        assertEquals(23.908886667, gps.longitude(), 0.00000001);
        assertEquals(11, gps.satellites());
        assertEquals(0.020, measurement.ax());
        assertEquals(-0.008, measurement.ay());
        assertEquals(0.982, measurement.az());
    }

    @Test
    void convertsSouthAndWestAndAcceptsCoordinatesWithoutLeadingZeros() {
        var gps = PacketDecoder.decode(SAMPLE
                .replace("5453.9492 N", "53.9492 S")
                .replace("2354.5332 E", "354.5332 W")
                .replace("SAT=11", "SAT=0")).gps().orElseThrow();

        assertEquals(-0.899153333, gps.latitude(), 0.00000001);
        assertEquals(-3.908886667, gps.longitude(), 0.00000001);
        assertEquals(0, gps.satellites());
    }

    @Test
    void decodesAccelerationWhileLd1WaitsForGps() {
        Measurement waiting = PacketDecoder.decode("JB; GPS=WAIT; AX=0.020 g; AY=-0.008 g; AZ=0.982 g");

        assertTrue(waiting.gps().isEmpty());
        assertEquals(-0.008, waiting.ay());
    }

    @Test
    void rejectsImpossibleCoordinatesAndTruncatedPackets() {
        assertThrows(IllegalArgumentException.class,
                () -> PacketDecoder.decode(SAMPLE.replace("5453.9492", "5460.0000")));
        assertThrows(IllegalArgumentException.class,
                () -> PacketDecoder.decode(SAMPLE.replace("2354.5332", "18000.0001")));
        assertThrows(IllegalArgumentException.class,
                () -> PacketDecoder.decode(SAMPLE.substring(0, SAMPLE.indexOf("; AZ"))));
    }

    @Test
    void reportsTheLd1SensorError() {
        var exception = assertThrows(IllegalArgumentException.class, () -> PacketDecoder.decode("JB; ACC=READ_ERROR"));
        assertTrue(exception.getMessage().contains("jutiklio"));
    }
}
