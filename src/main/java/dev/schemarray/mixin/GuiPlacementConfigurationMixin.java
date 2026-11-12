package dev.schemarray.mixin;

import dev.schemarray.ArraySync;
import fi.dy.masa.litematica.gui.GuiPlacementConfiguration;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Litematica's placement configuration (M, Configure placement, Configure): array rows right under the Rotate
 * button, laid out like the Placement origin rows. Mirror and the buttons below it move down to make room.
 *
 *   Array   Copies     Gap
 *   X: [3    ][+-]  [0    ][+-]
 *   Y: [1    ][+-]  [0    ][+-]
 *   Z: [1    ][+-]  [0    ][+-]
 *
 * The +- buttons work like Litematica's: left click +1, right click -1, Shift x8, Alt x4.
 */
@Mixin(value = GuiPlacementConfiguration.class, remap = false)
abstract class GuiPlacementConfigurationMixin {
    private static final int ROW = 18;
    private static final int BLOCK = 12 + 3 * ROW + 2;

    @Inject(method = "initGui", at = @At("TAIL"), remap = false)
    private void schemarray$addArrayRows(CallbackInfo ci) {
        GuiBase gui = (GuiBase) (Object) this;
        SchematicPlacement shown = ((GuiPlacementConfiguration) (Object) this).placement;
        if (shown == null) return;
        SchematicPlacement original = ArraySync.original(shown);

        // the right-hand column: Litematica puts it 130 from the right edge, 120 wide
        int colX = gui.getScreenWidth() - 130;
        List<ButtonBase> buttons = ((GuiBaseAccessor) gui).schemarray$buttons();
        List<ButtonBase> col = new ArrayList<>();
        for (ButtonBase b : buttons) if (b.getX() == colX && b.getWidth() == 120) col.add(b);
        col.sort(Comparator.comparingInt(ButtonBase::getY));
        // ... Move to player, Rotate, Mirror, Reset sub-regions: Rotate is the third from the bottom
        if (col.size() < 3) return;
        int rotateY = col.get(col.size() - 3).getY();
        for (ButtonBase b : buttons) {
            if (b.getX() >= colX && b.getX() < colX + 120 && b.getY() > rotateY) b.setY(b.getY() + BLOCK);
        }

        int white = 0xFFFFFFFF;
        int fw = 34, fieldX = colX + 12, gapX = colX + 68;
        int y = rotateY + 23;
        gui.addLabel(colX, y, 30, 10, white, ArraySync.isCopy(shown) ? "Array*" : "Array");
        gui.addLabel(fieldX + 6, y, 40, 10, white, "Copies");
        gui.addLabel(gapX + 6, y, 40, 10, white, "Gap");
        y += 12;

        int[] counts = ArraySync.counts(original), gaps = ArraySync.gaps(original);
        var font = Minecraft.getInstance().font;
        String[] axis = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            int ax = i;
            gui.addLabel(colX, y + 1, 10, 14, white, axis[i] + ":");

            GuiTextFieldInteger c = new GuiTextFieldInteger(fieldX, y + 1, fw, 14, font);
            c.setValueWrapper(String.valueOf(counts[i]));
            gui.addTextField(c, f -> { apply(original, false, ax, f.getValueWrapper()); return false; }, TextFieldType.INTEGER);
            ButtonGeneric cb = new ButtonGeneric(fieldX + fw + 2, y, Icons.BUTTON_PLUS_MINUS_16,
                    "Copies along " + axis[i] + ": left click +1, right click -1 (Shift x8, Alt x4).", "Negative = the other way. Turns with the placement.");
            gui.addButton(cb, (b, mouse) -> nudge(original, false, ax, mouse, c));

            GuiTextFieldInteger g = new GuiTextFieldInteger(gapX, y + 1, fw, 14, font);
            g.setValueWrapper(String.valueOf(gaps[i]));
            gui.addTextField(g, f -> { apply(original, true, ax, f.getValueWrapper()); return false; }, TextFieldType.INTEGER);
            ButtonGeneric gb = new ButtonGeneric(gapX + fw + 2, y, Icons.BUTTON_PLUS_MINUS_16,
                    "Blocks between the copies along " + axis[i] + ": left click +1, right click -1 (Shift x8, Alt x4).", "0 = touching, negative = overlap.");
            gui.addButton(gb, (b, mouse) -> nudge(original, true, ax, mouse, g));
            y += ROW;
        }
    }

    private static void nudge(SchematicPlacement original, boolean gap, int axis, int mouse, GuiTextFieldInteger field) {
        int d = (mouse == 1 ? -1 : 1) * (GuiBase.isShiftDown() ? 8 : 1) * (GuiBase.isAltDown() ? 4 : 1);
        int v = gap ? ArraySync.gaps(original)[axis] : ArraySync.counts(original)[axis];
        int nv = v + d;
        if (!gap) {
            // 1 and -1 both mean "no copies": step over 0 and -1
            if (v >= 1 && nv < 1) nv -= 2;
            else if (v <= -2 && nv > -2) nv += 2;
            if (nv == -1) nv = 1;
        }
        if (set(original, gap, axis, nv)) field.setValueWrapper(String.valueOf(gap ? ArraySync.gaps(original)[axis] : ArraySync.counts(original)[axis]));
    }

    private static void apply(SchematicPlacement original, boolean gap, int axis, String text) {
        int v;
        try {
            v = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return;   // "-" or empty while typing
        }
        set(original, gap, axis, v);
    }

    private static boolean set(SchematicPlacement original, boolean gap, int axis, int v) {
        if (!gap) {
            int[] c = ArraySync.counts(original);
            c[axis] = v == 0 ? 1 : v;
            long n = (long) Math.abs(c[0]) * Math.abs(c[1]) * Math.abs(c[2]);
            if (n - 1 > ArraySync.MAX_COPIES) return false;
        } else if (Math.abs(v) > 10000) return false;
        int[] now = gap ? ArraySync.gaps(original) : ArraySync.counts(original);
        if (now[axis] == v) return true;
        ArraySync.set(original, gap, axis, v);
        return true;
    }
}
