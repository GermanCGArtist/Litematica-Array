package dev.schemarray.shape.mc;

import dev.schemarray.shape.GeneratorSettings;
import java.util.List;

/** Last settings and named presets in config/schemarray/shapes.json. Work package B. */
public final class ShapePresets {
    private ShapePresets() {}

    /** The settings of the last session, or GeneratorSettings.defaults(). */
    public static GeneratorSettings last() {
        throw new UnsupportedOperationException("WP B");
    }

    public static void setLast(GeneratorSettings settings) {
        throw new UnsupportedOperationException("WP B");
    }

    /** Preset names, sorted case-insensitively. */
    public static List<String> names() {
        throw new UnsupportedOperationException("WP B");
    }

    /** The preset, or null. */
    public static GeneratorSettings get(String name) {
        throw new UnsupportedOperationException("WP B");
    }

    /** Saves (or replaces) a preset and writes the file. */
    public static void put(String name, GeneratorSettings settings) {
        throw new UnsupportedOperationException("WP B");
    }

    public static void remove(String name) {
        throw new UnsupportedOperationException("WP B");
    }
}
