package dev.schemarray.editor.mc;

import dev.schemarray.editor.core.ArrayCells;
import dev.schemarray.editor.core.ArrayModifier;
import dev.schemarray.editor.core.AssemblyNode;
import dev.schemarray.editor.core.Ids;
import dev.schemarray.editor.core.Modifier;
import dev.schemarray.editor.core.Node;
import dev.schemarray.editor.core.ObjectNode;
import dev.schemarray.editor.core.ProjectNode;
import dev.schemarray.editor.core.Scene;
import dev.schemarray.editor.core.Transform;
import dev.schemarray.editor.core.Vec3i;
import dev.schemarray.editor.io.ProjectLayout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The planning and writing half of the reconciler (EDITOR-2.0 section 4), with no Minecraft types: it talks to the
 * placement list through {@link PlacementPort}, so the tests run it against a fake. {@link Reconciler} feeds it the
 * live world each tick.
 *
 * <p>One {@link #reconcile} call:
 * <ol>
 *   <li>walks the scene: every visible ObjectNode (not hidden itself or through an ancestor, in a project of the
 *       current world and dimension) gives its base cell and its array cells, each with a world transform;
 *   <li>per cell, finds the placement by the stored hash id; creates it when missing or of the wrong file (at most
 *       {@link #BUDGET} creations per call, the rest wait for the next), or moves it when it differs from the model;
 *   <li>removes placements the model no longer wants (hidden nodes, array cells that are gone, nodes that left the
 *       model) and never touches a placement it does not know;
 *   <li>reads back the base placement: when it was moved outside the editor (it differs from what this class last
 *       wrote or saw), the model takes the move and the {@link DriftListener} hears about it.
 * </ol>
 * The editor changing the model is not drift: the placement still equals the last state this class saw, so the model
 * wins and the placement follows. The first time a placement is met (after a join) the model wins for project
 * objects, and the placement wins for Loose objects (Litematica is their source of truth).
 *
 * <p>Linked assemblies: the parts come from the {@code .lasm} (cached by modification time); the placements of a part
 * are kept in a runtime map under {@code Ids.derived(instanceId + ":" + partId)} and never saved. Linked parts are
 * read only, so a move outside the editor is put back instead of adopted.
 */
public final class ReconcileCore {
    /** Most placements created in one call. */
    public static final int BUDGET = 64;
    /** Ticks between a creation and the chunk rebuild of its batch. */
    public static final int REBUILD_DELAY = 20;
    private static final int SUBREGION_EVERY = 5;

    public record Result(int created, int updated, int removed, int adopted, int deferred) {}

    private record Slot(Map<String, String> ids) {}

    private final PlacementPort port;
    private final SizeSource sizes;
    private final LasmCache lasms;
    private final Path looseBase;
    private DriftListener listener = DriftListener.NONE;
    private int budget = BUDGET;
    /** Local view (runtime only, never saved): the owners that stay visible; null = everything. */
    private Set<UUID> localOnly;

    /** Base placement id to the transform this class last wrote or saw. */
    private final Map<String, Transform> lastSeen = new HashMap<>();
    private Set<String> prevModelIds = new HashSet<>();
    private final Map<UUID, Map<String, String>> linkedIds = new HashMap<>();
    private final Set<String> batch = new HashSet<>();
    private int batchAt;
    private final TreeSet<Integer> fullRebuildAt = new TreeSet<>();

    /** @param looseBase the folder relative file paths of Loose objects are resolved against (may be null) */
    public ReconcileCore(PlacementPort port, SizeSource sizes, LasmCache lasms, Path looseBase) {
        this.port = port;
        this.sizes = sizes;
        this.lasms = lasms;
        this.looseBase = looseBase;
    }

    public void setListener(DriftListener l) {
        this.listener = l == null ? DriftListener.NONE : l;
    }

    /**
     * Local view: only these owners (ids of objects or assemblies, as {@code Scene.ownerOf} gives them) stay
     * placed; the others in projects are treated as hidden until this is cleared with null. Loose objects are not
     * affected (their placement is Litematica's own, and removing it would mark them missing).
     */
    public void setLocalView(Set<UUID> owners) {
        localOnly = owners == null ? null : Set.copyOf(owners);
    }

    public void setBudget(int budget) {
        this.budget = Math.max(1, budget);
    }

    /** Forgets everything remembered between calls (a new world). Placements are left alone. */
    public void reset() {
        lastSeen.clear();
        prevModelIds = new HashSet<>();
        linkedIds.clear();
        batch.clear();
        batchAt = 0;
        fullRebuildAt.clear();
        lasms.clear();
    }

    /** Marks all managed placements for a chunk rebuild at these ticks (after a join, Litematica needs it). */
    public void scheduleFullRebuild(int... ticks) {
        for (int t : ticks) fullRebuildAt.add(t);
    }

    /** Removes the placements of a project that is being closed (the ids of its linked parts go on the next call). */
    public void release(ProjectNode p) {
        for (Node n : p.subtree()) {
            if (!(n instanceof ObjectNode o)) continue;
            for (String id : o.placementIds().values()) {
                port.remove(id);
                lastSeen.remove(id);
            }
        }
    }

    private final class Run {
        final Scene scene;
        final int tick;
        final Set<String> existing;
        final Set<String> modelIds = new HashSet<>();
        final Set<String> claimed = new HashSet<>();
        int created, updated, removed, adopted, deferred;

        Run(Scene scene, int tick, Set<String> existing) {
            this.scene = scene;
            this.tick = tick;
            this.existing = existing;
        }
    }

    /**
     * One pass.
     *
     * @param world the world key of the player now; a project of another world or dimension has its placements removed
     * @param dim   the dimension of the player now
     * @param tick  a counter that grows by one per call (for the delayed rebuilds)
     */
    public Result reconcile(Scene scene, String world, String dim, int tick) {
        Run r = new Run(scene, tick, new HashSet<>(port.allHashIds()));
        for (ProjectNode p : new ArrayList<>(scene.projects())) {
            boolean here = p.world().equals(world) && p.dim().equals(dim);
            visit(p, !here, null, r);
        }
        visit(scene.loose(), false, null, r);

        // placements of nodes that left the model (deleted, project closed)
        for (String id : prevModelIds) {
            if (!r.modelIds.contains(id) && r.existing.remove(id)) {
                port.remove(id);
                lastSeen.remove(id);
                r.removed++;
            }
        }
        prevModelIds = r.modelIds;

        if (batchAt != 0 && tick >= batchAt) {
            for (String id : batch) if (r.existing.contains(id)) port.markRebuild(id);
            batch.clear();
            batchAt = 0;
        }
        if (!fullRebuildAt.isEmpty() && fullRebuildAt.first() <= tick) {
            while (!fullRebuildAt.isEmpty() && fullRebuildAt.first() <= tick) fullRebuildAt.pollFirst();
            for (String id : r.modelIds) if (r.existing.contains(id)) port.markRebuild(id);
        }
        return new Result(r.created, r.updated, r.removed, r.adopted, r.deferred);
    }

    // ------------------------------------------------------------------------------------------ the walk

    private void visit(Node n, boolean off, ProjectNode proj, Run r) {
        boolean o = off || n.hidden();
        if (n instanceof ProjectNode p) proj = p;
        if (!o && localOnly != null && proj != null && (n instanceof ObjectNode || n instanceof AssemblyNode a0 && a0.isLinked())
                && !localOnly.contains(r.scene.ownerOf(n).id())) o = true;
        if (n instanceof ObjectNode ob) {
            object(new Slot(ob.placementIds()), ob, o, r.scene.worldTransform(ob), proj, r, false);
        } else if (n instanceof AssemblyNode a && a.isLinked()) {
            Transform aw = r.scene.worldTransform(a);
            List<Node> parts = linkedParts(proj, (AssemblyNode.Link) a.source());
            if (parts != null) for (Node p : parts) visitLinked(p, o, aw, a.id(), proj, r);
        } else {
            for (Node c : new ArrayList<>(n.children())) visit(c, o, proj, r);
        }
    }

    private void visitLinked(Node node, boolean off, Transform parentWorld, UUID instanceId, ProjectNode proj, Run r) {
        boolean o = off || node.hidden();
        Transform w = parentWorld.then(node.localTransform());
        UUID key = Ids.derived(instanceId + ":" + node.id());
        if (node instanceof ObjectNode ob) {
            Slot s = new Slot(linkedIds.computeIfAbsent(key, k -> new LinkedHashMap<>()));
            object(s, ob, o, w, proj, r, true);
        } else if (node instanceof AssemblyNode a && a.isLinked()) {
            List<Node> parts = linkedParts(proj, (AssemblyNode.Link) a.source());
            if (parts != null) for (Node p : parts) visitLinked(p, o, w, key, proj, r);
        } else {
            for (Node c : node.children()) visitLinked(c, o, w, instanceId, proj, r);
        }
    }

    private List<Node> linkedParts(ProjectNode proj, AssemblyNode.Link link) {
        if (proj == null) return null;
        Path file = resolve(proj, link.path());
        return file == null ? null : lasms.parts(file);
    }

    // ------------------------------------------------------------------------------------------ one object

    private void object(Slot s, ObjectNode ob, boolean off, Transform world, ProjectNode proj, Run r, boolean linked) {
        Map<String, String> ids = s.ids;
        r.modelIds.addAll(ids.values());
        if (off) {
            for (String id : ids.values()) {
                if (r.existing.remove(id)) {
                    port.remove(id);
                    lastSeen.remove(id);
                    r.removed++;
                }
            }
            return;
        }
        Path file = resolve(proj, ob.file());
        Vec3i size = file == null ? null : sizes.size(file);
        if (size == null) return;   // unreadable: leave its placements as they are

        String baseId = ids.get(ObjectNode.BASE_CELL);
        PlacementState bs = baseId != null && r.existing.contains(baseId) ? port.read(baseId) : null;
        if (!linked && proj == null && baseId != null && bs == null) {
            ob.setMissing(true);   // a Loose object whose placement is not in Litematica: keep the node, stop reconciling it
            return;
        }
        ob.setMissing(false);

        // drift: the base placement was moved outside the editor
        if (bs != null && !linked) {
            Transform actual = bs.transform(), seen = lastSeen.get(baseId);
            boolean external = seen == null ? proj == null : !actual.equals(seen);
            if (external && !actual.equals(world)) {
                Node parent = ob.parent();
                Transform pw = parent == null ? Transform.IDENTITY : r.scene.worldTransform(parent);
                Transform local = TransformMath.inverse(pw).then(actual);
                Transform before = ob.transform();
                ob.setTransform(local);
                world = actual;
                r.adopted++;
                listener.onExternalChange(new ExternalChange(ob, before, local));
            }
        }

        Map<String, Transform> want = new LinkedHashMap<>();
        want.put(ObjectNode.BASE_CELL, world);
        List<ArrayModifier> arrays = new ArrayList<>();
        for (Modifier m : ob.mods()) if (m instanceof ArrayModifier am) arrays.add(am);
        for (var c : ArrayCells.cells(arrays, size).entrySet()) {
            want.put(c.getKey(), new Transform(world.apply(c.getValue()), world.rot(), world.mirror()));
        }

        // cells that are no longer wanted
        for (var it = ids.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (want.containsKey(e.getKey())) continue;
            if (r.existing.remove(e.getValue())) {
                port.remove(e.getValue());
                lastSeen.remove(e.getValue());
                r.removed++;
            }
            it.remove();
        }

        boolean enabled = bs == null || bs.enabled();
        boolean render = bs != null ? bs.render() : !ob.wire();
        for (var e : want.entrySet()) {
            String key = e.getKey();
            Transform t = e.getValue();
            boolean isBase = key.equals(ObjectNode.BASE_CELL);
            String id = ids.get(key);
            if (id != null && !r.claimed.add(id)) id = null;   // another node already owns this placement
            PlacementState st = id == null || !r.existing.contains(id) ? null : isBase ? bs : port.read(id);
            boolean wrongFile = st != null && !samePath(st.file(), file);
            if (st == null || wrongFile) {
                if (r.created >= budget) {
                    r.deferred++;
                    continue;
                }
                if (wrongFile) {
                    port.remove(id);
                    r.existing.remove(id);
                    lastSeen.remove(id);
                }
                String nid = port.create(file, t.pos(), t.rot(), t.mirror(), isBase ? ob.name() : ob.name() + " #" + key, enabled, render, id);
                if (nid == null) continue;
                ids.put(key, nid);
                r.claimed.add(nid);
                r.existing.add(nid);
                r.modelIds.add(nid);
                r.created++;
                if (isBase) lastSeen.put(nid, t);
                if (batchAt == 0 || batchAt <= r.tick) {
                    batch.clear();
                    batchAt = r.tick + REBUILD_DELAY;
                }
                batch.add(nid);
                continue;
            }
            boolean tdiff = !st.transform().equals(t);
            boolean sdiff = !isBase && (st.enabled() != enabled || st.render() != render);
            if (tdiff || sdiff) {
                port.update(id, t.pos(), t.rot(), t.mirror(), isBase ? st.enabled() : enabled, isBase ? st.render() : render);
                r.updated++;
            }
            if (isBase) lastSeen.put(id, t);
            else if (r.tick % SUBREGION_EVERY == 0 && baseId != null && r.existing.contains(ids.get(ObjectNode.BASE_CELL))) {
                port.copySubRegions(ids.get(ObjectNode.BASE_CELL), id);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ files

    /**
     * A file path of the model as an absolute path: relative to the project folder inside a project (an absolute
     * path is refused there), absolute or relative to the schematics folder for Loose objects. Null when unusable.
     */
    Path resolve(ProjectNode proj, String file) {
        if (file == null || file.isEmpty()) return null;
        try {
            if (proj != null) {
                return proj.path().isEmpty() ? null : ProjectLayout.resolveInside((looseBase != null ? looseBase.resolve(proj.path()).normalize() : Path.of(proj.path())), file);
            }
            Path p = Path.of(file);
            if (p.isAbsolute()) return p.normalize();
            return looseBase == null ? null : looseBase.resolve(file).normalize();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean samePath(Path a, Path b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        try {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
