package dev.schemarray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The arrays, keyed by the hash id of the original placement (Litematica keeps that id in its own placement file, so
 * it survives restarts). Each array remembers the ids of its copy placements per cell, so they are found again.
 * Saved in config/schemarray/arrays.json.
 */
final class ArrayStore {
    private ArrayStore() {}

    static final class Entry {
        int[] counts = {1, 1, 1};   // along the schematic's own x, y, z; negative = towards minus
        int[] gaps = {0, 0, 0};
        Map<String, String> copies = new LinkedHashMap<>();   // "x,y,z" -> copy placement hash id

        boolean active() {
            return Math.abs(counts[0]) * Math.abs(counts[1]) * Math.abs(counts[2]) > 1;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Entry> arrays;
    private static Map<String, String> copyToParent;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("schemarray").resolve("arrays.json");
    }

    static Map<String, Entry> all() {
        if (arrays == null) {
            arrays = new LinkedHashMap<>();
            Path f = file();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f)) {
                    Map<String, Entry> m = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, Entry>>() {}.getType());
                    if (m != null) arrays.putAll(m);
                } catch (Exception ex) {
                    System.out.println("[SchematicArray] cannot read " + f + ": " + ex);
                }
            }
            reindex();
        }
        return arrays;
    }

    static void reindex() {
        copyToParent = new HashMap<>();
        for (var e : arrays.entrySet()) for (String c : e.getValue().copies.values()) copyToParent.put(c, e.getKey());
    }

    /** The original placement's id if this id is one of our copies, else null. */
    static String parentOf(UUID id) {
        all();
        return copyToParent.get(id.toString());
    }

    static Entry get(UUID parent) {
        return all().get(parent.toString());
    }

    static Entry getOrCreate(UUID parent) {
        return all().computeIfAbsent(parent.toString(), k -> new Entry());
    }

    static void remove(String parent) {
        all().remove(parent);
        reindex();
    }

    static void save() {
        reindex();
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f)) {
                GSON.toJson(arrays, w);
            }
        } catch (Exception ex) {
            System.out.println("[SchematicArray] cannot write " + f + ": " + ex);
        }
    }
}
