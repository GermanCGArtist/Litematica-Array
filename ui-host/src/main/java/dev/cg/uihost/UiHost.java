package dev.cg.uihost;

/** Public entry point of cg-uihost: register layers, read the theme and the UI scale. */
public final class UiHost {
    private UiHost() {}

    /** Adds a layer. A layer with the same id replaces the earlier one. Safe to call before ImGui is initialised. */
    public static void register(UiLayer layer) {
        HostCore.register(layer);
    }

    /** The shared colour tokens and fonts. */
    public static Theme theme() {
        return Theme.INSTANCE;
    }

    /** The UI scale (1.0 = 100 %). */
    public static float scale() {
        return HostCore.scale();
    }

    /** Sets the UI scale (clamped to 0.5..4); fonts and style are rebuilt on the next frame. */
    public static void setScale(float scale) {
        HostCore.setScale(scale);
    }

    /** Whether ImGui is up (natives loaded, context created). False until the first frame with a visible layer. */
    public static boolean isInitialized() {
        return HostCore.isInitialized();
    }

    /** ImGui's own keyboard-capture flag, for the default {@link UiLayer#wantsKeyboard()}. */
    public static boolean imguiWantsKeyboard() {
        return HostCore.imguiWantsKeyboard();
    }

    /** ImGui's own mouse-capture flag, for the default {@link UiLayer#wantsMouse()}. */
    public static boolean imguiWantsMouse() {
        return HostCore.imguiWantsMouse();
    }
}
