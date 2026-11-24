package lt.ktu.ld2.ui;

import javafx.geometry.Point3D;
import javafx.scene.transform.Rotate;
import lt.ktu.ld2.protocol.Measurement;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiltFilterTest {
    @Test
    void mapsAxToModelDepthAndAzToModelX() {
        var ax = new Measurement(Optional.empty(), 1, 0, 0);
        var az = new Measurement(Optional.empty(), 0, 0, 1);

        assertEquals(Quaternion.IDENTITY, new TiltFilter().update(ax));
        assertPoint(TiltFilter.UP, new TiltFilter().update(az).rotate(Rotate.X_AXIS));
    }

    @Test
    void alignsGravityForBothSignsOfBothHorizontalAxes() {
        for (Point3D axis : new Point3D[]{Rotate.X_AXIS, Rotate.Y_AXIS}) {
            for (double angle : new double[]{-30, 30}) {
                var gravity = Quaternion.axisAngle(axis, Math.toRadians(angle)).rotate(TiltFilter.UP);
                assertPoint(TiltFilter.UP, new TiltFilter().update(sample(gravity)).rotate(gravity));
            }
        }
    }

    @Test
    void followsAnInversionAndHoldsItThroughNoiseAndFreeFall() {
        TiltFilter filter = new TiltFilter();
        for (int degrees = 0; degrees <= 180; degrees += 5) {
            var gravity = Quaternion.axisAngle(Rotate.X_AXIS, Math.toRadians(degrees)).rotate(TiltFilter.UP);
            filter.update(sample(gravity));
        }
        Point3D inverted = TiltFilter.UP.multiply(-1);
        Quaternion pose = Quaternion.IDENTITY;
        for (int i = 0; i < 16; i++) {
            pose = filter.update(sample(inverted));
        }
        assertTrue(pose.rotate(inverted).distance(TiltFilter.UP) < 0.006);
        assertPoint(Rotate.X_AXIS, pose.rotate(Rotate.X_AXIS));
        assertEquals(pose, filter.update(sample(new Point3D(0.001, 0, 1))));
        assertEquals(pose, filter.update(sample(Point3D.ZERO)));
    }

    @Test
    void slerpKeepsHalfOfAFullFlipAndArcballCanTwistInTheScreenPlane() {
        var flip = Quaternion.axisAngle(Rotate.X_AXIS, Math.PI);
        assertPoint(Rotate.Y_AXIS, Quaternion.IDENTITY.interpolate(flip, 0.5).rotate(TiltFilter.UP));

        var start = SensorPosition.arcball(1, 0, 1);
        var end = SensorPosition.arcball(0, 1, 1);
        var twist = Quaternion.between(start, end);
        assertPoint(Rotate.Y_AXIS, twist.rotate(Rotate.X_AXIS));
        assertPoint(TiltFilter.UP, twist.rotate(TiltFilter.UP));
    }

    private static Measurement sample(Point3D gravity) {
        return new Measurement(Optional.empty(), -gravity.getZ(), -gravity.getY(), gravity.getX());
    }

    private static void assertPoint(Point3D expected, Point3D actual) {
        assertEquals(0, expected.distance(actual), 1e-8);
    }
}
