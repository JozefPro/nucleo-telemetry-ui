package lt.ktu.ld2.ui;

import lt.ktu.ld2.protocol.Measurement;

final class TiltFilter {
    record Angles(double roll, double pitch) {}

    private static final double WEIGHT = 0.46;
    private static final double DEADBAND_DEGREES = 0.4;
    private Angles angles;

    Angles update(Measurement measurement) {
        double yz = Math.hypot(measurement.ay(), measurement.az());
        double magnitude = Math.hypot(measurement.ax(), yz);
        if (magnitude < 0.15 || !Double.isFinite(magnitude)) {
            return angles != null ? angles : new Angles(0, 0);
        }

        double roll = Math.toDegrees(Math.atan2(measurement.ax(), yz));
        // When X is nearly vertical, Y and Z provide no reliable pitch.
        double pitch = yz < 0.08 * magnitude
                ? (angles != null ? angles.pitch() : 0)
                : Math.toDegrees(Math.atan2(measurement.ay(), measurement.az()));
        angles = angles == null ? new Angles(roll, pitch) : new Angles(
                smooth(angles.roll(), roll), smooth(angles.pitch(), pitch));
        return angles;
    }

    void reset() {
        angles = null;
    }

    private static double smooth(double current, double target) {
        double delta = Math.IEEEremainder(target - current, 360);
        return Math.abs(delta) < DEADBAND_DEGREES ? current : current + WEIGHT * delta;
    }
}
