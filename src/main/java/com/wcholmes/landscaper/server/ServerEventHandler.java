package com.wcholmes.landscaper.server;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.Landscaper;
import com.wcholmes.landscaper.common.config.PlayerConfig;
import com.wcholmes.landscaper.common.network.PacketRateLimiter;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Server-side event handler for player lifecycle and cleanup.
 *
 * <p>Handles:
 * <ul>
 *   <li>Player disconnect cleanup (removes per-player config and rate limit data)</li>
 *   <li>Server shutdown cleanup (clears all temporary data)</li>
 * </ul>
 *
 * @since 2.3.0
 */
@Mod.EventBusSubscriber(modid = Landscaper.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called when a player logs out.
     * Cleans up per-player configuration and rate limit data to prevent memory leaks.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            PlayerConfig.removePlayerSettings(event.getEntity().getUUID());
            PacketRateLimiter.clearPlayer(event.getEntity().getUUID());

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cleaned up data for player: {}", event.getEntity().getName().getString());
            }
        }
    }

    /**
     * Called when the server is stopping.
     * Clears all temporary data.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PlayerConfig.clearAll();
        PacketRateLimiter.clearAll();
        LOGGER.info("Cleared all player data on server shutdown");
    }
}
