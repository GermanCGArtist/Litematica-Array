package dev.schemarray;

import dev.schemarray.shape.mc.ShapePreview;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the copy placements of every array where they belong. The copies are ordinary Litematica placements of the
 * same schematic (so they render, print and verify like any placement), placed at the original's origin plus the
 * cell offset, turned and mirrored with it. Runs every client tick, so moving, rotating, mirroring, hiding or
 * switching sub-regions of the original shows on the copies at once.
 */
public final class ArraySync {
    private ArraySync() {}

    /** Most copies one array may have. */
    public static final int MAX_COPIES = 1024;

    static final IMessageConsumer QUIET = new IMessageConsumer() {
        @Override
        public void addMessage(Message.MessageType type, String key, Object... args) {}

        @Override
        public void addMessage(Message.MessageType type, int lifeTime, String key, Object... args) {}
    };
    private static final IStringConsumer QUIET_STR = s -> {};

    private static Object lastLevel;
    private static int ticks;
    private static final List<Integer> rebuildAt = new ArrayList<>();

    public static void tick(Minecraft mc) {
        if (mc.level == null) { lastLevel = null; return; }
        ticks++;
        if (mc.level != lastLevel) {
            // just joined or changed dimension: Litematica loads the placements now, and copies of the same schematic
            // sometimes come up with chunks missing. Rebuild their chunks a little later, and once more after that.
            lastLevel = mc.level;
            rebuildAt.clear();
            rebuildAt.add(ticks + 40);
            rebuildAt.add(ticks + 200);
        }
        Map<String, ArrayStore.Entry> arrays = ArrayStore.all();
        if (arrays.isEmpty()) return;
        SchematicPlacementManager mgr = DataManager.getSchematicPlacementManager();
        Map<String, SchematicPlacement> byId = new HashMap<>();
        for (SchematicPlacement p : mgr.getAllSchematicsPlacements()) {
            if (!ShapePreview.isPreview(p)) byId.put(p.getHashId().toString(), p);
        }

        if (rebuildAt.remove((Integer) ticks)) {
            for (var e : arrays.entrySet()) {
                SchematicPlacement parent = byId.get(e.getKey());
                if (parent != null) mgr.markChunksForRebuild(parent);
                for (String c : e.getValue().copies.values()) {
                    SchematicPlacement cp = byId.get(c);
                    if (cp != null) mgr.markChunksForRebuild(cp);
                }
            }
        }

        boolean changed = false;
        for (String parentId : new ArrayList<>(arrays.keySet())) {
            ArrayStore.Entry e = arrays.get(parentId);
            SchematicPlacement parent = byId.get(parentId);
            if (parent == null) {
                // copies here but no original: it was removed, so its copies go too. No copies either: another world.
                boolean any = false;
                for (String c : e.copies.values()) {
                    SchematicPlacement cp = byId.get(c);
                    if (cp != null) { mgr.removeSchematicPlacement(cp, false); any = true; }
                }
                if (any) { ArrayStore.remove(parentId); changed = true; }
                continue;
            }
            changed |= sync(mgr, parent, e, byId);
            if (!e.active() && e.copies.isEmpty()) { ArrayStore.remove(parentId); changed = true; }
        }
        if (changed) ArrayStore.save();
    }

    /** Size of the schematic (its enclosing box). */
    public static Vec3i size(LitematicaSchematic s) {
        Vec3i t = s.getTotalSize();
        if (t != null && t.getX() != 0 && t.getY() != 0 && t.getZ() != 0) return new Vec3i(Math.abs(t.getX()), Math.abs(t.getY()), Math.abs(t.getZ()));
        int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        Map<String, BlockPos> sizes = s.getAreaSizes();
        for (var a : s.getAreaPositions().entrySet()) {
            BlockPos p = a.getValue(), sz = sizes.get(a.getKey());
            if (sz == null) continue;
            int[] pp = {p.getX(), p.getY(), p.getZ()}, ss = {sz.getX(), sz.getY(), sz.getZ()};
            for (int i = 0; i < 3; i++) {
                int end = ss[i] >= 0 ? pp[i] + ss[i] - 1 : pp[i] + ss[i] + 1;
                min[i] = Math.min(min[i], Math.min(pp[i], end));
                max[i] = Math.max(max[i], Math.max(pp[i], end));
            }
        }
        if (min[0] == Integer.MAX_VALUE) return new Vec3i(1, 1, 1);
        return new Vec3i(max[0] - min[0] + 1, max[1] - min[1] + 1, max[2] - min[2] + 1);
    }

