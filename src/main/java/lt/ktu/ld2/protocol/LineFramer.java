package lt.ktu.ld2.protocol;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class LineFramer {
    static final int MAX_LINE_BYTES = 1024;

    private final byte[] line = new byte[MAX_LINE_BYTES];
    private int size;
    private boolean truncated;

    void accept(byte[] bytes, int length, Consumer<String> onLine, Consumer<String> onOverflow) {
        for (int index = 0; index < length; index++) {
            byte next = bytes[index];
            if (next == '\n') {
                if (truncated) {
                    onOverflow.accept(new String(line, StandardCharsets.US_ASCII));
                } else {
                    int end = size > 0 && line[size - 1] == '\r' ? size - 1 : size;
                    onLine.accept(new String(line, 0, end, StandardCharsets.US_ASCII));
                }
                size = 0;
                truncated = false;
            } else if (size < line.length) {
                line[size++] = next;
            } else {
                truncated = true;
            }
        }
    }
}
