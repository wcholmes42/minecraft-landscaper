package com.wcholmes.landscaper.server;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.Landscaper;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Server-side event handler for player lifecycle and cleanup.
 */
@Mod.EventBusSubscriber(modid = Landscaper.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called when a player logs out.
     * Cleans up per-player settings to prevent memory leaks.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            PlayerSettings.clear(event.getEntity().getUUID());

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cleaned up settings for player: {}", event.getEntity().getName().getString());
            }
        }
    }

    /**
     * Called when the server is stopping.
     * Clears all temporary data.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // PlayerSettings cleanup happens automatically
        LOGGER.info("Server stopping");
    }
}
