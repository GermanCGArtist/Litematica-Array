package dev.schemarray;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** The only place that touches Minecraft APIs which differ between versions (current screen, mouse button state). */
public final class Compat {
    private Compat() {}

    /** The screen that is open right now, or null. */
    public static Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    /**
     * Whether the left mouse button is held right now. Asks GLFW: Minecraft's MouseHandler.isLeftPressed is not
     * updated while a screen handles the click, so it stays false inside menus.
     */
    public static boolean leftDown() {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }
}
