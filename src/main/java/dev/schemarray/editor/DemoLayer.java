package dev.schemarray.editor;

import dev.cg.uihost.UiHost;
import dev.cg.uihost.UiLayer;
import imgui.ImGui;
import imgui.type.ImBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

// TODO remove in WP 0.2
/** Test-only layer: Ctrl+F6 toggles ImGui's demo window (the cursor must be free, e.g. with a screen open). */
public final class DemoLayer implements UiLayer {
    private final ImBoolean show = new ImBoolean(false);
    private boolean wasDown;

    public static void register() {
        DemoLayer layer = new DemoLayer();
        UiHost.register(layer);
        ClientTickEvents.END_CLIENT_TICK.register(layer::tick);
    }

    private void tick(Minecraft mc) {
        if (mc.getWindow() == null) return;
        long w = mc.getWindow().handle();
        boolean down = GLFW.glfwGetKey(w, GLFW.GLFW_KEY_F6) == GLFW.GLFW_PRESS
                && (GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
        if (down && !wasDown) show.set(!show.get());
        wasDown = down;
    }

    @Override public String id() { return "schemarray.demo"; }
    @Override public int order() { return 1000; }
    @Override public boolean visible() { return show.get(); }

    @Override
    public void draw() {
        ImGui.showDemoWindow(show);
    }
}
