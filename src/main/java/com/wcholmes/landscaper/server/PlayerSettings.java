package com.wcholmes.landscaper.server;

import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.item.NaturalizationMode;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores per-player settings for landscaper commands
 */
public class PlayerSettings {
    private static final Map<UUID, PlayerSettings> PLAYER_SETTINGS = new HashMap<>();

    private NaturalizationMode mode;
    private int radius;

    private PlayerSettings() {
        this.mode = NaturalizationMode.GRASS_ONLY; // Default mode
        this.radius = NaturalizationConfig.getRadius(); // Default from config
    }

    public static PlayerSettings get(ServerPlayer player) {
        return PLAYER_SETTINGS.computeIfAbsent(player.getUUID(), uuid -> new PlayerSettings());
    }

    public static void clear(UUID playerUUID) {
        PLAYER_SETTINGS.remove(playerUUID);
    }

    public NaturalizationMode getMode() {
        return mode;
    }

    public void setMode(NaturalizationMode mode) {
        this.mode = mode;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(1, Math.min(50, radius)); // Clamp to valid range
    }
}
