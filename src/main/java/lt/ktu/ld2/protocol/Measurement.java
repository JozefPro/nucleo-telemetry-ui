package lt.ktu.ld2.protocol;

import java.util.Optional;

public record Measurement(Optional<GpsData> gps, double ax, double ay, double az) {
    public record GpsData(String eet, double latitude, double longitude, int satellites) {}
}
