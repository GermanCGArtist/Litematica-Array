package dev.schemarray.shape.gui;

import dev.schemarray.shape.VoxelResult;
import fi.dy.masa.malilib.render.GuiContext;

/**
 * The rotatable 3D view of the shape inside the generator screen. The screen owns one instance, tells it the area
 * it covers each frame and forwards mouse events that land in it. Work package D.
 */
public final class ShapeThumbnail {
    /** New shape to show (null = empty). Rebuilds the cached mesh; cheap to call only when the result changes. */
    public void setResult(VoxelResult result) {
        throw new UnsupportedOperationException("WP D");
    }

    /** Draws the view into the rectangle (screen coordinates), with a dark background and a 1px border. */
    public void render(GuiContext ctx, int x, int y, int width, int height, int mouseX, int mouseY) {
        throw new UnsupportedOperationException("WP D");
    }

    /** Left button pressed inside the view: starts orbiting. Returns true if taken. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        throw new UnsupportedOperationException("WP D");
    }

    public boolean mouseDragged(double mouseX, double mouseY, double dragX, double dragY) {
        throw new UnsupportedOperationException("WP D");
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        throw new UnsupportedOperationException("WP D");
    }

    /** Wheel inside the view: zoom. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        throw new UnsupportedOperationException("WP D");
    }

    /** Back to the default angle and zoom (fit the whole shape). */
    public void resetView() {
        throw new UnsupportedOperationException("WP D");
    }
}
