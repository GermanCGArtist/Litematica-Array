package dev.schemarray.shape;

/** Picks the block of a slot for a cell, see PatternMode for each mode. Work package A. */
public final class Patterns {
    private Patterns() {}

    /**
     * Index into slot.entries() for cell (x, y, z) of a shape of the given size, or -1 for air (only BRICK without
     * mortar never returns -1; an air slot always returns -1). Deterministic: same inputs, same answer.
     */
    public static int pick(BlockSlot slot, int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        throw new UnsupportedOperationException("WP A");
    }
}
