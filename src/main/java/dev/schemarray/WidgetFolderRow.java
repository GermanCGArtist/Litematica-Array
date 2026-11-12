package dev.schemarray;

import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * A folder row in Litematica's placement list: open/closed arrow, name and count, and on the right the buttons
 * Visible/Hidden, +, Rename and Delete. A click anywhere else on the row opens or closes the folder (see DragDrop: it is done on release, so the row can also be dragged).
 */
public final class WidgetFolderRow extends WidgetListEntryBase<Folders.Node> {
    private final boolean isOdd;

    public WidgetFolderRow(int x, int y, int width, int height, boolean isOdd, Folders.Node node, int listIndex) {
        super(x, y, width, height, node, listIndex);
        this.isOdd = isOdd;
        int bx = x + width - 2, by = y + 1;

        ButtonGeneric del = new ButtonGeneric(bx, by, -1, true, "Delete");
        del.setHoverStrings("Hold Shift and click to delete the folder.", "Its placements and subfolders move up; no placement is removed.");
        addButton(del, (b, mouse) -> {
            if (!GuiBase.isShiftDown()) return;
            Folders.delete(node.id);
            ListRefresh.later();
        });
        bx = del.getX() - 1;

        ButtonGeneric rename = new ButtonGeneric(bx, by, -1, true, "Rename");
        addButton(rename, (b, mouse) -> GuiBase.openGui(new GuiTextInput(64, "Rename folder", node.name, Minecraft.getInstance().screen, s -> {
            String name = s.trim();
            if (!name.isEmpty()) Folders.rename(node.id, name);
        })));
        bx = rename.getX() - 1;

        ButtonGeneric add = new ButtonGeneric(bx, by, -1, true, "+");
        add.setHoverStrings("New subfolder");
        addButton(add, (b, mouse) -> GuiBase.openGui(new GuiTextInput(64, "New subfolder", "", Minecraft.getInstance().screen, s -> {
            String name = s.trim();
            if (!name.isEmpty()) Folders.create(name, node.id);
        })));
        bx = add.getX() - 1;

        ButtonGeneric vis = new ButtonGeneric(bx, by, -1, true, node.hidden ? "§cHidden" : "§aVisible");
        vis.setHoverStrings("Hidden switches the rendering of everything in this folder off.", "Placements you switched off yourself stay off when shown again.");
        addButton(vis, (b, mouse) -> {
            Folders.toggleHidden(node.id);
            ListRefresh.later();
        });
    }

    @Override
    public boolean canSelectAt(MouseButtonEvent event) {
        return false;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        int color = isMouseOver(mouseX, mouseY) ? 0xA0707070 : isOdd ? 0xA0101010 : 0xA0303030;
        RenderUtils.drawRect(ctx, x, y, width, height, color);
        boolean colored = entry.color >= 0 && entry.color < Folders.COLORS.length;
        int rgb = colored ? Folders.COLORS[entry.color] : 0;
        if (colored) RenderUtils.drawRect(ctx, x, y, width, height, 0x50000000 | rgb);
        int tx = x + 4;
        drawString(ctx, tx, y + 7, 0xFFFFFFFF, entry.open ? "v" : ">");
        Icons.FILE_ICON_DIR.renderAt(ctx, tx + 10, y + 5, zLevel, false, false);
        drawString(ctx, tx + 28, y + 7, colored ? 0xFF000000 | rgb : entry.hidden ? 0xFF909090 : 0xFFFFFFFF, entry.name + " (" + entry.count + ")");
        super.render(ctx, mouseX, mouseY, selected);
    }
}
