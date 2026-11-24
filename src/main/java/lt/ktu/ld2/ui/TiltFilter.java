package lt.ktu.ld2.ui;

import javafx.geometry.Point3D;
import lt.ktu.ld2.protocol.Measurement;

final class TiltFilter {
    static final Point3D UP = new Point3D(0, 0, -1);
    private static final double DEADBAND = Math.cos(Math.toRadians(0.3));
    private Point3D smoothed;
    private Point3D lastDirection = UP;
    private Quaternion tilt = Quaternion.IDENTITY;

    Quaternion update(Measurement measurement) {
        Point3D direction = new Point3D(measurement.az(), -measurement.ay(), -measurement.ax());
        double magnitude = direction.magnitude();
        if (!Double.isFinite(magnitude) || magnitude < 0.15) {
            return tilt;
        }
        direction = direction.multiply(1 / magnitude);
        smoothed = smoothed == null ? direction
                : smoothed.multiply(0.42).add(direction.multiply(0.58)).normalize();
        if (smoothed.dotProduct(lastDirection) >= DEADBAND) {
            return tilt;
        }

        // Carry the last fold axis through the inverted pose, where the
        // shortest rotation from upright otherwise depends on tiny axis noise.
        tilt = smoothed.dotProduct(UP) < -0.96
                ? tilt.multiply(Quaternion.between(smoothed, lastDirection)).normalized()
                : Quaternion.between(smoothed, UP);
        lastDirection = smoothed;
        return tilt;
    }

    void reset() {
        smoothed = null;
        lastDirection = UP;
        tilt = Quaternion.IDENTITY;
    }
}
