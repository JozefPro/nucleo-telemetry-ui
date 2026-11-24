package lt.ktu.ld2.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lt.ktu.ld2.protocol.PacketStream;
import lt.ktu.ld2.serial.SerialReader;

import java.util.Locale;
import java.util.prefs.Preferences;

public final class Ld2Application extends Application {
    private static final String NO_VALUE = "—";
    private static final int TERMINAL_LIMIT = 48_000;
    private static final double STACKED_BELOW = 840;
    private static final String DARK_THEME = "darkTheme";
    private static final PseudoClass CONNECTED = PseudoClass.getPseudoClass("connected");
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    private enum State { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

    private final ComboBox<SerialReader.Port> ports = new ComboBox<>();
    private final Button refresh = new Button("Atnaujinti");
    private final Button connect = new Button("Prisijungti");
    private final ToggleButton theme = new ToggleButton("Tamsi tema");
    private final Label status = new Label();
    private final TextField latitude = readOnlyField();
    private final TextField longitude = readOnlyField();
    private final TextField eet = readOnlyField();
    private final TextField satellites = readOnlyField();
    private final AccelerationChart chart = new AccelerationChart();
    private final SensorPosition sensor = new SensorPosition();
    private final TextArea terminal = new TextArea();
    private final Preferences preferences = Preferences.userNodeForPackage(Ld2Application.class);

    private SerialReader reader;
    private GpsMap gpsMap;
    private State state = State.DISCONNECTED;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        gpsMap = new GpsMap(getHostServices()::showDocument);
        ScrollPane scroll = new ScrollPane();
        scroll.getStyleClass().add("dashboard-scroll");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        GridPane visuals = visualsPanel(scroll);
        VBox workspace = new VBox(14, connectionPanel(), gpsPanel(), visuals, gpsMap, terminalPanel());
        workspace.getStyleClass().add("workspace");
        VBox.setVgrow(visuals, Priority.ALWAYS);
        scroll.setContent(workspace);

        var screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(scroll, Math.min(1120, screen.getWidth()),
                Math.min(925, screen.getHeight() - 45));
        scene.getStylesheets().add(getClass().getResource("ld2.css").toExternalForm());
        stage.setTitle("Kompiuterinės komunikacijos · LD2");
        stage.setMinWidth(660);
        stage.setMinHeight(680);
        stage.setScene(scene);

        theme.setSelected(preferences.getBoolean(DARK_THEME, false));
        applyTheme(scroll, theme.isSelected());
        theme.selectedProperty().addListener((observable, previous, dark) -> {
            applyTheme(scroll, dark);
            preferences.putBoolean(DARK_THEME, dark);
        });
        setState(State.DISCONNECTED);
        refreshPorts();
        stage.show();
    }

    @Override
    public void stop() {
        if (reader != null) {
            reader.close();
        }
        if (gpsMap != null) {
            gpsMap.close();
        }
    }

    private void applyTheme(ScrollPane root, boolean dark) {
        root.pseudoClassStateChanged(DARK, dark);
        sensor.setDark(dark);
        gpsMap.setDark(dark);
    }

