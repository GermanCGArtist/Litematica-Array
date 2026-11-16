package dev.schemarray.editor.pick;

import dev.schemarray.editor.core.ArrayCells;
import dev.schemarray.editor.core.ArrayModifier;
import dev.schemarray.editor.core.AssemblyNode;
import dev.schemarray.editor.core.Box;
import dev.schemarray.editor.core.Modifier;
import dev.schemarray.editor.core.Node;
import dev.schemarray.editor.core.ObjectNode;
import dev.schemarray.editor.core.Overlaps;
import dev.schemarray.editor.core.Scene;
import dev.schemarray.editor.core.Transform;
import dev.schemarray.editor.core.Vec3i;
import dev.schemarray.editor.ui.TreeModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The boxes of the scene in world blocks, for picking, outlines, overlaps and framing. An object has one box for
 * its base placement and one per array cell; an owner (an object, or the topmost assembly above it) has all the
 * boxes of what it holds. Pure: sizes come from a function. Objects with an unknown size are left out.
 */
public final class WorldBoxes {
    /** The boxes of one owner: the owner node, its boxes, and the box around all of them. */
    public record Owned(Node owner, List<Box> boxes, Box union) {}

    private final Map<UUID, Owned> owners = new LinkedHashMap<>();
    private final Map<UUID, List<Box>> objects = new LinkedHashMap<>();

    /** Computes the boxes of every visible, present object of the scene. */
    public WorldBoxes(Scene scene, Function<ObjectNode, Vec3i> sizeOf) {
        Map<UUID, List<Box>> lists = new LinkedHashMap<>();
        Map<UUID, Node> nodes = new LinkedHashMap<>();
        for (Node n : scene.allNodes()) {
            if (!(n instanceof ObjectNode o) || o.missing() || TreeModel.effectivelyHidden(o)) continue;
            Vec3i size = sizeOf.apply(o);
            if (size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) continue;
            Node owner = scene.ownerOf(o);
            List<Box> mine = boxesOf(scene, o, size);
            objects.put(o.id(), mine);
            lists.computeIfAbsent(owner.id(), k -> new ArrayList<>()).addAll(mine);
            nodes.put(owner.id(), owner);
        }
        for (var e : lists.entrySet()) {
            List<Box> bs = e.getValue();
            Box u = bs.get(0);
            for (Box b : bs) u = u.union(b);
            owners.put(e.getKey(), new Owned(nodes.get(e.getKey()), bs, u));
        }
    }

    /** The base box of an object, then one box per array cell. */
    public static List<Box> boxesOf(Scene scene, ObjectNode o, Vec3i size) {
        Transform world = scene.worldTransform(o);
        Box base = Box.ofSize(size);
        List<Box> out = new ArrayList<>();
        out.add(base.transformed(world));
        List<ArrayModifier> arrays = new ArrayList<>();
        for (Modifier m : o.mods()) if (m instanceof ArrayModifier am) arrays.add(am);
        for (Vec3i off : ArrayCells.cells(arrays, size).values()) {
            out.add(base.transformed(new Transform(world.apply(off), world.rot(), world.mirror())));
        }
        return out;
    }

    /** The owners that can be picked, by id. */
    public Map<UUID, Owned> owners() {
        return owners;
    }

    public Owned ownerBoxes(UUID ownerId) {
        return owners.get(ownerId);
    }

    /** Picking candidates in the order of the scene. */
    public List<PickMath.Candidate> candidates() {
        List<PickMath.Candidate> out = new ArrayList<>();
        for (Owned o : owners.values()) {
            int front = o.owner() instanceof dev.schemarray.editor.core.HasFront f ? f.front() : 0;
            out.add(new PickMath.Candidate(o.owner().id(), front, o.boxes()));
        }
        return out;
    }

    /** The overlap items of the owners that take part in the front order. */
    public List<Overlaps.Item> overlapItems() {
        List<Overlaps.Item> out = new ArrayList<>();
        for (Owned o : owners.values()) {
            if (o.owner() instanceof ObjectNode || o.owner() instanceof AssemblyNode) out.add(Overlaps.Item.of(o.owner(), o.boxes()));
        }
        return out;
    }

    /** The boxes (base and array cells) of one visible object, or an empty list. */
    public List<Box> objectBoxes(UUID objectId) {
        return objects.getOrDefault(objectId, List.of());
    }

    /** The union of the boxes of the objects below these node ids (the nodes themselves included), or null. */
    public Box unionOf(Scene scene, List<UUID> ids) {
        Box u = null;
        for (UUID id : ids) {
            Node n = scene.byId(id);
            if (n == null) continue;
            for (Node x : n.subtree()) {
                for (Box b : objectBoxes(x.id())) u = u == null ? b : u.union(b);
            }
        }
        return u;
    }
}
