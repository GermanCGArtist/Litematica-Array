package dev.schemarray.editor;

import dev.cg.uihost.UiHost;
import dev.cg.uihost.UiLayer;
import dev.schemarray.editor.cam.EditorCamera;
import dev.schemarray.editor.view.EditorChrome;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * The editor's cg-uihost layer: draws the editor chrome and routes input while editor mode is on.
 * <ul>
 *   <li>Mouse: always ours (so a click can never re-grab the mouse or attack), except while a screen such as chat
 *       is open and the pointer is not over an ImGui window. Presses that land on an ImGui window belong to
 *       ImGui; presses on the viewport drive the {@link EditorCamera}.</li>
 *   <li>Keyboard: everything is blocked from vanilla (movement, attack/use, hotbar, Esc) except F1 to F12, the
 *       editor toggle, chat and command, so chat and the debug keys keep working. While a screen is open it gets
 *       every key.</li>
 * </ul>
 */
public final class EditorInputLayer implements UiLayer {
    @Override public String id() { return "schemarray.editor"; }
    @Override public int order() { return 100; }
    @Override public boolean visible() { return EditorMode.isActive(); }

    @Override
    public void draw() {
        EditorChrome.draw();
    }

    // ------------------------------------------------------------------ keyboard

    @Override
    public boolean wantsKey(int key, int scancode, int action, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return UiHost.imguiWantsKeyboard();       // chat: it gets the keys
        if (UiHost.imguiWantsTextInput()) return true;                    // a text field is active
        return !EditorMode.passesToVanilla(mc, key, scancode, mods);
    }

    @Override
    public void onKey(int key, int scancode, int action, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || action != GLFW.GLFW_PRESS) return;
        if (UiHost.imguiWantsKeyboard()) return;                          // typing in a field, or menu navigation
        if (key == GLFW.GLFW_KEY_F || key == GLFW.GLFW_KEY_KP_DECIMAL) {
            EditorCamera.get().frame(mc.player.getBoundingBox());         // WP 1.x: the selection; for now the player
        }
    }

    // ------------------------------------------------------------------ mouse

    @Override
    public boolean wantsMouse() {
        // with chat open the mouse belongs to chat, unless the pointer is over an editor window
        return Minecraft.getInstance().screen == null || UiHost.imguiWantsMouse();
    }

    @Override
    public void onMouseButton(int button, int action, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null && action == GLFW.GLFW_PRESS) return;
        EditorCamera.get().onMouseButton(mc, button, action, UiHost.imguiWantsMouse());
    }

    @Override
    public void onMouseMove(double x, double y) {
        EditorCamera.get().onMouseMove(Minecraft.getInstance(), x, y);
    }

    @Override
    public void onScroll(double dx, double dy) {
        if (UiHost.imguiWantsMouse()) return;                              // over a window: it scrolls
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        EditorCamera.get().onScroll(mc, dy);
    }
}
