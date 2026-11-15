package dev.schemarray.editor.view;

import dev.cg.uihost.Theme;
import dev.cg.uihost.UiHost;
import dev.schemarray.editor.EditorMode;
import dev.schemarray.editor.cam.EditorCamera;
import dev.schemarray.editor.cam.SpectatorFollow;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * Placeholder editor chrome (WP 0.2): the thin top toolbar with only the "EDITOR  F6" chip, the status bar with the
 * navigation hints, and the fly HUD. Metrics and colours follow DESIGN.md sections 3, 5 and 6. WP 1.4 replaces this
 * with the real toolbar and status bar.
 */
public final class EditorChrome {
    private static final int FIXED = ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoCollapse;

    private record Hint(String[] caps, String verb) {
        static Hint of(String verb, String... caps) { return new Hint(caps, verb); }
    }

    private static final List<Hint> HINTS_NAV = List.of(
            Hint.of("orbit", "MMB"),
            Hint.of("pan", "Shift", "MMB"),
            Hint.of("zoom", "Wheel"),
            Hint.of("fly", "hold RMB"),
            Hint.of("frame", "F"),
            Hint.of("leave", "F6"));

    private static final List<Hint> HINTS_FLY = List.of(
            Hint.of("move", "W", "A", "S", "D"),
            Hint.of("down/up", "Q", "E"),
            Hint.of("fast", "Shift"),
            Hint.of("speed", "Wheel"),
            Hint.of("stop", "release RMB"));

    private EditorChrome() {}

    public static void draw() {
        float s = UiHost.scale();
        ImGuiViewport vp = ImGui.getMainViewport();
        float toolbarBottom = drawToolbar(vp, s);
        drawStatusBar(vp, s);
        drawFlyHud(vp, s, toolbarBottom);
    }

    // ------------------------------------------------------------------ toolbar

