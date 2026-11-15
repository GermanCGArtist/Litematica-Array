package dev.schemarray;

import dev.schemarray.editor.DemoLayer;
import dev.schemarray.editor.mc.Reconciler;
import net.fabricmc.api.ClientModInitializer;
import dev.schemarray.shape.mc.ShapePreview;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SchemArrayMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DemoLayer.register(); // TODO remove in WP 0.2
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return;
        ThumbnailCompat.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ShapePreview.clear();
            Reconciler.onDisconnect();   // saves the open projects
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                Reconciler.tick(mc);   // before ArraySync: it decides which arrays the editor owns
            } catch (Throwable t) {
                System.out.println("[SchematicArray] editor failed: " + t);
            }
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