    private VBox connectionPanel() {
        status.getStyleClass().add("status");
        Label portLabel = new Label("Nuoseklusis prievadas");
        portLabel.getStyleClass().add("section-title");
        portLabel.setLabelFor(ports);
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        theme.getStyleClass().add("theme-toggle");
        HBox heading = new HBox(8, portLabel, space, theme, status);
        heading.setAlignment(Pos.CENTER_LEFT);

        ports.setPromptText("Pasirinkite prievadą");
        ports.setMinWidth(180);
        ports.setPrefWidth(220);
        ports.setMaxWidth(Double.MAX_VALUE);
        ports.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(SerialReader.Port port, boolean empty) {
                super.updateItem(port, empty);
                setText(empty || port == null ? null : port.device() + "  ·  " + port.description());
            }
        });
        ports.setOnShowing(event -> refreshPorts());
        ports.valueProperty().addListener((observable, previous, selected) -> updateControls());
        refresh.setMinWidth(Region.USE_PREF_SIZE);
        refresh.setOnAction(event -> refreshPorts());
        connect.getStyleClass().add("primary");
        connect.setOnAction(event -> toggleConnection());
        Label settings = new Label("921 600 baud · 8N1");
        settings.getStyleClass().add("secondary-text");
        settings.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(ports, Priority.ALWAYS);

        HBox controls = new HBox(8, ports, refresh, connect, settings);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox panel = new VBox(9, heading, controls);
        panel.getStyleClass().addAll("panel", "toolbar");
        return panel;
    }

    private GridPane gpsPanel() {
        GridPane panel = new GridPane();
        panel.setHgap(12);
        addGpsField(panel, 0, "Platuma", latitude);
        addGpsField(panel, 1, "Ilguma", longitude);
        addGpsField(panel, 2, "EET laikas", eet);
        addGpsField(panel, 3, "Palydovų skaičius", satellites);
        return panel;
    }

    private void addGpsField(GridPane panel, int column, String name, TextField field) {
        ColumnConstraints width = new ColumnConstraints();
        width.setPercentWidth(25);
        panel.getColumnConstraints().add(width);
        Label label = new Label(name);
        label.setLabelFor(field);
        label.getStyleClass().add("field-label");
        VBox card = new VBox(5, label, field);
        card.getStyleClass().addAll("panel", "gps-card");
        panel.add(card, column, 0);
    }

    private static TextField readOnlyField() {
        TextField field = new TextField(NO_VALUE);
        field.setEditable(false);
        field.setMinWidth(0);
        field.getStyleClass().add("gps-value");
        return field;
    }

    private GridPane visualsPanel(ScrollPane scroll) {
        VBox plot = plotPanel();
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        ColumnConstraints mainColumn = new ColumnConstraints();
        mainColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints sideColumn = new ColumnConstraints(224, 245, 270);
        grid.getColumnConstraints().setAll(mainColumn, sideColumn);
        grid.add(plot, 0, 0);
        grid.add(sensor, 1, 0);
        GridPane.setVgrow(plot, Priority.ALWAYS);
        scroll.viewportBoundsProperty().addListener((observable, previous, bounds) -> {
            double width = bounds.getWidth();
            boolean stacked = width > 0 && width < STACKED_BELOW;
            if (stacked != Integer.valueOf(1).equals(GridPane.getRowIndex(sensor))) {
                GridPane.setColumnIndex(sensor, stacked ? 0 : 1);
                GridPane.setRowIndex(sensor, stacked ? 1 : 0);
                if (stacked) {
                    grid.getColumnConstraints().setAll(mainColumn);
                } else {
                    grid.getColumnConstraints().setAll(mainColumn, sideColumn);
                }
            }
        });
        return grid;
    }

    private VBox plotPanel() {
        Label title = new Label("LIS2DUXS12 pagreitis");
        title.getStyleClass().add("section-title");
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        Label window = new Label("30 s langas");
        window.getStyleClass().add("secondary-text");
        HBox heading = new HBox(10, title, space, window);
        heading.setAlignment(Pos.CENTER_LEFT);
        chart.setMinSize(0, 210);
        chart.setPrefHeight(268);
        VBox.setVgrow(chart, Priority.ALWAYS);
        VBox panel = new VBox(2, heading, chart);
        panel.getStyleClass().addAll("panel", "plot-panel");
        return panel;
    }

    private VBox terminalPanel() {
        Label title = new Label("Terminalas");
        title.setLabelFor(terminal);
        title.getStyleClass().add("section-title");
        terminal.setEditable(false);
        terminal.setWrapText(true);
        terminal.setPrefHeight(122);
        terminal.setMinHeight(90);
        terminal.getStyleClass().add("terminal");
        VBox panel = new VBox(8, title, terminal);
        panel.getStyleClass().addAll("panel", "terminal-panel");
        return panel;
    }

    private void refreshPorts() {
        SerialReader.Port selected = ports.getValue();
        try {
            var found = SerialReader.listPorts();
            if (!found.equals(ports.getItems())) {
                ports.getItems().setAll(found);
                ports.setValue(found.stream()
                        .filter(port -> selected != null && port.device().equals(selected.device()))
                        .findFirst()
                        .orElse(found.size() == 1 ? found.getFirst() : null));
            }
        } catch (RuntimeException | LinkageError exception) {
            ports.getItems().clear();
            ports.setValue(null);
            setState(State.ERROR);
            log("[Klaida] Nepavyko gauti prievadų sąrašo. " + exception.getMessage());
        }
        updateControls();
    }

    private void toggleConnection() {
        if (state == State.CONNECTED) {
            setState(State.DISCONNECTING);
            reader.close();
            return;
        }
        SerialReader.Port device = ports.getValue();
        if (device == null) {
            return;
        }

        setState(State.CONNECTING);
        log("[Ryšys] Jungiamasi prie " + device + ".");
        reader = new SerialReader(device, new SerialReader.Listener() {
            @Override
            public void connected() {
                Platform.runLater(() -> {
                    chart.reset();
                    sensor.reset();
                    clearGps();
                    setState(State.CONNECTED);
                    log("[Ryšys] Prisijungta prie " + device + ".");
                });
            }

            @Override
            public void received(PacketStream.ReceivedLine line) {
                Platform.runLater(() -> display(line));
            }

            @Override
            public void closed(String error) {
                Platform.runLater(() -> {
                    reader = null;
                    if (error == null) {
                        log("[Ryšys] Atsijungta nuo " + device + ".");
                        setState(State.DISCONNECTED);
                    } else {
                        log("[Klaida] " + device + " · " + error);
                        if (state == State.CONNECTED || state == State.DISCONNECTING) {
                            log("[Ryšys] Atsijungta nuo " + device + ".");
                        }
                        setState(State.ERROR);
                    }
                });
            }
        });
    }

    private void display(PacketStream.ReceivedLine line) {
        log(line.raw());
        if (line.error() != null) {
            log("[Paketas] " + line.error());
            return;
        }

        var measurement = line.measurement();
        chart.addMeasurement(line.elapsedSeconds(), measurement);
        sensor.update(measurement);
        measurement.gps().ifPresentOrElse(gps -> {
            latitude.setText(formatCoordinate(gps.latitude(), "N", "S"));
            longitude.setText(formatCoordinate(gps.longitude(), "E", "W"));
            eet.setText(gps.eet());
            satellites.setText(Integer.toString(gps.satellites()));
            gpsMap.update(gps);
        }, this::clearGps);
    }

    private static String formatCoordinate(double coordinate, String positive, String negative) {
        String direction = Math.copySign(1, coordinate) < 0 ? negative : positive;
        return String.format(Locale.ROOT, "%.6f° %s", Math.abs(coordinate), direction);
    }

    private void clearGps() {
        latitude.setText(NO_VALUE);
        longitude.setText(NO_VALUE);
        eet.setText(NO_VALUE);
        satellites.setText(NO_VALUE);
    }

    private void log(String message) {
        int overflow = terminal.getLength() + message.length() + 1 - TERMINAL_LIMIT;
        if (overflow > 0) {
            int lineEnd = terminal.getText().indexOf('\n', overflow);
            terminal.deleteText(0, lineEnd >= 0 ? lineEnd + 1 : terminal.getLength());
        }
        terminal.appendText(message + "\n");
    }

    private void setState(State state) {
        this.state = state;
        status.setText(switch (state) {
            case DISCONNECTED -> "Neprisijungta";
            case CONNECTING -> "Prisijungiama…";
            case CONNECTED -> "Prisijungta";
            case DISCONNECTING -> "Atsijungiama…";
            case ERROR -> "Ryšio klaida";
        });
        status.pseudoClassStateChanged(CONNECTED, state == State.CONNECTED);
        status.pseudoClassStateChanged(ERROR, state == State.ERROR);
        updateControls();
    }

    private void updateControls() {
        boolean busy = state == State.CONNECTING || state == State.DISCONNECTING;
        boolean connected = state == State.CONNECTED;
        ports.setDisable(busy || connected);
        refresh.setDisable(busy || connected);
        connect.setText(connected || state == State.DISCONNECTING ? "Atsijungti" : "Prisijungti");
        connect.setDisable(busy || (!connected && ports.getValue() == null));
    }
}
