package dev.schemarray.mixin;

import dev.schemarray.ArraySync;
import dev.schemarray.MoveScreen;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Move button on every placement row, left of Configure: opens the screen that picks the folder. */
@Mixin(value = WidgetSchematicPlacement.class, remap = false)
abstract class WidgetSchematicPlacementMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void schemarray$addMove(CallbackInfo ci) {
        WidgetSchematicPlacement w = (WidgetSchematicPlacement) (Object) this;
        if (ArraySync.isCopy(w.placement)) return;
        ButtonGeneric move = new ButtonGeneric(w.buttonsStartX, w.getY() + 1, -1, true, "Move");
        ((WidgetContainerAccessor) w).schemarray$addButton(move, (b, mouse) ->
                GuiBase.openGui(new MoveScreen(Minecraft.getInstance().screen, w.placement)));
        w.buttonsStartX = move.getX() - 1;
    }
}
