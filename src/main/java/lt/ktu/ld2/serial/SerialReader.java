package lt.ktu.ld2.serial;

import com.fazecast.jSerialComm.SerialPort;
import lt.ktu.ld2.protocol.PacketStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class SerialReader implements AutoCloseable {
    private static final int BAUD_RATE = 921_600;
    private static final int READ_TIMEOUT_MS = 100;

    public record Port(String device, String description) {
        @Override
        public String toString() {
            return device;
        }
    }

    public interface Listener {
        void connected();
        void received(PacketStream.ReceivedLine line);
        void closed(String error);
    }

    private volatile boolean stopRequested;

    public static List<Port> listPorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(port -> {
                    String path = port.getSystemPortPath();
                    String device = path.startsWith("/dev/") ? path : port.getSystemPortName();
                    return new Port(device, port.getDescriptivePortName());
                })
                .sorted(Comparator.comparing(Port::device))
                .toList();
    }

    public SerialReader(Port device, Listener listener) {
        Thread worker = new Thread(() -> read(device, listener), "ld2-uart");
        worker.start();
    }

    @Override
    public void close() {
        stopRequested = true;
    }

    private void read(Port device, Listener listener) {
        SerialPort port = null;
        String error = null;
        try {
            port = SerialPort.getCommPort(device.device());
            if (!port.setComPortParameters(BAUD_RATE, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
                    || !port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
                    || !port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MS, 0)) {
                throw new IOException("Nepavyko nustatyti UART parametrų.");
            }
            if (!port.openPort()) {
                throw new IOException("Nepavyko atidaryti prievado. Patikrinkite, ar jis prijungtas ir laisvas.");
            }

            PacketStream stream = new PacketStream(listener::received);
            byte[] buffer = new byte[1024];
            listener.connected();
            while (!stopRequested) {
                int length = port.readBytes(buffer, buffer.length);
                if (length < 0) {
                    throw new IOException("Nepavyko nuskaityti prievado. Patikrinkite USB jungtį.");
                }
                if (length > 0 && !stopRequested) {
                    stream.accept(buffer, length);
                }
            }
        } catch (IOException | RuntimeException | LinkageError exception) {
            error = exception.getMessage();
            if (error == null) {
                error = exception.getClass().getSimpleName();
            }
        } finally {
            if (port != null && port.isOpen() && !port.closePort() && error == null) {
                error = "Nepavyko uždaryti prievado.";
            }
            listener.closed(error);
        }
    }
}
