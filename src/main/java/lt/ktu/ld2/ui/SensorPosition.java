package lt.ktu.ld2.ui;

import javafx.animation.Transition;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.DirectionalLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import lt.ktu.ld2.protocol.Measurement;

import java.io.IOException;

final class SensorPosition extends VBox {
    private static final Quaternion DEFAULT_VIEW = Quaternion.axisAngle(Rotate.Y_AXIS, Math.toRadians(-1))
            .multiply(Quaternion.axisAngle(Rotate.X_AXIS, Math.toRadians(-90)));
    private static final double DEFAULT_DISTANCE = 345;
    private final TiltFilter filter = new TiltFilter();
    private final Affine orientation = new Affine();
    private final ToggleButton free = new ToggleButton("Laisvai");
    private final BoardModel board;
    private final SubScene view;
    private final PerspectiveCamera camera;
    private Quaternion tilt = Quaternion.IDENTITY;
    private Quaternion tiltFrom = tilt;
    private Quaternion tiltTo = tilt;
    private Quaternion orbit = DEFAULT_VIEW;
    private double heading;
    private Point3D lastDrag;
    private double lastX;
    private final Transition movement = new Transition() {
        {
            setCycleDuration(Duration.millis(275));
        }

        @Override
        protected void interpolate(double fraction) {
            tilt = tiltFrom.interpolate(tiltTo, fraction);
            if (!free.isSelected()) {
                renderOrientation();
            }
        }
    };

    SensorPosition() {
        super(8);
        getStyleClass().addAll("panel", "sensor-panel");
        Label title = new Label("Jutiklio padėtis");
        title.getStyleClass().add("section-title");
        StackPane viewport = new StackPane();
        viewport.getStyleClass().add("sensor-viewport");
        viewport.setMinWidth(0);
        viewport.setPrefWidth(210);
        viewport.setMinHeight(155);
        viewport.setPrefHeight(201);
        VBox.setVgrow(viewport, Priority.ALWAYS);
        getChildren().add(title);

        board = loadBoard(viewport);
        if (board == null) {
            view = null;
            camera = null;
            getChildren().add(viewport);
            return;
        }

        Group model = new Group(board.geometry());
        model.getTransforms().add(orientation);
        AmbientLight ambient = new AmbientLight(Color.color(0.65, 0.65, 0.65));
        DirectionalLight light = new DirectionalLight(Color.gray(0.48));
        light.setDirection(new Point3D(0.35, 0.5, 1));
        Group world = new Group(model, ambient, light);

        camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-DEFAULT_DISTANCE);
        camera.setFarClip(1200);
        camera.setFieldOfView(38);
        view = new SubScene(world, 200, 170, true, SceneAntialiasing.BALANCED);
        view.setCamera(camera);
        view.widthProperty().bind(Bindings.max(1, viewport.widthProperty().subtract(2)));
        view.heightProperty().bind(Bindings.max(1, viewport.heightProperty().subtract(2)));
        viewport.getChildren().add(view);
        getChildren().addAll(controls(), viewport);
        bindGestures();
        renderOrientation();
    }

    private static BoardModel loadBoard(StackPane viewport) {
        String message = "Šiame įrenginyje 3D vaizdas nepasiekiamas";
        if (Platform.isSupported(ConditionalFeature.SCENE3D)) {
            try {
                return new BoardModel();
            } catch (IOException | RuntimeException exception) {
                System.err.println("LD2 GLB: " + exception);
                message = "Nepavyko įkelti maketo modelio";
            }
        }
        Label unavailable = new Label(message);
        unavailable.setWrapText(true);
        unavailable.getStyleClass().add("secondary-text");
        viewport.getChildren().add(unavailable);
        return null;
    }

    private HBox controls() {
        ToggleGroup modes = new ToggleGroup();
        ToggleButton live = new ToggleButton("Gyvai");
        live.setToggleGroup(modes);
        free.setToggleGroup(modes);
        live.setSelected(true);
        live.setTooltip(new Tooltip("Jutiklio pasvirimas. Tempkite horizontaliai, kad pasuktumėte apie gravitacijos ašį."));
        free.setTooltip(new Tooltip("Tempdami sukite modelį. Sukite pelės ratuką, kad keistumėte mastelį."));
        for (var mode : new ToggleButton[]{live, free}) {
            mode.getStyleClass().add("model-control");
            mode.setOnAction(event -> {
                mode.setSelected(true);
                renderOrientation();
            });
        }
        Button reset = new Button("Atkurti vaizdą");
        reset.getStyleClass().add("model-control");
        reset.setOnAction(event -> {
            orbit = DEFAULT_VIEW;
            heading = 0;
            camera.setTranslateZ(-DEFAULT_DISTANCE);
            renderOrientation();
        });
        return new HBox(5, live, free, reset);
    }

    private void bindGestures() {
        view.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                lastDrag = arcball(event.getX(), event.getY());
                lastX = event.getX();
            }
        });
        view.setOnMouseReleased(event -> lastDrag = null);
        view.setOnMouseDragged(event -> {
            if (lastDrag == null || !event.isPrimaryButtonDown()) {
                return;
            }
            if (free.isSelected()) {
                Point3D next = arcball(event.getX(), event.getY());
                orbit = Quaternion.between(lastDrag, next).multiply(orbit).normalized();
                lastDrag = next;
            } else {
                heading = Math.IEEEremainder(heading + (event.getX() - lastX) * 0.015, 2 * Math.PI);
            }
            lastX = event.getX();
            renderOrientation();
        });
        view.setOnScroll(event -> {
            double distance = -camera.getTranslateZ() * Math.exp(-event.getDeltaY() / 480);
            camera.setTranslateZ(-Math.clamp(distance, 198, 660));
            event.consume();
        });
    }

    private Point3D arcball(double x, double y) {
        double radius = 0.46 * Math.min(view.getWidth(), view.getHeight());
        return arcball(x - view.getWidth() / 2, y - view.getHeight() / 2, radius);
    }

    static Point3D arcball(double x, double y, double radius) {
        double u = x / radius;
        double v = y / radius;
        double squared = u * u + v * v;
        return squared < 1 ? new Point3D(u, v, -Math.sqrt(1 - squared))
                : new Point3D(u, v, 0).normalize();
    }

    private void renderOrientation() {
        Quaternion rotation = free.isSelected() ? orbit
                : DEFAULT_VIEW.multiply(Quaternion.axisAngle(TiltFilter.UP, heading)).multiply(tilt);
        Point3D x = rotation.rotate(Rotate.X_AXIS);
        Point3D y = rotation.rotate(Rotate.Y_AXIS);
        Point3D z = rotation.rotate(Rotate.Z_AXIS);
        orientation.setToTransform(x.getX(), y.getX(), z.getX(), 0,
                x.getY(), y.getY(), z.getY(), 0, x.getZ(), y.getZ(), z.getZ(), 0);
        board.sortAcrylic(rotation);
    }

    void update(Measurement measurement) {
        if (view == null) {
            return;
        }
        Quaternion target = filter.update(measurement);
        if (!target.equals(tiltTo)) {
            tiltFrom = tilt;
            tiltTo = target;
            movement.playFromStart();
        }
    }

    void reset() {
        movement.stop();
        filter.reset();
        tilt = Quaternion.IDENTITY;
        tiltFrom = tilt;
        tiltTo = tilt;
        if (view != null && !free.isSelected()) {
            renderOrientation();
        }
    }

    void setDark(boolean dark) {
        if (view != null) {
            view.setFill(Color.web(dark ? "#1b2534" : "#f5f7fa"));
        }
    }
}
