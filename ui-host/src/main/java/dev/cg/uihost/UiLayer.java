package dev.cg.uihost;

import imgui.ImGui;

/**
 * One piece of ImGui UI. Register it once with {@link UiHost#register(UiLayer)}; the host calls {@link #draw()}
 * every frame while {@link #visible()} is true, between {@code ImGui.newFrame()} and {@code ImGui.render()}, in
 * ascending {@link #order()}. All methods are called on the render thread.
 */
public interface UiLayer {
    /** Unique, stable id (for logs and de-duplication), e.g. {@code "schemarray.editor"}. */
    String id();

    /** Draw order: lower first, so higher orders are drawn on top. */
    int order();

    /** Whether the layer draws and takes part in input routing this frame. */
    boolean visible();

    /** Issues the ImGui calls for this frame. An exception disables the layer (logged once). */
    void draw();

    /** Whether this layer wants keyboard input. Default: ImGui says it wants it (a text field or focused window). */
    default boolean wantsKeyboard() {
        return UiHost.imguiWantsKeyboard();
    }

    /** Whether this layer wants mouse input. Default: ImGui says the pointer is over one of its windows. */
    default boolean wantsMouse() {
        return UiHost.imguiWantsMouse();
    }
}
