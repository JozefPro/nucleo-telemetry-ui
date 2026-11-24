package lt.ktu.ld2.ui;

import javafx.geometry.Point3D;

record Quaternion(double w, double x, double y, double z) {
    static final Quaternion IDENTITY = new Quaternion(1, 0, 0, 0);

    static Quaternion axisAngle(Point3D axis, double radians) {
        Point3D vector = axis.normalize().multiply(Math.sin(radians / 2));
        return new Quaternion(Math.cos(radians / 2), vector.getX(), vector.getY(), vector.getZ());
    }

    // Both directions must be unit vectors.
    static Quaternion between(Point3D from, Point3D to) {
        double dot = from.dotProduct(to);
        Point3D cross = from.crossProduct(to);
        if (dot < 0 && cross.magnitude() < 1e-8) {
            Point3D basis = Math.abs(from.getX()) < 0.8 ? new Point3D(1, 0, 0) : new Point3D(0, 1, 0);
            return axisAngle(from.crossProduct(basis), Math.PI);
        }
        return new Quaternion(1 + dot, cross.getX(), cross.getY(), cross.getZ()).normalized();
    }

    Quaternion multiply(Quaternion b) {
        return new Quaternion(w * b.w - x * b.x - y * b.y - z * b.z,
                w * b.x + x * b.w + y * b.z - z * b.y,
                w * b.y - x * b.z + y * b.w + z * b.x,
                w * b.z + x * b.y - y * b.x + z * b.w);
    }

    Quaternion normalized() {
        return scaled(1 / Math.sqrt(dot(this)));
    }

    Quaternion interpolate(Quaternion target, double fraction) {
        double cosine = dot(target);
        if (cosine < 0) {
            target = target.scaled(-1);
            cosine = -cosine;
        }
        if (cosine > 0.9995) {
            return scaled(1 - fraction).plus(target.scaled(fraction)).normalized();
        }
        double angle = Math.acos(cosine);
        double sine = Math.sin(angle);
        return scaled(Math.sin((1 - fraction) * angle) / sine)
                .plus(target.scaled(Math.sin(fraction * angle) / sine));
    }

    Point3D rotate(Point3D point) {
        Point3D vector = new Point3D(x, y, z);
        Point3D cross = vector.crossProduct(point).multiply(2);
        return point.add(cross.multiply(w)).add(vector.crossProduct(cross));
    }

    private double dot(Quaternion b) {
        return w * b.w + x * b.x + y * b.y + z * b.z;
    }

    private Quaternion scaled(double factor) {
        return new Quaternion(w * factor, x * factor, y * factor, z * factor);
    }

    private Quaternion plus(Quaternion b) {
        return new Quaternion(w + b.w, x + b.x, y + b.y, z + b.z);
    }
}