    private static String cell(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static boolean sync(SchematicPlacementManager mgr, SchematicPlacement parent, ArrayStore.Entry e, Map<String, SchematicPlacement> byId) {
        LitematicaSchematic schematic = parent.getSchematic();
        if (schematic == null) return false;
        boolean changed = false;

        // which cells there should be (the original itself is cell 0,0,0)
        Set<String> want = new HashSet<>();
        long n = (long) Math.abs(e.counts[0]) * Math.abs(e.counts[1]) * Math.abs(e.counts[2]);
        if (n > 1 && n <= MAX_COPIES + 1) {
            for (int y = 0; y < Math.abs(e.counts[1]); y++)
                for (int z = 0; z < Math.abs(e.counts[2]); z++)
                    for (int x = 0; x < Math.abs(e.counts[0]); x++)
                        if (x != 0 || y != 0 || z != 0) want.add(cell(x, y, z));
        }

        // cells that are no longer wanted
        for (var it = e.copies.entrySet().iterator(); it.hasNext(); ) {
            var c = it.next();
            if (want.contains(c.getKey())) continue;
            SchematicPlacement cp = byId.get(c.getValue());
            if (cp != null) mgr.removeSchematicPlacement(cp, false);
            it.remove();
            changed = true;
        }

        Vec3i size = size(schematic);
        int[] step = {size.getX() + e.gaps[0], size.getY() + e.gaps[1], size.getZ() + e.gaps[2]};
        BlockPos origin = parent.getOrigin();
        Rotation rot = parent.getRotation();
        Mirror mir = parent.getMirror();

        Map<String, Boolean> subStates = new HashMap<>();
        for (SubRegionPlacement s : parent.getAllSubRegionsPlacements()) subStates.put(s.getName(), s.isEnabled());

        for (String key : want) {
            String[] k = key.split(",");
            int cx = Integer.parseInt(k[0]), cy = Integer.parseInt(k[1]), cz = Integer.parseInt(k[2]);
            int lx = cx * step[0] * Integer.signum(e.counts[0]);
            int ly = cy * step[1] * Integer.signum(e.counts[1]);
            int lz = cz * step[2] * Integer.signum(e.counts[2]);
            BlockPos at = origin.offset(transform(lx, ly, lz, mir, rot));

            String id = e.copies.get(key);
            SchematicPlacement cp = id == null ? null : byId.get(id);
            if (cp == null || cp.getSchematic() != schematic) {
                if (cp != null) mgr.removeSchematicPlacement(cp, false);
                UUID uuid = UUID.randomUUID();
                cp = SchematicPlacement.createFor(schematic, at, parent.getName() + " #" + key, parent.isEnabled(), parent.isRenderingEnabled(), uuid);
                cp.setRotation(rot, QUIET);
                cp.setMirror(mir, QUIET);
                mgr.addSchematicPlacement(cp, false);
                e.copies.put(key, uuid.toString());
                byId.put(uuid.toString(), cp);
                changed = true;
                if (!rebuildAt.contains(ticks + 20)) rebuildAt.add(ticks + 20);
            }
            if (cp.getRotation() != rot) cp.setRotation(rot, QUIET);
            if (cp.getMirror() != mir) cp.setMirror(mir, QUIET);
            if (!cp.getOrigin().equals(at)) cp.setOrigin(at, QUIET_STR);
            if (cp.isEnabled() != parent.isEnabled()) cp.setEnabled(parent.isEnabled());
            if (cp.isRenderingEnabled() != parent.isRenderingEnabled()) cp.setRenderSchematic(parent.isRenderingEnabled());
            for (SubRegionPlacement s : cp.getAllSubRegionsPlacements()) {
                Boolean on = subStates.get(s.getName());
                if (on != null && on != s.isEnabled()) cp.toggleSubRegionEnabled(s.getName(), QUIET);
            }
        }
        if (changed) ArrayStore.reindex();
        return changed;
    }

    /** Schematic coordinates to world offset: mirror first, then rotation (as Litematica does). */
    static BlockPos transform(int x, int y, int z, Mirror m, Rotation r) {
        if (m == Mirror.LEFT_RIGHT) z = -z;
        else if (m == Mirror.FRONT_BACK) x = -x;
        return switch (r) {
            case CLOCKWISE_90 -> new BlockPos(-z, y, x);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, -x);
            case CLOCKWISE_180 -> new BlockPos(-x, y, -z);
            default -> new BlockPos(x, y, z);
        };
    }

    /** The placement whose array the config screen edits: the original, also when a copy is open. */
    public static SchematicPlacement original(SchematicPlacement p) {
        String parent = ArrayStore.parentOf(p.getHashId());
        if (parent == null) return p;
        for (SchematicPlacement o : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (o.getHashId().toString().equals(parent)) return o;
        }
        return p;
    }

    public static boolean isCopy(SchematicPlacement p) {
        return ArrayStore.parentOf(p.getHashId()) != null;
    }

    public static int[] counts(SchematicPlacement original) {
        ArrayStore.Entry e = ArrayStore.get(original.getHashId());
        return e == null ? new int[]{1, 1, 1} : e.counts.clone();
    }

    public static int[] gaps(SchematicPlacement original) {
        ArrayStore.Entry e = ArrayStore.get(original.getHashId());
        return e == null ? new int[]{0, 0, 0} : e.gaps.clone();
    }

    /** Sets one count (axis 0..2) or gap; the copies follow on the next tick. */
    public static void set(SchematicPlacement original, boolean gap, int axis, int value) {
        ArrayStore.Entry e = ArrayStore.getOrCreate(original.getHashId());
        if (gap) e.gaps[axis] = value;
        else e.counts[axis] = value == 0 ? 1 : value;
        ArrayStore.save();
        tick(Minecraft.getInstance());
    }

    /** All copies of this array, as one list (for the label). */
    public static List<String> copyIds(SchematicPlacement original) {
        ArrayStore.Entry e = ArrayStore.get(original.getHashId());
        return e == null ? List.of() : new ArrayList<>(e.copies.values());
    }
}
