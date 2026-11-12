package dev.schemarray;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Small screen to pick the folder for a placement: "(top level)" and every folder as an indented tree. */
public final class MoveScreen extends GuiBase {
    private final SchematicPlacement placement;
    private final Screen back;

    public MoveScreen(Screen back, SchematicPlacement placement) {
        this.back = back;
        this.placement = placement;
        setParent(back);
        setTitle("Move " + placement.getName() + " to");
    }

    @Override
    public void initGui() {
        super.initGui();
        var font = Minecraft.getInstance().font;
        String current = Folders.folderOf(placement);
        List<Folders.Node> rows = new ArrayList<>();
        rows.add(null);   // (top level)
        rows.addAll(Folders.tree());

        int top = 40, bottom = getScreenHeight() - 40;
        int x0 = 20, y = top;
        for (Folders.Node n : rows) {
            String id = n == null ? null : n.id;
            String label = n == null ? "(top level)" : n.name;
            int indent = n == null ? 0 : 12 * (n.depth + 1);
            if (y > bottom) { y = top; x0 += 200; }
            String shown = (Objects.equals(id, current) ? "§a" : "") + label;
            addButton(new ButtonGeneric(x0 + indent, y, font.width(label) + 14, 20, shown), (b, mouse) -> {
                Folders.move(placement, id);
                GuiBase.openGui(back);
            });
            y += 22;
        }
        addButton(new ButtonGeneric(20, getScreenHeight() - 30, font.width("Back") + 20, 20, "Back"), (b, mouse) -> GuiBase.openGui(back));
    }
}
