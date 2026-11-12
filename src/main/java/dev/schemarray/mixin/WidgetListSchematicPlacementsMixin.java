package dev.schemarray.mixin;

import dev.schemarray.ArraySync;
import fi.dy.masa.litematica.gui.widgets.WidgetListSchematicPlacements;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Litematica's placement list shows only the originals; their array copies are managed by the original. */
@Mixin(value = WidgetListSchematicPlacements.class, remap = false)
abstract class WidgetListSchematicPlacementsMixin {
    @Inject(method = "getAllEntries", at = @At("RETURN"), cancellable = true, remap = false)
    private void schemarray$hideCopies(CallbackInfoReturnable<Collection<SchematicPlacement>> cir) {
        Collection<SchematicPlacement> all = cir.getReturnValue();
        if (all == null) return;
        List<SchematicPlacement> out = new ArrayList<>(all.size());
        for (SchematicPlacement p : all) if (!ArraySync.isCopy(p)) out.add(p);
        if (out.size() != all.size()) cir.setReturnValue(out);
    }
}
