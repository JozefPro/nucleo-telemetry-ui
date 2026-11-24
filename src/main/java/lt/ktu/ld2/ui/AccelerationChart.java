package lt.ktu.ld2.ui;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import lt.ktu.ld2.protocol.Measurement;

final class AccelerationChart extends LineChart<Number, Number> {
    private static final double WINDOW_SECONDS = 30;

    private final NumberAxis timeAxis;
    private final NumberAxis accelerationAxis;
    private final XYChart.Series<Number, Number> ax = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> ay = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> az = new XYChart.Series<>();

    AccelerationChart() {
        this(new NumberAxis(0, WINDOW_SECONDS, 5), new NumberAxis(-1, 1, 0.5));
    }

    private AccelerationChart(NumberAxis timeAxis, NumberAxis accelerationAxis) {
        super(timeAxis, accelerationAxis);
        this.timeAxis = timeAxis;
        this.accelerationAxis = accelerationAxis;
        timeAxis.setLabel("Laikas nuo nuskaitymo pradžios (s)");
        timeAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(timeAxis) {
            @Override
            public String toString(Number value) {
                double seconds = value.doubleValue();
                if (timeAxis.getLowerBound() > 0
                        && (seconds == timeAxis.getLowerBound() || seconds == timeAxis.getUpperBound())) {
                    return "";
                }
                return super.toString(value);
            }
        });
        accelerationAxis.setLabel("Pagreitis (g)");
        timeAxis.setAnimated(false);
        accelerationAxis.setAnimated(false);
        setAnimated(false);
        setAxisSortingPolicy(SortingPolicy.NONE);
        setAlternativeRowFillVisible(false);
        ax.setName("AX");
        ay.setName("AY");
        az.setName("AZ");
        getData().add(ax);
        getData().add(ay);
        getData().add(az);
    }

    void addMeasurement(double seconds, Measurement measurement) {
        double lower = Math.max(0, seconds - WINDOW_SECONDS);
        timeAxis.setLowerBound(lower);
        timeAxis.setUpperBound(lower + WINDOW_SECONDS);
        addPoint(ax, seconds, measurement.ax(), lower);
        addPoint(ay, seconds, measurement.ay(), lower);
        addPoint(az, seconds, measurement.az(), lower);
        accelerationAxis.setAutoRanging(true);
    }

    private void addPoint(XYChart.Series<Number, Number> series, double seconds, double value, double lower) {
        var points = series.getData();
        int expired = 0;
        while (expired < points.size() && points.get(expired).getXValue().doubleValue() < lower) {
            expired++;
        }
        points.remove(0, expired);
        points.add(new XYChart.Data<>(seconds, value));
    }

    void reset() {
        getData().forEach(series -> series.getData().clear());
        timeAxis.setLowerBound(0);
        timeAxis.setUpperBound(WINDOW_SECONDS);
        accelerationAxis.setAutoRanging(false);
        accelerationAxis.setLowerBound(-1);
        accelerationAxis.setUpperBound(1);
        accelerationAxis.setTickUnit(0.5);
    }
}
