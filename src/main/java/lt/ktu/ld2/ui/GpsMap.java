package lt.ktu.ld2.ui;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import lt.ktu.ld2.protocol.Measurement;
import netscape.javascript.JSObject;
import okhttp3.Call;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class GpsMap extends VBox implements AutoCloseable {
    private record Position(double latitude, double longitude) {}

    private final Label status = new Label();
    private final StackPane viewport = new StackPane();
    private final TileBridge bridge = new TileBridge();
    private final Map<Integer, Call> requests = new HashMap<>();
    private OsmTiles tiles;
    private WebView view;
    private JSObject page;
    private Position firstPosition;
    private Position latestPosition;
    private Position displayedPosition;
    private boolean dark;

    GpsMap(Consumer<String> openLink) {
        super(8);
        getStyleClass().addAll("panel", "map-panel");
        Label title = new Label("GPS vieta");
        title.getStyleClass().add("section-title");
        viewport.getStyleClass().add("map-viewport");
        viewport.setMinHeight(155);
        viewport.setPrefHeight(220);
        VBox.setVgrow(viewport, Priority.ALWAYS);
        getChildren().addAll(title, viewport);

        status.getStyleClass().add("map-status");
        status.setMouseTransparent(true);
        status.setWrapText(true);
        showStatus("Laukiama GPS duomenų");
        Hyperlink attribution = new Hyperlink("© OpenStreetMap contributors");
        attribution.getStyleClass().add("map-attribution");
        attribution.setOnAction(event -> openLink.accept("https://www.openstreetmap.org/copyright"));
        StackPane.setAlignment(attribution, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(attribution, new Insets(0, 5, 5, 0));
        viewport.getChildren().addAll(status, attribution);
        load();
    }

    private void load() {
        if (!Platform.isSupported(ConditionalFeature.WEB)) {
            unavailable();
            return;
        }
        try {
            String source = getClass().getResource("map/index.html").toExternalForm();
            tiles = new OsmTiles();
            view = new WebView();
            view.setMinSize(0, 0);
            view.setVisible(false);
            view.setContextMenuEnabled(false);
            viewport.getChildren().addFirst(view);

            var engine = view.getEngine();
            engine.getLoadWorker().stateProperty().addListener((observable, previous, state) -> {
                if (state == Worker.State.SUCCEEDED) {
                    try {
                        page = (JSObject) engine.executeScript("window.ld2Map");
                        page.call("initialize", bridge, dark, OsmTiles.MAX_ZOOM);
                        displayLatestPosition();
                    } catch (RuntimeException exception) {
                        unavailable();
                    }
                } else if (state == Worker.State.FAILED) {
                    unavailable();
                }
            });
            engine.load(source);
        } catch (IOException | RuntimeException | LinkageError exception) {
            unavailable();
        }
    }

    void update(Measurement.GpsData gps) {
        if (firstPosition == null) {
            firstPosition = new Position(gps.latitude(), gps.longitude());
        }
        latestPosition = new Position(gps.latitude(), gps.longitude());
        displayLatestPosition();
    }

    void setDark(boolean dark) {
        this.dark = dark;
        invoke("setDark", dark);
    }

    private void displayLatestPosition() {
        if (page == null || latestPosition == null || latestPosition.equals(displayedPosition)) {
            return;
        }
        if (displayedPosition == null) {
            view.setVisible(true);
            if (!display(firstPosition)) {
                return;
            }
        }
        if (!latestPosition.equals(displayedPosition)) {
            display(latestPosition);
        }
    }

    private boolean display(Position position) {
        if (!invoke("setPosition", position.latitude(), position.longitude())) {
            return false;
        }
        displayedPosition = position;
        return true;
    }

    private boolean invoke(String method, Object... arguments) {
        if (page == null) {
            return false;
        }
        try {
            page.call(method, arguments);
            return true;
        } catch (RuntimeException exception) {
            unavailable();
            return false;
        }
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setVisible(!message.isEmpty());
    }

    private void unavailable() {
        close();
        if (view != null) {
            view.setVisible(false);
        }
        showStatus("Nepavyko įkelti žemėlapio");
    }

    @Override
    public void close() {
        page = null;
        requests.clear();
        if (tiles != null) {
            tiles.close();
            tiles = null;
        }
    }

    public final class TileBridge {
        public void requestTile(int id, int zoom, int x, int y) {
            if (tiles == null || page == null) {
                return;
            }
            if (zoom < 0 || zoom > OsmTiles.MAX_ZOOM || x < 0 || y < 0 || x >= (1 << zoom) || y >= (1 << zoom)) {
                Platform.runLater(() -> invoke("completeTile", id, null));
                return;
            }
            Call call = tiles.request(zoom, x, y, source -> Platform.runLater(() -> {
                if (requests.remove(id) != null) {
                    invoke("completeTile", id, source);
                }
            }));
            requests.put(id, call);
        }

        public void cancelTile(int id) {
            Call call = requests.remove(id);
            if (call != null) {
                call.cancel();
            }
        }

        public void tilesLoading() {
            showStatus("Įkeliamas žemėlapis…");
        }

        public void tilesLoaded(int succeeded, int failed) {
            if (failed == 0) {
                showStatus("");
            } else if (succeeded == 0) {
                showStatus("Žemėlapio plytelės nepasiekiamos");
            } else {
                showStatus("Dalis žemėlapio plytelių nepasiekiama");
            }
        }
    }
}
