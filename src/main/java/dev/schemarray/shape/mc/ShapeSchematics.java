package dev.schemarray.shape.mc;

import dev.schemarray.Compat;
import dev.schemarray.shape.VoxelResult;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** VoxelResult -> LitematicaSchematic: one region "Shape" from 0,0,0 to size-1, author = the player. Render thread only. */
public final class ShapeSchematics {
    private ShapeSchematics() {}

    static final String REGION = "Shape";

    /** Builds the schematic; throws IllegalArgumentException naming the block when a palette entry does not parse. */
    public static LitematicaSchematic build(VoxelResult r, String name) {
        BlockState[] states = new BlockState[r.palette().size()];
        for (int i = 0; i < states.length; i++) {
            String entry = r.palette().get(i);
            states[i] = BlockParsing.parse(entry);
            if (states[i] == null) throw new IllegalArgumentException("Invalid block: " + entry);
        }

        AreaSelection area = new AreaSelection();
        area.setName(name);
        area.addSubRegionBox(new Box(BlockPos.ZERO, new BlockPos(r.sizeX() - 1, r.sizeY() - 1, r.sizeZ() - 1), REGION), false);
        area.setExplicitOrigin(BlockPos.ZERO);
        LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(area, Compat.playerName());
        if (schematic == null) throw new IllegalArgumentException("Could not create the schematic");

        LitematicaBlockStateContainer container = schematic.getSubRegionContainer(REGION);
        short[] cells = r.cells();
        int i = 0;
        for (int y = 0; y < r.sizeY(); y++) {
            for (int z = 0; z < r.sizeZ(); z++) {
                for (int x = 0; x < r.sizeX(); x++, i++) {
                    if (cells[i] != 0) container.set(x, y, z, states[cells[i] - 1]);
                }
            }
        }

        long now = System.currentTimeMillis();
        schematic.getMetadata().setTotalBlocks(r.blockCount());
        schematic.getMetadata().setTimeCreated(now);
        schematic.getMetadata().setTimeModified(now);
        return schematic;
    }
}
