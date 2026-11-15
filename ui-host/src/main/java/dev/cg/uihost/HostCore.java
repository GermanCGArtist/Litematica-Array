package dev.cg.uihost;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.internal.ImGuiContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMonitorCallback;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Internal: ImGui lifecycle, frame loop and input routing. Called by the mixins in {@code dev.cg.uihost.mixin}
 * and by {@link UiHost}; not API. Init is lazy (first frame with a visible layer); any failure disables the host
 * with one warning and never crashes the game.
 */
public final class HostCore {
    private static final Logger LOG = LoggerFactory.getLogger("cg-uihost");

    private static final List<UiLayer> LAYERS = new CopyOnWriteArrayList<>();
    private static final Set<String> FAILED_LAYERS = new HashSet<>();

    private static boolean initialized;
    private static boolean disabled;
    private static float scale = 1.0f;
    private static ImGuiContext ctx;
    private static ImGuiImplGlfw glfw;
    private static ImGuiImplGl3 gl3;

    private HostCore() {}

    // ------------------------------------------------------------------ layers

    static void register(UiLayer layer) {
        if (layer == null) return;
        LAYERS.removeIf(l -> l.id().equals(layer.id()));
        LAYERS.add(layer);
        LAYERS.sort((a, b) -> Integer.compare(a.order(), b.order()));
    }

    /** The visible, not failed layers in draw order. */
    private static List<UiLayer> visibleLayers() {
        List<UiLayer> out = null;
        for (UiLayer l : LAYERS) {
            boolean vis;
            try {
                vis = l.visible() && !FAILED_LAYERS.contains(l.id());
            } catch (Throwable t) {
                fail(l, t);
                continue;
            }
            if (vis) {
                if (out == null) out = new ArrayList<>(4);
                out.add(l);
            }
        }
        return out == null ? List.of() : out;
    }

    private static void fail(UiLayer l, Throwable t) {
        if (FAILED_LAYERS.add(l.id())) LOG.warn("[cg-uihost] layer '{}' disabled after an error", l.id(), t);
    }

    // ------------------------------------------------------------------ state

    static float scale() {
        return scale;
    }

    static void setScale(float s) {
        scale = Math.max(0.5f, Math.min(4f, s));
    }

    static boolean isInitialized() {
        return initialized;
    }

    static boolean imguiWantsKeyboard() {
        if (!initialized) return false;
        ImGui.setCurrentContext(ctx);
        return ImGui.getIO().getWantCaptureKeyboard();
    }

    static boolean imguiWantsMouse() {
        if (!initialized) return false;
        ImGui.setCurrentContext(ctx);
        return ImGui.getIO().getWantCaptureMouse();
    }

    // ------------------------------------------------------------------ frame (render thread)

    /** Called at the head of {@code RenderSystem.flipFrame}. */
    public static void renderFrame() {
        if (disabled || LAYERS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Window win = mc.getWindow();
        if (win == null || win.isMinimized() || win.getWidth() <= 0 || win.getHeight() <= 0) return;
        List<UiLayer> layers = visibleLayers();
        if (layers.isEmpty()) return;
        try {
            if (!initialized && !init(win.handle())) return;
            render(mc, layers);
        } catch (Throwable t) {
            disabled = true;
            LOG.warn("[cg-uihost] ImGui host disabled after an error", t);
        }
    }

    private static boolean init(long windowHandle) {
        try {
            ImGuiNatives.load(FabricLoader.getInstance().getConfigDir().resolve("cg-uihost").resolve("natives"),
                    HostCore.class.getClassLoader());

            ctx = ImGui.createContext();
            ImGui.setCurrentContext(ctx);
            ImGuiIO io = ImGui.getIO();
            io.setIniFilename(null);
            io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);

            Theme.apply(ImGui.getStyle(), scale);
            try {
                Theme.loadFonts(io, scale);
            } catch (Throwable t) {
                LOG.warn("[cg-uihost] bundled fonts failed to load, using ImGui's default", t);
                io.getFonts().addFontDefault();
            }

            glfw = new ImGuiImplGlfw();
            GLFWMonitorCallback prevMonitor = GLFW.glfwSetMonitorCallback(null);
            glfw.init(windowHandle, false);
            GLFWMonitorCallback imguiMonitor = GLFW.glfwSetMonitorCallback(null);
            GLFW.glfwSetMonitorCallback((monitor, event) -> {
                if (prevMonitor != null) prevMonitor.invoke(monitor, event);
                if (imguiMonitor != null) imguiMonitor.invoke(monitor, event);
            });

            gl3 = new ImGuiImplGl3();
            gl3.init("#version 150");
            initialized = true;
            return true;
        } catch (Throwable t) {
            disabled = true;
            LOG.warn("[cg-uihost] ImGui host disabled: {}", t.toString());
            LOG.debug("[cg-uihost] init failure", t);
            return false;
        }
    }

