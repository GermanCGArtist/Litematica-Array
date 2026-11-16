package dev.schemarray.editor.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Which blocks of an object are removed in the world (EDITOR-2.0 section 5). Pure: block occupancy of other objects
 * comes from an {@link Occupancy} that the Minecraft side builds from a loaded schematic and its world transform.
 *
 * <p>A plan lists the contributors of one object:
 * <ul>
 *   <li>{@link Kind#TRIM}: an owner (object or assembly) with a better front value whose boxes overlap this object.
 *       Where it has a non-air block, this object loses its block (a partly filled winner trims only where it is solid);
 *   <li>{@link Kind#DIFFERENCE}: an enabled Boolean modifier: removed where the target is non-air;
 *   <li>{@link Kind#INTERSECT}: an enabled Boolean modifier: kept only where the target is non-air.
 * </ul>
 * Disabled modifiers, wire objects (as losers or as winners of a trim), hidden nodes and Heavymatica/External nodes
 * never contribute. The {@link Plan#hash} depends only on the file modification time of the source, its own transform
 * and, for each contributor, the modification times, transforms and operation of every placed copy it brings, so it is
 * the same for the same inputs and changes when any of them does.
 */
public final class MaskPlan {
    private MaskPlan() {}

    /** Whether the world block at a position is a non-air block of some placed schematic. */
    @FunctionalInterface
    public interface Occupancy {
        boolean solid(int x, int y, int z);
    }

    public enum Kind { TRIM, DIFFERENCE, INTERSECT }

    /** One placed copy that a contributor brings: its file, the file's modification time, world transform and world box. */
    public record Placed(Path file, long mtime, Transform world, Box box) {}

    /** What a plan needs from the outside: file lookup, modification times and (lazy) occupancy. */
    public interface Env extends AssemblyBounds.Env {
        /** The file of an object whose files are relative to {@code base} (null = the model's rule), or null. */
        Path file(ObjectNode o, Path base);

        /** Modification time of the file in ms, 0 when it cannot be read. */
        long mtime(Path file);

        /** The occupancy of the union of these placed copies; may load files lazily. */
        Occupancy occupancy(List<Placed> placed);
    }

    /** One source of removals. {@code id} is the winner (TRIM) or the Boolean target. */
    public record Contributor(Kind kind, UUID id, List<Placed> placed, Occupancy occupancy) {
        public Contributor {
            placed = List.copyOf(placed);
        }

        /** Whether the block position lies in the box of any placed copy. */
        public boolean inBoxes(int x, int y, int z) {
            Vec3i p = new Vec3i(x, y, z);
            for (Placed pl : placed) if (pl.box().contains(p)) return true;
            return false;
        }

        String signature() {
            List<String> parts = new ArrayList<>();
            for (Placed p : placed) parts.add(p.mtime() + "@" + canon(p.world()));
            parts.sort(null);
            return kind + parts.toString();
        }
    }

    /** What to include in a plan. */
    public record Filter(boolean trims, boolean booleans, int onlyModifier) {
        public static final Filter ALL = new Filter(true, true, -1);

        /** Only the Boolean modifier at {@code index} (for Apply). */
        public static Filter onlyBoolean(int index) {
            return new Filter(false, true, index);
        }
    }

    /** The plan of one object. Empty when nothing is removed (use the source file). */
    public record Plan(UUID objectId, long sourceMtime, Transform world, List<Contributor> contributors, String hash) {
        public Plan {
            contributors = List.copyOf(contributors);
        }

        public boolean isEmpty() {
            return contributors.isEmpty();
        }

        /** Whether the block at this world position is removed. */
        public boolean removes(int x, int y, int z) {
            return remover(x, y, z) != null;
        }

        /** The first contributor that removes the block at this world position, or null when it stays. */
        public Contributor remover(int x, int y, int z) {
            for (Contributor c : contributors) {
                switch (c.kind()) {
                    case TRIM, DIFFERENCE -> {
                        if (c.inBoxes(x, y, z) && c.occupancy().solid(x, y, z)) return c;
                    }
                    case INTERSECT -> {
                        if (!c.inBoxes(x, y, z) || !c.occupancy().solid(x, y, z)) return c;
                    }
                }
            }
            return null;
        }
    }

    public static Plan emptyPlan(UUID objectId) {
        return new Plan(objectId, 0, Transform.IDENTITY, List.of(), "");
    }

    /**
     * Plans one schematic object in a project of the model. Loose objects (no project folder for the cache), wire
     * objects, hidden or missing ones and unreadable files give an empty plan.
     *
     * @param owners the front owners of the world ({@code Scene.frontOwnersInWorld}), the candidates for trims
     */
    public static Plan plan(Scene scene, ObjectNode target, Collection<? extends HasFront> owners, Env env, Filter f) {
        UUID id = target.id();
        if (target.wire() || target.missing() || hiddenChain(target) || scene.projectOf(target) == null) return emptyPlan(id);
        Path file = env.file(target, null);
        Vec3i size = env.size(target, null);
        if (file == null || size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) return emptyPlan(id);
        long mtime = env.mtime(file);
        if (mtime <= 0) return emptyPlan(id);
        Transform own = scene.worldTransform(target);
        Box ownBox = Box.ofSize(size).transformed(own);
        Node ownOwner = scene.ownerOf(target);
        int ownFront = ownOwner instanceof HasFront h ? h.front() : 0;

        List<Contributor> cs = new ArrayList<>();
        if (f.trims()) {
            for (HasFront w : owners) {
                if (!(w instanceof Node wn) || wn.id().equals(ownOwner.id())) continue;
                if (wn instanceof ObjectNode wo ? wo.wire() || wo.missing() : !(wn instanceof AssemblyNode)) continue;
                if (hiddenChain(wn)) continue;
                if (FrontOrder.compare(w.front(), wn.id(), ownFront, ownOwner.id()) >= 0) continue;
                List<Placed> placed = placedOf(scene, wn, env, ownBox);
                if (!placed.isEmpty()) cs.add(new Contributor(Kind.TRIM, wn.id(), placed, env.occupancy(placed)));
            }
        }
        if (f.booleans()) {
            List<Modifier> mods = target.mods();
            for (int i = 0; i < mods.size(); i++) {
                if (!(mods.get(i) instanceof BooleanModifier bm) || !bm.enabled || bm.targetId == null) continue;
                if (f.onlyModifier() >= 0 && f.onlyModifier() != i) continue;
                Node t = scene.byId(bm.targetId);
                if (!(t instanceof ObjectNode || t instanceof AssemblyNode) || t == target || isAncestor(t, target) || isAncestor(target, t)) continue;
                boolean intersect = bm.op == BooleanModifier.Op.INTERSECT;
                // a cutter that does not touch the object removes nothing (difference) and is left out; an intersect keeps every copy
                List<Placed> placed = placedOf(scene, t, env, intersect ? null : ownBox);
                if (placed.isEmpty()) continue;   // an unreadable or hidden target is ignored, never "remove everything"
                cs.add(new Contributor(intersect ? Kind.INTERSECT : Kind.DIFFERENCE, t.id(), placed, env.occupancy(placed)));
            }
        }
        if (cs.isEmpty()) return emptyPlan(id);
        return new Plan(id, mtime, own, cs, hash(mtime, own, cs));
    }

    /** The placed copies of a node (arrays and parts included); with {@code within} only those whose box overlaps it. */
    private static List<Placed> placedOf(Scene scene, Node n, Env env, Box within) {
        Transform parent = n.parent() == null ? Transform.IDENTITY : scene.worldTransform(n.parent());
        List<Placed> out = new ArrayList<>();
        for (BakeParts.Part p : BakeParts.collect(List.of(n), parent, null, env)) {
            Path file = env.file(p.object(), p.base());
            Vec3i size = env.size(p.object(), p.base());
            if (file == null || size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) continue;
            long mt = env.mtime(file);
            if (mt <= 0) continue;
            Box box = Box.ofSize(size).transformed(p.world());
            if (within != null && within.intersect(box).isEmpty()) continue;
            out.add(new Placed(file, mt, p.world(), box));
        }
        return out;
    }

    /** The stable hash of the inputs (16 hex digits). */
    public static String hash(long sourceMtime, Transform own, List<Contributor> contributors) {
        List<String> sigs = new ArrayList<>();
        for (Contributor c : contributors) sigs.add(c.signature());
        sigs.sort(null);
        String text = "own:" + sourceMtime + "@" + canon(own) + "|" + String.join("|", sigs);
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String canon(Transform t) {
        Transform c = t.canonical();
        return c.pos().x() + "," + c.pos().y() + "," + c.pos().z() + "/" + c.rot().steps() + "/" + c.mirror();
    }

    private static boolean hiddenChain(Node n) {
        for (Node x = n; x != null; x = x.parent()) if (x.hidden()) return true;
        return false;
    }

    private static boolean isAncestor(Node anc, Node n) {
        for (Node x = n.parent(); x != null; x = x.parent()) if (x == anc) return true;
        return false;
    }
}