    /** Draws the toolbar and returns its bottom edge (screen y). */
    private static float drawToolbar(ImGuiViewport vp, float s) {
        float pad = 4 * s, chipH = 28 * s, h = chipH + 2 * pad, top = vp.getPosY() + 10 * s;
        Theme.push(UiHost.theme().small());
        String label = "EDITOR";
        float labelW = ImGui.calcTextSizeX(label);
        ImGui.popFont();
        Theme.push(UiHost.theme().monoS());
        float capW = ImGui.calcTextSizeX("F6") + 10 * s;
        ImGui.popFont();
        float chipW = 8 * s + labelW + 6 * s + capW + 8 * s;
        float w = chipW + 2 * pad;
        ImGui.setNextWindowPos(vp.getPosX() + (vp.getSizeX() - w) * 0.5f, top);
        ImGui.setNextWindowSize(w, h);

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, pad, pad);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 8 * s);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, Theme.u32(Theme.PANEL, 0.94f));
        ImGui.pushStyleColor(ImGuiCol.Border, Theme.u32(Theme.LINE));
        if (ImGui.begin("##la_toolbar", FIXED | ImGuiWindowFlags.NoFocusOnAppearing | ImGuiWindowFlags.NoNav)) {
            float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
            boolean clicked = ImGui.invisibleButton("##chip", chipW, chipH);   // true on release, so ImGui sees the whole click
            boolean hot = ImGui.isItemHovered();
            ImDrawList dl = ImGui.getWindowDrawList();
            dl.addRectFilled(x, y, x + chipW, y + chipH, Theme.u32(Theme.SEL, hot ? 1f : 0.85f), 6 * s);
            Theme.push(UiHost.theme().small());
            float fh = ImGui.getFontSize();
            dl.addText(x + 8 * s, y + (chipH - fh) * 0.5f, Theme.u32(Theme.ACCENT), label);
            ImGui.popFont();
            keyCap(dl, x + 8 * s + labelW + 6 * s, y + (chipH - 18 * s) * 0.5f, "F6", s, Theme.DIM);
            if (hot) ImGui.setTooltip("Leave the editor (F6)");
            if (clicked) EditorMode.exit(Minecraft.getInstance(), true);
        }
        ImGui.end();
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(3);
        return top + h;
    }

    // ------------------------------------------------------------------ status bar

    private static void drawStatusBar(ImGuiViewport vp, float s) {
        float h = 24 * s;
        ImGui.setNextWindowPos(vp.getPosX(), vp.getPosY() + vp.getSizeY() - h);
        ImGui.setNextWindowSize(vp.getSizeX(), h);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10 * s, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, Theme.u32(Theme.PANEL2));
        if (ImGui.begin("##la_status", FIXED | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.NoInputs)) {
            ImDrawList dl = ImGui.getWindowDrawList();
            float x0 = ImGui.getWindowPosX(), y0 = ImGui.getWindowPosY(), w = ImGui.getWindowWidth();
            dl.addLine(x0, y0 + 0.5f, x0 + w, y0 + 0.5f, Theme.u32(Theme.LINE), 1f);   // top rule

            // right: selection summary (nothing can be selected yet)
            Theme.push(UiHost.theme().small());
            String sel = "Nothing selected";
            float selW = ImGui.calcTextSizeX(sel), fh = ImGui.getFontSize();
            dl.addText(x0 + w - 10 * s - selW, y0 + (h - fh) * 0.5f, Theme.u32(Theme.FG), sel);

            // left: hints for the current mode, dropped from the end when space runs out
            List<Hint> hints = EditorCamera.get().isFlying() ? HINTS_FLY : HINTS_NAV;
            float limit = x0 + w - 10 * s - selW - 14 * s;
            float x = x0 + 10 * s;
            for (Hint hint : hints) {
                float need = hintWidth(hint, s);
                if (x + need > limit) break;
                x = drawHint(dl, hint, x, y0, h, s) + 10 * s;
            }
            ImGui.popFont();
        }
        ImGui.end();
        ImGui.popStyleColor();
        ImGui.popStyleVar(3);
    }

    /** Width of a hint; the small font must be current. */
    private static float hintWidth(Hint hint, float s) {
        float w = 0;
        for (String cap : hint.caps()) w += capWidth(cap, s) + 3 * s;
        return w + 3 * s + ImGui.calcTextSizeX(hint.verb());
    }

    /** Draws caps + verb, small font current; returns the right edge. */
    private static float drawHint(ImDrawList dl, Hint hint, float x, float y0, float h, float s) {
        float capH = 18 * s;
        for (String cap : hint.caps()) {
            x = keyCap(dl, x, y0 + (h - capH) * 0.5f, cap, s, Theme.DIM) + 3 * s;
        }
        x += 3 * s;
        float fh = ImGui.getFontSize();
        dl.addText(x, y0 + (h - fh) * 0.5f, Theme.u32(Theme.DIM), hint.verb());
        return x + ImGui.calcTextSizeX(hint.verb());
    }

    private static float capWidth(String text, float s) {
        Theme.push(UiHost.theme().monoS());
        float w = ImGui.calcTextSizeX(text) + 10 * s;
        ImGui.popFont();
        return w;
    }

    /** A key cap (DESIGN 3.2: 18 high, pad-x 5, rounding 4, CHIP fill, LINE border, monoS). Returns its right edge. */
    private static float keyCap(ImDrawList dl, float x, float y, String text, float s, int textRgb) {
        Theme.push(UiHost.theme().monoS());
        float w = ImGui.calcTextSizeX(text) + 10 * s, h = 18 * s, fh = ImGui.getFontSize();
        dl.addRectFilled(x, y, x + w, y + h, Theme.u32(Theme.CHIP), 4 * s);
        dl.addRect(x, y, x + w, y + h, Theme.u32(Theme.LINE), 4 * s);
        dl.addText(x + 5 * s, y + (h - fh) * 0.5f, Theme.u32(textRgb), text);
        ImGui.popFont();
        return x + w;
    }

    // ------------------------------------------------------------------ fly HUD

    /** Fly pill under the toolbar: a speed bar (log scale 0.2x to 8x) and the multiplier. Never takes input. */
    private static void drawFlyHud(ImGuiViewport vp, float s, float toolbarBottom) {
        EditorCamera cam = EditorCamera.get();
        if (!cam.isFlying()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean loading = SpectatorFollow.chunksLoading(mc, cam);
        String speed = String.format(Locale.ROOT, "%.1f×", cam.speed());
        String tail = loading ? "loading chunks…" : null;

        Theme.push(UiHost.theme().mono());
        float fh = ImGui.getFontSize();
        float flyW = ImGui.calcTextSizeX("Fly"), speedW = ImGui.calcTextSizeX(speed);
        float tailW = tail == null ? 0 : ImGui.calcTextSizeX(tail) + 10 * s;
        float barW = 60 * s, barH = 5 * s, gap = 10 * s, padX = 14 * s, h = 30 * s;
        float w = padX + flyW + gap + barW + gap + speedW + tailW + padX;
        float x = vp.getPosX() + (vp.getSizeX() - w) * 0.5f, y = toolbarBottom + 10 * s;

        ImDrawList dl = ImGui.getForegroundDrawList();
        dl.addRectFilled(x, y, x + w, y + h, Theme.u32(0x14161a, 0.82f), h * 0.5f);
        dl.addRect(x, y, x + w, y + h, Theme.u32(0xffffff, 0.10f), h * 0.5f);
        float tx = x + padX, ty = y + (h - fh) * 0.5f;
        dl.addText(tx, ty, Theme.u32(Theme.FG), "Fly");
        tx += flyW + gap;
        float by = y + (h - barH) * 0.5f;
        dl.addRectFilled(tx, by, tx + barW, by + barH, Theme.u32(Theme.TRACK), barH * 0.5f);
        double t = Math.log(cam.speed() / EditorCamera.SPEED_MIN) / Math.log(EditorCamera.SPEED_MAX / EditorCamera.SPEED_MIN);
        float fill = (float) Math.max(0.03, Math.min(1.0, t)) * barW;
        dl.addRectFilled(tx, by, tx + fill, by + barH, Theme.u32(Theme.ACCENT), barH * 0.5f);
        tx += barW + gap;
        dl.addText(tx, ty, Theme.u32(Theme.ACCENT), speed);
        if (tail != null) dl.addText(tx + speedW + 10 * s, ty, Theme.u32(Theme.DIM), tail);
        ImGui.popFont();
    }
}
