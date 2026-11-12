package dev.schemarray;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.sdl.SDLMouse;

/** The only place that touches Minecraft APIs which differ between versions (current screen, mouse button state). */
public final class Compat {
    private Compat() {}

    /** The screen that is open right now, or null. */
    public static Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    /**
     * Whether the left mouse button is held right now. Minecraft 26.3 uses SDL instead of GLFW; SDL_GetMouseState
     * reports SDL's own button state, which Minecraft's MouseHandler.isLeftPressed is not (it stays false while a
     * screen handles the click).
     */
    public static boolean leftDown() {
        return (SDLMouse.SDL_GetMouseState(null, null) & SDLMouse.SDL_BUTTON_LMASK) != 0;
    }
}
