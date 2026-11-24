package lt.ktu.ld2.ui;

import de.javagl.jgltf.model.AccessorFloatData;
import de.javagl.jgltf.model.AccessorIntData;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import de.javagl.jgltf.model.PbrMaterialModel;
import javafx.geometry.Bounds;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import javafx.scene.transform.Affine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Imports the Fusion export's indexed triangles and untextured GLB materials. */
final class BoardModel {
    private final Group opaque = new Group();
    private final Group acrylic = new Group();
    private final Group geometry = new Group(opaque, acrylic);
    private final Map<PbrMaterialModel, PhongMaterial> materials = new HashMap<>();
    private final Point3D center;

    BoardModel() throws IOException {
        try (InputStream resource = BoardModel.class.getResourceAsStream("/models/komp-komun-ld2.glb")) {
            if (resource == null) {
                throw new IOException("Trūksta komp-komun-ld2.glb resurso.");
            }
            var document = new GltfModelReader().readWithoutReferences(resource);
            document.getSceneModels().getFirst().getNodeModels().forEach(this::addNode);
        }
        Bounds bounds = geometry.getBoundsInLocal();
        center = new Point3D(bounds.getCenterX(), bounds.getCenterY(), bounds.getCenterZ());
        double scale = 128 / Math.max(bounds.getWidth(), Math.max(bounds.getHeight(), bounds.getDepth()));
        geometry.getTransforms().add(new Affine(scale, 0, 0, -scale * center.getX(),
                0, scale, 0, -scale * center.getY(), 0, 0, scale, -scale * center.getZ()));
    }

    Group geometry() {
        return geometry;
    }

    void sortAcrylic(Quaternion orientation) {
        // Opaque meshes already filled the depth buffer. Blend from far to near.
        var sorted = acrylic.getChildren().stream().sorted(Comparator.comparingDouble(part -> {
            Bounds bounds = part.getBoundsInParent();
            return -orientation.rotate(new Point3D(bounds.getCenterX(), bounds.getCenterY(), bounds.getCenterZ())
                    .subtract(center)).getZ();
        })).toList();
        if (!sorted.equals(acrylic.getChildren())) {
            acrylic.getChildren().setAll(sorted);
        }
    }

    private void addNode(NodeModel node) {
        double[] m = node.computeGlobalTransform(null);
        // glTF Y is up. Put the board top toward the viewer (-Z) in its neutral pose.
        Affine axes = new Affine(m[0], m[4], m[8], m[12],
                m[2], m[6], m[10], m[14], -m[1], -m[5], -m[9], -m[13]);
        for (var mesh : node.getMeshModels()) {
            for (var primitive : mesh.getMeshPrimitiveModels()) {
                var material = (PbrMaterialModel) primitive.getMaterialModel();
                var view = new MeshView(triangles(primitive));
                view.setMaterial(materials.computeIfAbsent(material, BoardModel::material));
                view.getTransforms().add(axes);
                var layer = material.getAlphaMode() == PbrMaterialModel.AlphaMode.BLEND ? acrylic : opaque;
                layer.getChildren().add(view);
            }
        }
        node.getChildren().forEach(this::addNode);
    }

    private static TriangleMesh triangles(MeshPrimitiveModel primitive) {
        var mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        var positions = (AccessorFloatData) primitive.getAttributes().get("POSITION").getAccessorData();
        var normals = (AccessorFloatData) primitive.getAttributes().get("NORMAL").getAccessorData();
        var indices = (AccessorIntData) primitive.getIndices().getAccessorData();
        mesh.getPoints().setAll(vectors(positions));
        mesh.getNormals().setAll(vectors(normals));
        mesh.getTexCoords().setAll(0, 0);
        int[] faces = new int[indices.getNumElements() * 3];
        for (int i = 0; i < indices.getNumElements(); i++) {
            faces[3 * i] = indices.get(i);
            faces[3 * i + 1] = indices.get(i);
        }
        mesh.getFaces().setAll(faces);
        return mesh;
    }

    private static float[] vectors(AccessorFloatData accessor) {
        float[] result = new float[3 * accessor.getNumElements()];
        for (int i = 0; i < result.length; i++) {
            result[i] = accessor.get(i);
        }
        return result;
    }

    private static PhongMaterial material(PbrMaterialModel source) {
        var pbr = source.getPbrMetallicRoughnessModel();
        double[] rgba = pbr.getBaseColorFactor();
        double alpha = source.getAlphaMode() == PbrMaterialModel.AlphaMode.BLEND ? rgba[3] : 1;
        // Prism composites the shaded RGB with premultiplied alpha.
        var material = new PhongMaterial(Color.color(
                srgb(rgba[0]) * alpha, srgb(rgba[1]) * alpha, srgb(rgba[2]) * alpha, alpha));
        material.setSpecularColor(Color.gray((0.16 + pbr.getMetallicFactor() * 0.45) * alpha));
        material.setSpecularPower(2 + 62 * (1 - pbr.getRoughnessFactor()));
        return material;
    }

    private static double srgb(double linear) {
        return linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
    }
}
