package dev.schemarray.editor.world;

import dev.cg.uihost.Theme;
import dev.cg.uihost.UiHost;
import dev.schemarray.editor.EditorMode;
import dev.schemarray.editor.EditorSession;
import dev.schemarray.editor.cam.EditorCamera;
import dev.schemarray.editor.core.AssemblyNode;
import dev.schemarray.editor.core.Box;
import dev.schemarray.editor.core.Node;
import dev.schemarray.editor.core.ObjectNode;
import dev.schemarray.editor.mc.Picking;
import dev.schemarray.editor.pick.ViewMath;
import dev.schemarray.editor.pick.WorldBoxes;
import dev.schemarray.editor.ui.TreeModel;
import imgui.ImDrawList;
import imgui.ImGui;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the editor draws in the world (WP 1.5, selection only): depth-tested box outlines for the selection, fainter
 * ones for array copies and the hover, drawn with Minecraft's gizmos like Heavymatica's wireframes; and name tags
 * for the selection, drawn with ImGui's background draw list from projected points (DESIGN 11.1, 11.7).
 * Missing nodes have no box and so draw nothing.
 */
public final class WorldOverlay {
    private WorldOverlay() {}

    private static final double INFLATE = 0.02;

    /** Registers the render hook. Call once from the client entrypoint. */
    public static void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> renderOutlines());
    }

    private static int argb(int rgb, float alpha) {
        return (Math.round(alpha * 255f) << 24) | (rgb & 0xFFFFFF);
    }

    private static AABB aabb(Box b) {
        return new AABB(b.min().x(), b.min().y(), b.min().z(), b.maxExclusive().x(), b.maxExclusive().y(), b.maxExclusive().z()).inflate(INFLATE);
    }

    // ------------------------------------------------------------------ outlines (Minecraft gizmos)

    private record Line(AABB box, int rgb, float alpha, float width, boolean xray) {}

    private static void renderOutlines() {
        if (!EditorMode.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        EditorSession s = EditorSession.get();
        if (s == null || mc.levelRenderer == null || mc.player == null) return;
        List<Line> lines = new ArrayList<>();
        collect(s, lines);
        if (lines.isEmpty()) return;
        try (var ignored = mc.levelRenderer.collectPerFrameGizmos()) {
            for (Line l : lines) {
                var p = Gizmos.cuboid(l.box(), GizmoStyle.stroke(argb(l.rgb(), l.alpha()), l.width()));
                if (l.xray()) p.setAlwaysOnTop();
            }
        }
    }

    private static void collect(EditorSession s, List<Line> out) {
        WorldBoxes wb = s.boxes();
        var scene = s.scene();
        UUID active = s.selection.active();
        UUID hover = Picking.hover();
        if (hover != null && !s.selection.isSelected(hover)) {
            WorldBoxes.Owned o = wb.ownerBoxes(hover);
            if (o != null) {
                boolean linked = o.owner() instanceof AssemblyNode a && a.isLinked();
                out.add(new Line(aabb(o.union()), linked ? Theme_LINK : Theme.FG, linked ? 0.6f : 0.45f, 1.5f, false));
            }
        }
        for (UUID id : s.selection.ids()) {
            Node n = scene.byId(id);
            if (n == null) continue;
            boolean isActive = id.equals(active);
            boolean locked = TreeModel.lockedPart(n);
            int rgb = locked ? Theme.GUTTER : Theme.ACCENT;
            float alpha = isActive || locked ? 1f : 0.75f;
            float width = isActive ? 2f : 1.5f;
            if (n instanceof ObjectNode o) {
                List<Box> boxes = wb.objectBoxes(o.id());
                if (boxes.isEmpty()) continue;                                    // missing or hidden: nothing to draw
                out.add(new Line(aabb(boxes.get(0)), rgb, alpha, width, false));
                if (!locked) out.add(new Line(aabb(boxes.get(0)), rgb, isActive ? 0.30f : 0.20f, 1f, true));
                for (int i = 1; i < boxes.size(); i++) out.add(new Line(aabb(boxes.get(i)), rgb, 0.5f, 1f, false));
                Node owner = scene.ownerOf(o);
                if (owner != o && wb.ownerBoxes(owner.id()) != null) out.add(new Line(aabb(wb.ownerBoxes(owner.id()).union()), rgb, 0.35f, 1f, false));
            } else if (n instanceof AssemblyNode) {
                WorldBoxes.Owned ow = wb.ownerBoxes(id);
                if (ow == null) continue;
                out.add(new Line(aabb(ow.union()), rgb, alpha, width, false));
                if (!locked) out.add(new Line(aabb(ow.union()), rgb, isActive ? 0.30f : 0.20f, 1f, true));
            } else {
                Box u = wb.unionOf(scene, List.of(id));                              // folder, project, Loose: what they hold
                if (u != null) out.add(new Line(aabb(u), rgb, 0.35f, 1f, false));
            }
        }
    }

    private static final int Theme_LINK = 0x7fc8f0;

    // ------------------------------------------------------------------ name tags (ImGui)

    /** Draws the tags of the selection with the background draw list. Call from the layer's draw. */
    public static void drawTags() {
        EditorSession s = EditorSession.get();
        EditorCamera cam = EditorCamera.get();
        if (s == null || s.selection.isEmpty() || cam.isNavigating()) return;       // they flicker while flying and orbiting
        Minecraft mc = Minecraft.getInstance();
        WorldBoxes wb = s.boxes();
        var scene = s.scene();
        List<UUID> ids = s.selection.ids();
        double w = Math.max(1, mc.getWindow().getScreenWidth()), h = Math.max(1, mc.getWindow().getScreenHeight());
        ViewMath.Basis basis = ViewMath.Basis.of(cam.yaw(), cam.pitch());
        Vec3 cp = cam.position();
        double[] c = {cp.x, cp.y, cp.z};
        double fov = mc.options.fov().get();
        float sc = UiHost.scale();

        // up to 8 tags; with more, only the active one, with "+n"
        List<UUID> tagged = new ArrayList<>(ids);
        UUID active = s.selection.active();
        String extra = null;
        if (tagged.size() > 8 && active != null) {
            extra = "+" + (tagged.size() - 1);
            tagged = List.of(active);
        }
        ImDrawList dl = ImGui.getBackgroundDrawList();
        Theme.push(UiHost.theme().monoS());
        float fh = ImGui.getFontSize(), tagH = 18 * sc;
        List<float[]> placed = new ArrayList<>();
        for (UUID id : tagged) {
            Node n = scene.byId(id);
            if (n == null) continue;
            Box box = n instanceof ObjectNode ? firstBox(wb, id) : wb.unionOf(scene, List.of(id));
            if (box == null) continue;
            double[] p = ViewMath.project(basis, c, fov, w, h, (box.min().x() + box.maxExclusive().x()) * 0.5, box.maxExclusive().y(),
                    (box.min().z() + box.maxExclusive().z()) * 0.5);
            if (p == null) continue;
            String text = n.name() + (extra != null && id.equals(active) ? "  " + extra : "");
            float tw = ImGui.calcTextSizeX(text) + 12 * sc;
            float x = (float) p[0] - tw * 0.5f, y = (float) p[1] - 14 * sc - tagH;
            boolean hidden = false;
            for (int tries = 0; tries <= 3; tries++) {
                boolean hit = false;
                for (float[] r : placed) if (x < r[2] && x + tw > r[0] && y < r[3] && y + tagH > r[1]) hit = true;
                if (!hit) break;
                if (tries == 3) hidden = true;
                y -= tagH + 4 * sc;
            }
            if (hidden) continue;
            placed.add(new float[] {x, y, x + tw, y + tagH});
            dl.addRectFilled(x, y, x + tw, y + tagH, Theme.u32(0x14161a, 0.82f), 4 * sc);
            dl.addText(x + 6 * sc, y + (tagH - fh) * 0.5f, Theme.u32(Theme.ACCENT), text);
        }
        ImGui.popFont();
    }

    private static Box firstBox(WorldBoxes wb, UUID objectId) {
        List<Box> b = wb.objectBoxes(objectId);
        return b.isEmpty() ? null : b.get(0);
    }
}
