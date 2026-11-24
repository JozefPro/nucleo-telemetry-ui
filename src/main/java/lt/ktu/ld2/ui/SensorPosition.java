package lt.ktu.ld2.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
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
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import lt.ktu.ld2.protocol.Measurement;

final class SensorPosition extends VBox {
    private final TiltFilter filter = new TiltFilter();
    private final Rotate roll = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(0, Rotate.X_AXIS);
    private final Timeline movement = new Timeline();
    private final SubScene view;

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
        getChildren().addAll(title, viewport);

        if (!Platform.isSupported(ConditionalFeature.SCENE3D)) {
            view = null;
            Label unavailable = new Label("Šiame įrenginyje 3D vaizdas nepasiekiamas");
            unavailable.setWrapText(true);
            unavailable.getStyleClass().add("secondary-text");
            viewport.getChildren().add(unavailable);
            return;
        }

        Box box = new Box(126, 80, 30);
        box.setMaterial(new PhongMaterial(Color.web("#3678c1")));
        Box mark = new Box(15, 9, 2);
        mark.setTranslateX(42);
        mark.setTranslateY(-24);
        mark.setTranslateZ(-15);
        mark.setMaterial(new PhongMaterial(Color.web("#dbeafe")));
        Group model = new Group(box, mark);
        model.getTransforms().addAll(roll, pitch);

        Group angledView = new Group(model);
        angledView.getTransforms().addAll(new Rotate(-20, Rotate.Y_AXIS), new Rotate(18, Rotate.X_AXIS));
        AmbientLight ambient = new AmbientLight(Color.color(0.66, 0.69, 0.75));
        DirectionalLight light = new DirectionalLight(Color.WHITE);
        light.setDirection(new Point3D(0.5, 0.6, 1));
        Group world = new Group(angledView, ambient, light);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-325);
        camera.setFarClip(700);
        camera.setFieldOfView(38);
        view = new SubScene(world, 200, 170, true, SceneAntialiasing.DISABLED);
        view.setCamera(camera);
        view.setMouseTransparent(true);
        view.widthProperty().bind(Bindings.max(1, viewport.widthProperty().subtract(2)));
        view.heightProperty().bind(Bindings.max(1, viewport.heightProperty().subtract(2)));
        viewport.getChildren().add(view);
    }

    void update(Measurement measurement) {
        if (view == null) {
            return;
        }
        var angles = filter.update(measurement);
        movement.stop();
        movement.getKeyFrames().setAll(new KeyFrame(Duration.millis(275),
                new KeyValue(roll.angleProperty(), angles.roll(), Interpolator.EASE_BOTH),
                new KeyValue(pitch.angleProperty(), angles.pitch(), Interpolator.EASE_BOTH)));
        movement.playFromStart();
    }

    void reset() {
        movement.stop();
        filter.reset();
        roll.setAngle(0);
        pitch.setAngle(0);
    }

    void setDark(boolean dark) {
        if (view != null) {
            view.setFill(Color.web(dark ? "#1b2534" : "#f5f7fa"));
        }
    }
}
