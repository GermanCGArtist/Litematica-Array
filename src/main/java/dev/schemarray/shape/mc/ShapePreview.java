package dev.schemarray.shape.mc;

import dev.schemarray.shape.ShapeSpec;
import dev.schemarray.shape.VoxelResult;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.core.BlockPos;

/**
 * The in-world ghost: at most one temporary Litematica placement of the current VoxelResult. Render thread only.
 * Work package B.
 */
public final class ShapePreview {
    private ShapePreview() {}

    /**
     * Shows result at origin (the placement's min corner; see originFor). Replaces the previous ghost. A null
     * result just removes it.
     */
    public static void show(VoxelResult result, BlockPos origin) {
        throw new UnsupportedOperationException("WP B");
    }

    /** Where the ghost goes for this spec: the player's block position as min corner, or as box center when spec.anchorCenter(). */
    public static BlockPos originFor(ShapeSpec spec) {
        throw new UnsupportedOperationException("WP B");
    }

    /** The current origin of the ghost (the player may have moved it with Litematica's tools), or null. */
    public static BlockPos currentOrigin() {
        throw new UnsupportedOperationException("WP B");
    }

    /** Removes the ghost (Cancel, Esc, world leave). Safe to call when there is none. */
    public static void clear() {
        throw new UnsupportedOperationException("WP B");
    }

    /** Whether p is the ghost placement (Folders/ArraySync/placement list skip it). */
    public static boolean isPreview(SchematicPlacement p) {
        throw new UnsupportedOperationException("WP B");
    }
}
