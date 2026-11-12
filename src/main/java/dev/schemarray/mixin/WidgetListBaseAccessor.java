package dev.schemarray.mixin;

import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WidgetListBase.class, remap = false)
public interface WidgetListBaseAccessor {
    @Accessor(value = "browserEntryWidth", remap = false)
    int schemarray$entryWidth();

    @Accessor(value = "browserEntryHeight", remap = false)
    int schemarray$entryHeight();
}
