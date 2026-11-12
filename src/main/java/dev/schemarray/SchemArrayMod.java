package dev.schemarray;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SchemArrayMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                ArraySync.tick(mc);
            } catch (Throwable t) {
                System.out.println("[SchematicArray] sync failed: " + t);
            }
        });
    }
}