    private static void refreshScale() {
        if (!Theme.needsFonts(scale)) return;
        ImGuiIO io = ImGui.getIO();
        io.getFonts().clear();
        Theme.loadFonts(io, scale);
        gl3.destroyFontsTexture();
        gl3.createFontsTexture();
        Theme.apply(ImGui.getStyle(), scale);
    }

    private static void render(Minecraft mc, List<UiLayer> layers) {
        ImGui.setCurrentContext(ctx);
        refreshScale();
        ImGuiIO io = ImGui.getIO();
        if (mc.mouseHandler.isMouseGrabbed()) {
            // Mouse look: ImGui must neither see the pointer nor touch the cursor mode.
            io.addConfigFlags(ImGuiConfigFlags.NoMouse | ImGuiConfigFlags.NoMouseCursorChange);
            io.addMousePosEvent(-Float.MAX_VALUE, -Float.MAX_VALUE);
        } else {
            io.removeConfigFlags(ImGuiConfigFlags.NoMouse | ImGuiConfigFlags.NoMouseCursorChange);
        }
        io.setWantSetMousePos(false);
        glfw.newFrame();
        gl3.newFrame();
        ImGui.newFrame();

        for (UiLayer l : layers) {
            if (FAILED_LAYERS.contains(l.id())) continue;
            try {
                l.draw();
            } catch (Throwable t) {
                fail(l, t);
            }
        }

        ImGui.render();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        gl3.renderDrawData(ImGui.getDrawData());
    }

    // ------------------------------------------------------------------ input routing

    private static boolean routing(long window, Minecraft mc) {
        return initialized && !disabled && mc != null && mc.getWindow() != null && window == mc.getWindow().handle()
                && !visibleLayers().isEmpty();
    }

    private static boolean anyWantsKeyboard() {
        for (UiLayer l : visibleLayers()) {
            try {
                if (l.wantsKeyboard()) return true;
            } catch (Throwable t) {
                fail(l, t);
            }
        }
        return false;
    }

    private static boolean anyWantsMouse() {
        for (UiLayer l : visibleLayers()) {
            try {
                if (l.wantsMouse()) return true;
            } catch (Throwable t) {
                fail(l, t);
            }
        }
        return false;
    }

    /** Returns true when vanilla must not see the event. Releases always reach ImGui so no key stays stuck. */
    public static boolean onKey(long window, int key, int scancode, int action, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (!routing(window, mc)) return false;
        boolean wants = anyWantsKeyboard();
        ImGui.setCurrentContext(ctx);
        if (wants || action == GLFW.GLFW_RELEASE) glfw.keyCallback(window, key, scancode, action, mods);
        return wants;
    }

    public static boolean onChar(long window, int codepoint) {
        Minecraft mc = Minecraft.getInstance();
        if (!routing(window, mc)) return false;
        if (!anyWantsKeyboard()) return false;
        ImGui.setCurrentContext(ctx);
        glfw.charCallback(window, codepoint);
        return true;
    }

    public static boolean onMouseMove(long window, double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        if (!routing(window, mc) || mc.mouseHandler.isMouseGrabbed()) return false;
        ImGui.setCurrentContext(ctx);
        glfw.cursorPosCallback(window, x, y);
        return anyWantsMouse();
    }

    public static boolean onMouseButton(long window, int button, int action, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (!routing(window, mc) || mc.mouseHandler.isMouseGrabbed()) return false;
        boolean wants = anyWantsMouse();
        ImGui.setCurrentContext(ctx);
        if (wants || action == GLFW.GLFW_RELEASE) glfw.mouseButtonCallback(window, button, action, mods);
        return wants;
    }

    public static boolean onMouseScroll(long window, double dx, double dy) {
        Minecraft mc = Minecraft.getInstance();
        if (!routing(window, mc) || mc.mouseHandler.isMouseGrabbed()) return false;
        boolean wants = anyWantsMouse();
        if (wants) {
            ImGui.setCurrentContext(ctx);
            glfw.scrollCallback(window, dx, dy);
        }
        return wants;
    }
}
