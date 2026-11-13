package dev.schemarray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.schemarray.shape.mc.ShapePreview;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The folders of the placement list. Folders are global (shared by all worlds) and can be nested; a placement sits in
 * at most one folder, keyed by its hash id like the arrays. A hidden folder switches the rendering of everything
 * inside it off; the placements it switched off itself are remembered, so ones the user had switched off stay off
 * when the folder is shown again. Saved in config/schemarray/folders.json.
 */
public final class Folders {
    private Folders() {}

    static final class Folder {
        String name = "";
        String parent;   // folder id, null = top level
        boolean open = true;
        boolean hidden;
        int color = -1;   // index into COLORS, -1 = none
    }

    private static final class Data {
        Map<String, Folder> folders = new LinkedHashMap<>();
        Map<String, String> placements = new LinkedHashMap<>();   // placement hash id -> folder id
        Set<String> hiddenByFolder = new LinkedHashSet<>();
        Map<String, Integer> placementColors = new LinkedHashMap<>();   // placement hash id -> color index
    }

    /** A folder row in the placement list. */
    public static final class Node {
        public final String id;
        public final String name;
        public final int depth;
        public final int count;
        public final boolean open;
        public final boolean hidden;
        public final int color;

        Node(String id, Folder f, int depth, int count) {
            this.id = id;
            this.name = f.name;
            this.depth = depth;
            this.count = count;
            this.open = f.open;
            this.hidden = f.hidden;
            this.color = f.color;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data;
    private static final Map<SchematicPlacement, Integer> DEPTH = new IdentityHashMap<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("schemarray").resolve("folders.json");
    }

