package dev.schemarray.shape;

/** Inside tests for every ShapeType, in the shape's own box coordinates (0..size-1 per axis). Work package A. */
public final class Shapes {
    private Shapes() {}

    /** Whether cell (x, y, z) belongs to the solid shape (before the outline/fill split). spec is already clamped. */
    public static boolean inside(ShapeSpec spec, int x, int y, int z) {
        throw new UnsupportedOperationException("WP A");
    }
}
