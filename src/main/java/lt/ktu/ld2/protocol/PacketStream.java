package lt.ktu.ld2.protocol;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class PacketStream {
    public record ReceivedLine(String raw, double elapsedSeconds, Measurement measurement, String error) {}

    private final LineFramer framer = new LineFramer();
    private final Consumer<ReceivedLine> listener;
    private final LongSupplier nanoTime;
    private final long startedAt;

    public PacketStream(Consumer<ReceivedLine> listener) {
        this(listener, System::nanoTime);
    }

    PacketStream(Consumer<ReceivedLine> listener, LongSupplier nanoTime) {
        this.listener = listener;
        this.nanoTime = nanoTime;
        startedAt = nanoTime.getAsLong();
    }

    public void accept(byte[] bytes, int length) {
        framer.accept(bytes, length, this::decode, raw -> listener.accept(
                new ReceivedLine(raw + "…", elapsedSeconds(), null, "Per ilga eilutė.")));
    }

    private void decode(String raw) {
        if (raw.isBlank()) {
            return;
        }
        double elapsed = elapsedSeconds();
        Measurement measurement = null;
        String error = null;
        try {
            measurement = PacketDecoder.decode(raw);
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
        }
        listener.accept(new ReceivedLine(raw, elapsed, measurement, error));
    }

    private double elapsedSeconds() {
        return (nanoTime.getAsLong() - startedAt) / 1_000_000_000.0;
    }
}
