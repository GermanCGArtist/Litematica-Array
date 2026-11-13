package dev.schemarray.shape.mc;

import dev.schemarray.shape.VoxelResult;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;

/** Generate: writes the .litematic and turns the ghost into a normal placement. Render thread only. Work package B. */
public final class ShapeSaver {
    private ShapeSaver() {}

    /** The file the name would be written to (Litematica's schematics folder, ".litematic" added). */
    public static Path fileFor(String name) {
        throw new UnsupportedOperationException("WP B");
    }

    /**
     * Writes result as fileFor(name) (overwrite only when allowed), loads it, adds a normal placement at origin
     * named name, removes the ghost and selects the new placement. Returns null on success, else an error message
     * for the screen (e.g. "File exists", invalid block, write failed).
     */
    public static String save(VoxelResult result, String name, BlockPos origin, boolean overwrite) {
        throw new UnsupportedOperationException("WP B");
    }
}
