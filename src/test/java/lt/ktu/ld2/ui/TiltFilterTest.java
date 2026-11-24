package lt.ktu.ld2.ui;

import lt.ktu.ld2.protocol.Measurement;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiltFilterTest {
    private final TiltFilter filter = new TiltFilter();

    @Test
    void tiltsInBothDirectionsOnEachAxis() {
        assertTilt(0.5, 0, Math.sqrt(0.75), 30, 0);
        assertTilt(-0.5, 0, Math.sqrt(0.75), -30, 0);
        assertTilt(0, 0.5, Math.sqrt(0.75), 0, 30);
        assertTilt(0, -0.5, Math.sqrt(0.75), 0, -30);
    }

    @Test
    void suppressesSmallNoiseAndSmoothsLargerChanges() {
        filter.update(sample(0, 0, 1));
        assertEquals(0, filter.update(sample(0.003, -0.004, 1)).roll());
        assertEquals(0, filter.update(sample(0, 0.003, 1)).pitch());

        double first = filter.update(sample(0.5, 0, Math.sqrt(0.75))).roll();
        double second = filter.update(sample(0.5, 0, Math.sqrt(0.75))).roll();
        assertTrue(first > 0 && first < second && second < 30);
    }

    @Test
    void crossesThePitchWrapByTheShorterPathAndHoldsWhileInFreeFall() {
        filter.update(sample(0, 0.01, -1));
        var inverted = filter.update(sample(0, -0.01, -1));
        assertTrue(inverted.pitch() > 179 && inverted.pitch() < 181);
        assertEquals(inverted, filter.update(sample(0, 0, 0)));
    }

    private void assertTilt(double ax, double ay, double az, double roll, double pitch) {
        filter.reset();
        var angles = filter.update(sample(ax, ay, az));
        assertEquals(roll, angles.roll(), 1e-6);
        assertEquals(pitch, angles.pitch(), 1e-6);
    }

    private static Measurement sample(double ax, double ay, double az) {
        return new Measurement(Optional.empty(), ax, ay, az);
    }
}