    private static Data data() {
        if (data == null) {
            data = new Data();
            Path f = file();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f)) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null) {
                        if (d.folders != null) data.folders = d.folders;
                        if (d.placements != null) data.placements = d.placements;
                        if (d.hiddenByFolder != null) data.hiddenByFolder = d.hiddenByFolder;
                        if (d.placementColors != null) data.placementColors = d.placementColors;
                    }
                } catch (Exception ex) {
                    System.out.println("[SchematicArray] cannot read " + f + ": " + ex);
                }
            }
        }
        return data;
    }

    private static void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f)) {
                GSON.toJson(data(), w);
            }
        } catch (Exception ex) {
            System.out.println("[SchematicArray] cannot write " + f + ": " + ex);
        }
    }

    /** The folder's parent, or null if it is top level (or the parent no longer exists). */
    private static String parentId(Folder f) {
        return f.parent != null && data().folders.containsKey(f.parent) ? f.parent : null;
    }

    private static String folderOf(String placementId) {
        String id = data().placements.get(placementId);
        return id != null && data().folders.containsKey(id) ? id : null;
    }

    /** Where the placement sits: its folder id, null = top level. */
    public static String folderOf(SchematicPlacement p) {
        return folderOf(p.getHashId().toString());
    }

    /**
     * The rows of the list: per level the folders (by name) first, then the placements (by name); only the contents of
     * open folders. Also fills the depth the placement rows are indented with.
     */
    public static List<Object> rows(Collection<SchematicPlacement> placements) {
        DEPTH.clear();
        Map<String, List<SchematicPlacement>> byFolder = new LinkedHashMap<>();
        for (SchematicPlacement p : placements) byFolder.computeIfAbsent(folderOf(p), k -> new ArrayList<>()).add(p);
        List<Object> out = new ArrayList<>();
        addLevel(null, 0, byFolder, out, new HashSet<>());
        return out;
    }

    private static void addLevel(String folder, int depth, Map<String, List<SchematicPlacement>> byFolder, List<Object> out, Set<String> seen) {
        for (String id : childFolders(folder)) {
            if (!seen.add(id)) continue;
            Folder f = data().folders.get(id);
            out.add(new Node(id, f, depth, count(id, byFolder, new HashSet<>())));
            if (f.open) addLevel(id, depth + 1, byFolder, out, seen);
        }
        List<SchematicPlacement> own = new ArrayList<>(byFolder.getOrDefault(folder, List.of()));
        own.sort(Comparator.comparing(p -> p.getName().toLowerCase()));
        for (SchematicPlacement p : own) {
            DEPTH.put(p, depth);
            out.add(p);
        }
    }

    private static int count(String folder, Map<String, List<SchematicPlacement>> byFolder, Set<String> seen) {
        if (!seen.add(folder)) return 0;
        int n = byFolder.getOrDefault(folder, List.of()).size();
        for (String c : childFolders(folder)) n += count(c, byFolder, seen);
        return n;
    }

    private static List<String> childFolders(String parent) {
        List<String> ids = new ArrayList<>();
        for (var e : data().folders.entrySet()) if (Objects.equals(parentId(e.getValue()), parent)) ids.add(e.getKey());
        ids.sort(Comparator.comparing(id -> data().folders.get(id).name.toLowerCase()));
        return ids;
    }

    /** Every folder as a tree, open or not (for the Move screen). */
    public static List<Node> tree() {
        List<Node> out = new ArrayList<>();
        addTree(null, 0, out, new HashSet<>());
        return out;
    }

    private static void addTree(String parent, int depth, List<Node> out, Set<String> seen) {
        for (String id : childFolders(parent)) {
            if (!seen.add(id)) continue;
            out.add(new Node(id, data().folders.get(id), depth, 0));
            addTree(id, depth + 1, out, seen);
        }
    }

    /** How many levels the placement's row is indented. */
    public static int depthOf(SchematicPlacement p) {
        return DEPTH.getOrDefault(p, 0);
    }

    public static String create(String name, String parent) {
        Folder f = new Folder();
        f.name = name;
        f.parent = parent != null && data().folders.containsKey(parent) ? parent : null;
        String id = UUID.randomUUID().toString();
        data().folders.put(id, f);
        if (f.parent != null) data().folders.get(f.parent).open = true;
        save();
        return id;
    }

    public static void rename(String id, String name) {
        Folder f = data().folders.get(id);
        if (f == null) return;
        f.name = name;
        save();
    }

    public static void toggleOpen(String id) {
        Folder f = data().folders.get(id);
        if (f == null) return;
        f.open = !f.open;
        save();
    }

    public static void toggleHidden(String id) {
        Folder f = data().folders.get(id);
        if (f == null) return;
        f.hidden = !f.hidden;
        save();
    }

    /** Deletes the folder only: its subfolders and placements move up to its parent. */
    public static void delete(String id) {
        Folder f = data().folders.remove(id);
        if (f == null) return;
        String up = parentId(f);
        for (Folder c : data().folders.values()) if (id.equals(c.parent)) c.parent = up;
        for (var e : new ArrayList<>(data().placements.entrySet())) {
            if (!id.equals(e.getValue())) continue;
            if (up == null) data().placements.remove(e.getKey());
            else data().placements.put(e.getKey(), up);
        }
        save();
    }

    /** Puts the placement into the folder (null = top level). Array copies are never put in a folder. */
    public static void move(SchematicPlacement p, String folder) {
        if (ArraySync.isCopy(p)) return;
        String id = p.getHashId().toString();
        if (folder == null || !data().folders.containsKey(folder)) data().placements.remove(id);
        else data().placements.put(id, folder);
        save();
    }

    /** Minecraft's 16 chat colors (§0-§f) as RGB; a color is an index into this, -1 = none. */
    public static final int[] COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    public static void setFolderColor(String id, int color) {
        Folder f = data().folders.get(id);
        if (f == null) return;
        f.color = color;
        save();
    }

    /** The placement's own color, -1 = none. */
    public static int ownColor(SchematicPlacement p) {
        Integer c = data().placementColors.get(p.getHashId().toString());
        return c == null || c < 0 || c >= COLORS.length ? -1 : c;
    }

    public static void setPlacementColors(Collection<SchematicPlacement> placements, int color) {
        for (SchematicPlacement p : placements) {
            String id = p.getHashId().toString();
            if (color < 0) data().placementColors.remove(id);
            else data().placementColors.put(id, color);
        }
        save();
    }

    /** The color the row shows: its own, else that of the nearest colored folder above it, else -1. */
    public static int effectiveColor(SchematicPlacement p) {
        int own = ownColor(p);
        if (own >= 0) return own;
        Set<String> seen = new HashSet<>();
        String folder = folderOf(p);
        while (folder != null && seen.add(folder)) {
            Folder f = data().folders.get(folder);
            if (f == null) return -1;
            if (f.color >= 0 && f.color < COLORS.length) return f.color;
            folder = parentId(f);
        }
        return -1;
    }

    /** True if the folder is {@code ancestor} itself or sits somewhere below it. */
    public static boolean isInside(String folder, String ancestor) {
        Set<String> seen = new HashSet<>();
        while (folder != null && seen.add(folder)) {
            if (folder.equals(ancestor)) return true;
            Folder f = data().folders.get(folder);
            if (f == null) return false;
            folder = parentId(f);
        }
        return false;
    }

    /** Moves the folder (with everything in it) into the parent (null = top level); false if that would put it into itself. */
    public static boolean moveFolder(String id, String parent) {
        Folder f = data().folders.get(id);
        if (f == null) return false;
        if (parent != null && (!data().folders.containsKey(parent) || isInside(parent, id))) return false;
        f.parent = parent;
        if (parent != null) data().folders.get(parent).open = true;
        save();
        return true;
    }

    /** Puts all the placements into the folder (null = top level), opens that folder and saves once. */
    public static void moveAll(Collection<SchematicPlacement> placements, String folder) {
        boolean top = folder == null || !data().folders.containsKey(folder);
        for (SchematicPlacement p : placements) {
            if (ArraySync.isCopy(p)) continue;
            String id = p.getHashId().toString();
            if (top) data().placements.remove(id);
            else data().placements.put(id, folder);
        }
        if (!top) data().folders.get(folder).open = true;
        save();
    }

    private static boolean hidden(String folder) {
        Set<String> seen = new HashSet<>();
        while (folder != null && seen.add(folder)) {
            Folder f = data().folders.get(folder);
            if (f == null) return false;
            if (f.hidden) return true;
            folder = parentId(f);
        }
        return false;
    }

    /** Every tick: switches the rendering of placements in hidden folders off, and back on when the folder is shown. */
    public static void tick(Minecraft mc) {
        if (mc.level == null) return;
        Data d = data();
        if (d.folders.isEmpty() && d.hiddenByFolder.isEmpty()) return;
        boolean changed = false;
        for (SchematicPlacement p : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (ArraySync.isCopy(p) || ShapePreview.isPreview(p)) continue;
            String id = p.getHashId().toString();
            if (hidden(folderOf(id))) {
                if (p.isRenderingEnabled()) {
                    p.setRenderSchematic(false);
                    d.hiddenByFolder.add(id);
                    changed = true;
                }
            } else if (d.hiddenByFolder.contains(id)) {
                p.setRenderSchematic(true);
                d.hiddenByFolder.remove(id);
                changed = true;
            }
        }
        if (changed) save();
    }
}
