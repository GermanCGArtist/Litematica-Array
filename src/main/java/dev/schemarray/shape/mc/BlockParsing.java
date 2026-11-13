package dev.schemarray.shape.mc;

import dev.schemarray.shape.BlockEntry;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

/** Block state strings <-> Minecraft block states, for the screen and the schematic builder. Work package B. */
public final class BlockParsing {
    private BlockParsing() {}

    /** Parses "oak_log[axis=x]" (namespace optional). Null if invalid. */
    public static BlockState parse(String blockState) {
        throw new UnsupportedOperationException("WP B");
    }

    /** Human readable reason why the string does not parse, or null when it is valid. */
    public static String error(String blockState) {
        throw new UnsupportedOperationException("WP B");
    }

    /** Block ids ("minecraft:stone", ...) starting with the typed prefix (namespace optional), sorted, at most limit. */
    public static List<String> suggest(String prefix, int limit) {
        throw new UnsupportedOperationException("WP B");
    }

    /** The block state string of the item in the player's main hand (with its default state), or null. */
    public static String fromMainHand() {
        throw new UnsupportedOperationException("WP B");
    }

    /**
     * Parses one block field: "stone" -> [stone x1]; "60%stone_bricks,30%cracked_stone_bricks" -> weighted entries
     * (a part without "%" weighs 1). Does not validate the block names (use error() per entry for that).
     */
    public static List<BlockEntry> parseEntries(String text) {
        throw new UnsupportedOperationException("WP B");
    }

    /** Inverse of parseEntries: "60%a,30%b" (or just "a" for one entry with weight 1). */
    public static String formatEntries(List<BlockEntry> entries) {
        throw new UnsupportedOperationException("WP B");
    }
}
