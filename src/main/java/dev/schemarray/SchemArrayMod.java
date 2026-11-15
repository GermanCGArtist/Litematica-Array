package dev.schemarray;

import dev.schemarray.editor.EditorMode;
import net.fabricmc.api.ClientModInitializer;
import dev.schemarray.shape.mc.ShapePreview;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SchemArrayMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EditorMode.init();   // F6 editor mode; needs no Litematica
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return;
        ThumbnailCompat.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ShapePreview.clear());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                ArraySync.tick(mc);
            } catch (Throwable t) {
                System.out.println("[SchematicArray] sync failed: " + t);
            }
            try {
                Folders.tick(mc);
            } catch (Throwable t) {
                System.out.println("[SchematicArray] folders failed: " + t);
            }
        });
    }
}
