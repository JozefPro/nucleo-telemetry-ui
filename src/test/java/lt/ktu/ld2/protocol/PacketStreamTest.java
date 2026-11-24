package lt.ktu.ld2.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static lt.ktu.ld2.protocol.PacketDecoderTest.SAMPLE;
import static org.junit.jupiter.api.Assertions.*;

class PacketStreamTest {
    private final List<PacketStream.ReceivedLine> received = new ArrayList<>();
    private long now = 12_000_000_000L;
    private final PacketStream stream = new PacketStream(received::add, () -> now);

    @Test
    void joinsPartialReadsIncludingASplitCrlfAndPreservesRawText() {
        send(SAMPLE.substring(0, 24));
        send(SAMPLE.substring(24) + " \r");
        assertTrue(received.isEmpty());
        now += 333_300_000L;
        send("\n");

        assertEquals(1, received.size());
        assertEquals(SAMPLE + " ", received.getFirst().raw());
        assertEquals(-0.008, received.getFirst().measurement().ay());
        assertEquals(0.3333, received.getFirst().elapsedSeconds(), 1e-12);
    }

    @Test
    void deliversEveryPacketWithTheSameEetSecondIncludingPacketsFromOneRead() {
        String second = SAMPLE.replace("AY=-0.008", "AY=-0.400");
        now += 300_000_000L;
        send(SAMPLE + "\n" + second + "\r\n");
        now += 333_300_000L;
        send(SAMPLE + "\n");

        assertEquals(3, received.size());
        assertTrue(received.stream().allMatch(line -> line.measurement().gps().orElseThrow().eet().equals("22:37:29")));
        assertEquals(-0.400, received.get(1).measurement().ay());
        assertEquals(0.3, received.getFirst().elapsedSeconds(), 1e-12);
        assertEquals(0.6333, received.getLast().elapsedSeconds(), 1e-12);
    }

    @Test
    void skipsBlankLinesAndContinuesAfterInvalidInput() {
        send("\r\n \t\n sugadinta eilute \r\n" + SAMPLE + "\n");

        assertEquals(2, received.size());
        assertEquals(" sugadinta eilute ", received.getFirst().raw());
        assertNotNull(received.getFirst().error());
        assertNull(received.getFirst().measurement());
        assertNotNull(received.getLast().measurement());
    }

    @Test
    void continuesAfterAnOversizedLine() {
        send("X".repeat(LineFramer.MAX_LINE_BYTES + 20));
        send("\n" + SAMPLE + "\r\n");

        assertEquals(2, received.size());
        assertEquals("Per ilga eilutė.", received.getFirst().error());
        assertNotNull(received.getLast().measurement());
    }

    private void send(String chunk) {
        byte[] bytes = chunk.getBytes(StandardCharsets.US_ASCII);
        stream.accept(bytes, bytes.length);
    }
}
