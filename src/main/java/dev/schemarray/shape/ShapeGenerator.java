package dev.schemarray.shape;

import java.util.function.BooleanSupplier;

/**
 * Turns settings into voxels: solid mask from Shapes.inside, outline = solid cells within spec.thickness of a
 * non-solid cell (6-neighbour distance; cells outside the box count as non-solid), fill = the other solid cells.
 * Outline cells take their block from the outline slot, fill cells from the fill slot (Patterns.pick). Work package A.
 */
public final class ShapeGenerator {
    private ShapeGenerator() {}

    /**
     * Pure and thread safe; runs on the preview worker thread. Checks cancelled() at least once per Y layer and
     * returns null when it says true.
     */
    public static VoxelResult generate(ShapeSpec spec, BlockSlot outline, BlockSlot fill, BooleanSupplier cancelled) {
        throw new UnsupportedOperationException("WP A");
    }

    public static VoxelResult generate(GeneratorSettings s, BooleanSupplier cancelled) {
        return generate(s.spec().clamped(), s.outline(), s.fill(), cancelled);
    }
}
