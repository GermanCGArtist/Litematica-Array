package dev.schemarray.editor;

import dev.schemarray.editor.cam.EditorCamera;
import dev.schemarray.editor.core.Box;
import dev.schemarray.editor.core.Node;
import dev.schemarray.editor.core.ObjectNode;
import dev.schemarray.editor.core.Overlaps;
import dev.schemarray.editor.core.ProjectNode;
import dev.schemarray.editor.core.Scene;
import dev.schemarray.editor.core.Vec3i;
import dev.schemarray.editor.gizmo.Pivots;
import dev.schemarray.editor.mc.ExternalChange;
import dev.schemarray.editor.ops.Move;
import dev.schemarray.editor.world.Gizmo;
import dev.schemarray.editor.mc.ProjectManager;
import dev.schemarray.editor.mc.Reconciler;
import dev.schemarray.editor.mc.SchematicInfo;
import dev.schemarray.editor.ops.ChangeSink;
import dev.schemarray.editor.ops.EditContext;
import dev.schemarray.editor.ops.ExternalChangeCommand;
import dev.schemarray.editor.ops.NioFileSystemPort;
import dev.schemarray.editor.ops.UndoStack;
import dev.schemarray.editor.pick.WorldBoxes;
import dev.schemarray.editor.ui.EditorActions;
import dev.schemarray.editor.ui.SelectionModel;
import dev.schemarray.editor.ui.ToastQueue;
import dev.schemarray.editor.ui.TreeModel;
import fi.dy.masa.litematica.data.DataManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * The glue of the editor UI (WP 1.4/1.5): one object per game that owns the undo stack, the edit context, the
 * selection, the active tool and the toasts, and connects them to the reconciler and the project manager.
 *
 * <ul>
 *   <li>The {@link EditContext} is rooted at Litematica's schematics folder; its change sink only flags that the
 *       projects need saving (the reconciler already looks at the model every tick).</li>
 *   <li>Drift the reconciler finds (a placement moved outside the editor) is recorded on the undo stack as an
 *       {@link ExternalChangeCommand}.</li>
 *   <li>When the world changes the project manager builds a new {@link Scene}; the session then starts a fresh
 *       context, undo stack and selection.</li>
 * </ul>
 * Render/client thread only. Needs Litematica; use {@link #get()}, which is null without it.
 */
public final class EditorSession {
    private static final Logger LOG = LoggerFactory.getLogger("schemarray-editor");

    public enum Tool { SELECT, MOVE, ROTATE }

    private static EditorSession instance;
    private static boolean failed;

    /** The session, or null when Litematica is missing or it failed to start. */
    public static EditorSession get() {
        if (instance == null && !failed) {
            if (!FabricLoader.getInstance().isModLoaded("litematica")) {
                failed = true;
                return null;
            }
            try {
                instance = new EditorSession();
            } catch (Throwable t) {
                failed = true;
                LOG.warn("[schemarray] editor session failed to start", t);
            }
        }
        return instance;
    }

    /** Called every client tick (after the reconciler): keeps the session in step with the world and saves. */
    public static void tick(Minecraft mc) {
        EditorSession s = get();
        if (s != null) s.tickInternal(mc);
    }

    // ------------------------------------------------------------------------------------------ state

    private final ProjectManager pm = Reconciler.projects();
    private Scene scene;
    private EditContext ctx;
    private UndoStack undo;
    private NioFileSystemPort port;
    private Path schematicsDir;

    public final SelectionModel selection = new SelectionModel();
    public final TreeModel tree = new TreeModel();
    public final ToastQueue toasts = new ToastQueue();
    public final EditorActions actions;
    private Tool tool = Tool.SELECT;

    public boolean windowsHidden;
    public boolean showScene = true, showProperties = true;
    /** Snapping toolbar toggle (Ctrl inverts it while dragging). Saved in the UI config. */
    public boolean snap;
    /** Local view is on (see {@link #toggleLocalView()}); runtime only. */
    public boolean localView;
    /** Pivot mode of the toolbar dropdown. Saved in the UI config. */
    public Pivots.Mode pivotMode = Pivots.Mode.MEDIAN;
    /** The move gizmo, ring, typed transforms and box select. */
    public final Gizmo gizmo = new Gizmo(this);
    private Set<UUID> localOwners;

    /** UI requests the windows pick up on their next frame. */
    public UUID renameRequest, scrollToRow, addSchematicParent;
    public boolean focusFilter, addMenuRequest, newProjectRequest, openProjectRequest;
    public boolean popupOpen;
    public boolean resetLayout;

    private boolean dirtyFlag;
    private int dirtyTicks;
    private boolean driftRegistered;

    private WorldBoxes boxes;
    private long boxesAt;
    private Set<UUID> issues = Set.of();
    private final Map<java.nio.file.Path, Long> unreadable = new HashMap<>();
    private Map<UUID, List<Overlaps.Overlap>> overlapsByNode = Map.of();

    private final Properties config = new Properties();
    private final Path configFile = FabricLoader.getInstance().getConfigDir().resolve("schemarray").resolve("editor-ui.properties");

    private EditorSession() {
        actions = new EditorActions(this::undoStack, selection, new HostImpl());
        loadConfig();
        snap = flag("snap", false);
        try {
            pivotMode = Pivots.Mode.valueOf(config.getProperty("pivot", Pivots.Mode.MEDIAN.name()));
        } catch (IllegalArgumentException e) {
            pivotMode = Pivots.Mode.MEDIAN;
        }
        sync();
    }

    /** Saves the snap toggle and the pivot mode (call after the toolbar changed one). */
    public void saveToolbarState() {
        config.setProperty("pivot", pivotMode.name());
        setFlag("snap", snap);
    }

    // ------------------------------------------------------------------------------------------ pivot, lock, local view

    /** The pivot block of a selection under the current pivot mode, or null when none of it has a visible box. */
    public Vec3i pivotOf(List<UUID> ids) {
        WorldBoxes wb = boxes();
        Scene sc = scene();
        List<Box> boxes = new ArrayList<>();
        for (UUID id : ids) {
            Box b = wb.unionOf(sc, List.of(id));
            if (b != null) boxes.add(b);
        }
        if (boxes.isEmpty()) return null;
        UUID a = selection.active();
        Box active = a == null || !ids.contains(a) ? null : wb.unionOf(sc, List.of(a));
        return Pivots.compute(pivotMode, boxes, active);
    }

    /** True when the selection cannot be moved (it is, or holds, a part of a linked assembly): the gizmo shows disabled. */
    public boolean selectionLocked() {
        List<UUID> ids = selection.ids();
        if (ids.isEmpty()) return false;
        return new Move(ids, new Vec3i(1, 0, 0)).check(context()).isPresent();
    }

    /**
     * Local view (/): only the owners of the selected objects stay placed. Lightest way that works: the Reconciler
     * gets a runtime filter ({@code ReconcileCore.setLocalView}), so nothing is saved and nothing in Litematica's own
     * toggles changes; the other placements are removed and come back when it is switched off. The boxes follow, so
     * picking and the gizmo only see what is visible.
     */
    public void toggleLocalView() {
        if (localOwners != null) {
            setLocalOwners(null);
            return;
        }
        Set<UUID> owners = new HashSet<>();
        Scene sc = scene();
        for (UUID id : selection.ids()) {
            Node n = sc.byId(id);
            if (n == null) continue;
            for (Node x : n.subtree()) if (x instanceof ObjectNode) owners.add(sc.ownerOf(x).id());
            if (n instanceof dev.schemarray.editor.core.AssemblyNode) owners.add(sc.ownerOf(n).id());
        }
        if (owners.isEmpty()) {
            toast(ToastQueue.Level.INFO, "Select an object first, then / isolates it");
            return;
        }
        setLocalOwners(owners);
    }

    private void setLocalOwners(Set<UUID> owners) {
        localOwners = owners;
        localView = owners != null;
        Reconciler.setLocalView(owners);
        invalidateBoxes();
    }

    private boolean visibleInLocalView(ObjectNode o) {
        if (localOwners == null) return true;
        Scene sc = scene();
        return sc.projectOf(o) == null || localOwners.contains(sc.ownerOf(o).id());   // Loose objects are not filtered
    }

    // ------------------------------------------------------------------------------------------ accessors

    public ProjectManager projects() {
        return pm;
    }

    public Scene scene() {
        sync();
        return scene;
    }

    public UndoStack undoStack() {
        sync();
        return undo;
    }

    public EditContext context() {
        sync();
        return ctx;
    }

    public Path schematicsDir() {
        return schematicsDir;
    }

    public Tool tool() {
        return tool;
    }

    public void setTool(Tool t) {
        tool = t;
    }

    public ToastQueue.Toast toast(ToastQueue.Level level, String text) {
        return toasts.add(level, text);
    }

    /** Makes the context follow the project manager's scene (it is replaced when a world is entered). */
    private void sync() {
        Scene now = pm.scene();
        if (now == scene) return;
        if (schematicsDir == null) schematicsDir = DataManager.getSchematicsBaseDirectory().toAbsolutePath().normalize();
        scene = now;
        gizmo.cancel();
        if (localOwners != null) setLocalOwners(null);          // a new world: the filter's ids do not exist there
        port = new NioFileSystemPort(schematicsDir);
        ctx = new EditContext(scene, port, new ChangeSink() {
            @Override
            public void markDirty(UUID projectId) {
                dirtyFlag = true;
                dirtyTicks = 0;
                boxesAt = 0;
            }

            @Override
            public void affected(java.util.Collection<UUID> nodeIds) {
                boxesAt = 0;
            }
        }).setPortRoot(schematicsDir).setLooseDir("");
        undo = new UndoStack(ctx);
        selection.clear();
        boxes = null;
    }

    private void tickInternal(Minecraft mc) {
        sync();
        if (!driftRegistered) {
            driftRegistered = true;
            Reconciler.setDriftListener(this::onDrift);
        }
        if (dirtyFlag && ++dirtyTicks >= 20) {
            dirtyFlag = false;
            try {
                pm.saveChanged();
            } catch (RuntimeException e) {
                LOG.warn("[schemarray] saving failed", e);
            }
        }
        selection.prune(id -> scene.byId(id) != null);
    }

    private void onDrift(ExternalChange change) {
        ObjectNode n = change.node();
        undo.record(ExternalChangeCommand.transform(n.id(), n.name(), change.before(), change.after()));
        boxesAt = 0;
    }

    // ------------------------------------------------------------------------------------------ sizes and boxes

    /** The file of an object as an absolute path (project files below the project folder, Loose below schematics). */
    public Path fileOf(ObjectNode o) {
        if (o.file().isEmpty()) return null;
        try {
            ProjectNode p = scene().projectOf(o);
            if (p != null) {
                Path dir = Path.of(p.path());
                if (!dir.isAbsolute()) dir = schematicsDir.resolve(dir);
                return dir.resolve(o.file()).normalize();
            }
            Path f = Path.of(o.file());
            return f.isAbsolute() ? f.normalize() : schematicsDir.resolve(f).normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The enclosing size of an object's schematic, or null when it cannot be read. */
    public Vec3i sizeOf(ObjectNode o) {
        Path f = fileOf(o);
        if (f == null) return null;
        long now = System.currentTimeMillis();
        Long until = unreadable.get(f);
        if (until != null && now < until) return null;          // a file that failed is not parsed again every frame
        Vec3i size = null;
        try {
            size = SchematicInfo.shared().size(f);
        } catch (RuntimeException e) {
            size = null;
        }
        if (size == null) unreadable.put(f, now + 3000);
        else unreadable.remove(f);
        return size;
    }

    /** The world boxes of the scene, recomputed at most every 100 ms and after every change. */
    public WorldBoxes boxes() {
        long now = System.nanoTime();
        if (boxes == null || boxesAt == 0 || now - boxesAt > 100_000_000L) {
            Scene sc = scene();
            boxes = new WorldBoxes(sc, o -> visibleInLocalView(o) ? sizeOf(o) : null);
            boxesAt = now;
            computeOverlaps(sc);
        }
        return boxes;
    }

    /** Forces the next {@link #boxes()} call to recompute. */
    public void invalidateBoxes() {
        boxesAt = 0;
    }

    private void computeOverlaps(Scene sc) {
        Set<UUID> ids = new HashSet<>();
        Map<UUID, List<Overlaps.Overlap>> by = new HashMap<>();
        for (Overlaps.Overlap o : Overlaps.find(boxes.overlapItems())) {
            ids.add(o.loser());
            ids.add(o.winner());
            by.computeIfAbsent(o.loser(), k -> new ArrayList<>()).add(o);
            by.computeIfAbsent(o.winner(), k -> new ArrayList<>()).add(o);
        }
        for (Node n : sc.allNodes()) if (n instanceof ObjectNode o && o.missing()) ids.add(o.id());
        issues = ids;
        overlapsByNode = by;
    }

    /** Objects and assemblies that overlap another one, plus missing objects (the Issues chip). */
    public Set<UUID> issues() {
        boxes();
        return issues;
    }

    public List<Overlaps.Overlap> overlapsOf(UUID id) {
        boxes();
        return overlapsByNode.getOrDefault(id, List.of());
    }

    // ------------------------------------------------------------------------------------------ config

    private void loadConfig() {
        try (InputStream in = Files.newInputStream(configFile)) {
            config.load(in);
        } catch (IOException ignored) {
            // no file yet
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream out = Files.newOutputStream(configFile)) {
                config.store(out, "Litematica Array editor UI");
            }
        } catch (IOException e) {
            LOG.debug("[schemarray] cannot save the editor ui config", e);
        }
    }

    public boolean flag(String key, boolean def) {
        return Boolean.parseBoolean(config.getProperty(key, Boolean.toString(def)));
    }

    public void setFlag(String key, boolean value) {
        config.setProperty(key, Boolean.toString(value));
        saveConfig();
    }

    // ------------------------------------------------------------------------------------------ keys

    /**
     * An editor shortcut (DESIGN 12.5) outside text fields. {@code mods} are GLFW modifier bits. Returns true when
     * the key was used. Tree-only keys (arrows, Space, Enter) are handled by the Scene window.
     */
    public boolean onKey(int key, int mods) {
        boolean ctrl = (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0, alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        if (gizmo.modal()) {                       // a gesture is running: its keys only (DESIGN 12.4)
            gizmo.onKey(key, mods);
            return true;
        }
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_Z -> {
                    if (shift) actions.redo();
                    else actions.undo();
                    return true;
                }
                case GLFW.GLFW_KEY_Y -> {
                    actions.redo();
                    return true;
                }
                case GLFW.GLFW_KEY_S -> {
                    pm.saveAll();
                    toast(ToastQueue.Level.INFO, "Saved all open projects");
                    return true;
                }
                case GLFW.GLFW_KEY_F -> {
                    focusFilter = true;
                    showScene = true;
                    return true;
                }
                case GLFW.GLFW_KEY_G -> {
                    actions.run("assembly.create");
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_W -> tool = Tool.SELECT;
            case GLFW.GLFW_KEY_G -> {
                tool = Tool.MOVE;
                if (!selection.isEmpty()) gizmo.startTypedMove(0);
            }
            case GLFW.GLFW_KEY_R -> {
                tool = Tool.ROTATE;
                if (!selection.isEmpty()) gizmo.startTypedRotate();
            }
            case GLFW.GLFW_KEY_M -> actions.run("t.mirror");
            case GLFW.GLFW_KEY_B -> gizmo.armBox();
            case GLFW.GLFW_KEY_A -> {
                if (shift) addMenuRequest = true;
                else actions.run(alt ? "select.none" : "select.all");
            }
            case GLFW.GLFW_KEY_H -> actions.run(alt ? "show.hidden" : "hide");
            case GLFW.GLFW_KEY_D -> {
                if (selection.isEmpty()) return false;
                if (shift) gizmo.startTypedMove(1);          // the copy starts as a typed move (the preview shows where it lands)
                else if (alt) gizmo.startTypedMove(2);
                else return false;
            }
            case GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_DELETE -> {
                if (!selection.isEmpty()) actions.run("delete");
            }
            case GLFW.GLFW_KEY_PAGE_UP -> actions.run(shift ? "front.front" : "front.forward");
            case GLFW.GLFW_KEY_PAGE_DOWN -> actions.run(shift ? "front.back" : "front.backward");
            case GLFW.GLFW_KEY_F2 -> actions.run("rename");
            case GLFW.GLFW_KEY_TAB -> windowsHidden = !windowsHidden;
            case GLFW.GLFW_KEY_SLASH -> toggleLocalView();
            case GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_KP_DECIMAL -> actions.run("frame");
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------ host

    private final class HostImpl implements EditorActions.Host {
        @Override
        public Vec3i sizeOf(ObjectNode o) {
            return EditorSession.this.sizeOf(o);
        }

        @Override
        public Box boxOf(List<UUID> ids) {
            return boxes().unionOf(scene(), ids);
        }

        @Override
        public void frame(Box box) {
            Box b = box;
            if (b == null) {
                for (WorldBoxes.Owned o : boxes().owners().values()) b = b == null ? o.union() : b.union(o.union());
            }
            if (b == null) return;
            EditorCamera.get().frame(new AABB(b.min().x(), b.min().y(), b.min().z(), b.maxExclusive().x(), b.maxExclusive().y(), b.maxExclusive().z()));
        }

        @Override
        public void toast(ToastQueue.Level level, String text, String actionLabel, Runnable action) {
            toasts.add(level, text, actionLabel, action);
        }

        @Override
        public void beginRename(UUID id) {
            renameRequest = id;
            scrollToRow = id;
            showScene = true;
            windowsHidden = false;
        }

        @Override
        public void requestAddSchematic(UUID parentId) {
            addSchematicParent = parentId;
        }

        @Override
        public void focusFilter() {
            focusFilter = true;
        }

        @Override
        public void setActive(ProjectNode p) {
            pm.setActive(p.id());
        }

        @Override
        public boolean closeProject(ProjectNode p) {
            boolean ok = pm.close(p.id());
            if (ok) {
                undo.clear();
                EditorSession.this.toast(ToastQueue.Level.INFO, "Closed \"" + p.name() + "\" (saved)");
            }
            return ok;
        }

        @Override
        public void showInExplorer(ProjectNode p) {
            try {
                Path dir = Path.of(p.path());
                if (!dir.isAbsolute()) dir = schematicsDir.resolve(dir);
                net.minecraft.util.Util.getPlatform().openPath(dir);
            } catch (RuntimeException e) {
                EditorSession.this.toast(ToastQueue.Level.WARN, "Cannot open the folder: " + e.getMessage());
            }
        }

        @Override
        public void saveAll() {
            pm.saveAll();
        }

        @Override
        public void toggleWindows() {
            windowsHidden = !windowsHidden;
        }

        @Override
        public Vec3i pivot(List<UUID> ids) {
            return pivotOf(ids);
        }

        @Override
        public void startMove() {
            tool = Tool.MOVE;
            gizmo.startTypedMove(0);
        }

        @Override
        public void startBoxSelect() {
            gizmo.armBox();
        }

        @Override
        public void toggleLocalView() {
            EditorSession.this.toggleLocalView();
        }

        @Override
        public void showInTree(UUID id) {
            Node n = scene().byId(id);
            if (n != null) tree.reveal(n);
            scrollToRow = id;
        }
    }
}
