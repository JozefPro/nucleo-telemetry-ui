package lt.ktu.ld2.protocol;

import java.util.Optional;
import java.util.regex.Pattern;

public final class PacketDecoder {
    private static final String SEPARATOR = "\\h*;\\h*";
    private static final String ACCELERATION = "[+-]?[0-9]+(?:\\.[0-9]+)?";
    private static final Pattern PACKET = Pattern.compile(
            "JB" + SEPARATOR
            + "(?:GPS=WAIT|EET=(?<eet>(?:[01][0-9]|2[0-3]):[0-5][0-9]:(?:[0-5][0-9]|60))"
            + SEPARATOR + "LAT=(?<lat>[0-9]{1,4}(?:\\.[0-9]+)?)\\h+(?<ns>[NS])"
            + SEPARATOR + "LON=(?<lon>[0-9]{1,5}(?:\\.[0-9]+)?)\\h+(?<ew>[EW])"
            + SEPARATOR + "SAT=(?<sat>[0-9]+))"
            + SEPARATOR + "AX=(?<ax>" + ACCELERATION + ")\\h+g"
            + SEPARATOR + "AY=(?<ay>" + ACCELERATION + ")\\h+g"
            + SEPARATOR + "AZ=(?<az>" + ACCELERATION + ")\\h+g");

    private PacketDecoder() {}

    public static Measurement decode(String line) {
        String packet = line.strip();
        if (packet.equals("JB; ACC=READ_ERROR")) {
            throw new IllegalArgumentException("LD1 pranešė pagreičio jutiklio nuskaitymo klaidą.");
        }

        var fields = PACKET.matcher(packet);
        if (!fields.matches()) {
            throw new IllegalArgumentException("Eilutė neatitinka LD1 paketo formato.");
        }

        Optional<Measurement.GpsData> gps = Optional.empty();
        if (fields.group("eet") != null) {
            gps = Optional.of(new Measurement.GpsData(
                    fields.group("eet"),
                    coordinate(fields.group("lat"), fields.group("ns"), 90),
                    coordinate(fields.group("lon"), fields.group("ew"), 180),
                    satelliteCount(fields.group("sat"))));
        }
        return new Measurement(gps,
                acceleration(fields.group("ax")),
                acceleration(fields.group("ay")),
                acceleration(fields.group("az")));
    }

    private static double coordinate(String value, String direction, int limit) {
        double packed = Double.parseDouble(value);
        int degrees = (int) (packed / 100);
        double minutes = packed - degrees * 100;
        if (minutes >= 60 || degrees > limit || (degrees == limit && minutes > 0)) {
            throw new IllegalArgumentException("GPS koordinatė už leistinų ribų.");
        }
        double decimal = degrees + minutes / 60;
        return direction.equals("S") || direction.equals("W") ? -decimal : decimal;
    }

    private static int satelliteCount(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Netinkamas palydovų skaičius.", exception);
        }
    }

    private static double acceleration(String value) {
        double acceleration = Double.parseDouble(value);
        if (!Double.isFinite(acceleration)) {
            throw new IllegalArgumentException("Netinkama pagreičio reikšmė.");
        }
        return acceleration;
    }
}
