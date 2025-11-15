package com.wcholmes.landscaper.common.config;

import com.wcholmes.landscaper.common.config.NaturalizationConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player configuration storage for multiplayer servers.
 * Each player can have their own radius, vegetation density, messy edge settings, etc.
 */
public class PlayerConfig {

    // Per-player settings storage
    private static final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();

    public static class PlayerSettings {
        public int radius;
        public NaturalizationConfig.VegetationDensity vegetationDensity;
        public int messyEdgeExtension;
        public boolean consumeResources;
        public boolean overworldOnly;

        // New advanced settings
        public int maxFlattenHeight;
        public int erosionStrength;
        public double roughnessAmount;

        public PlayerSettings(int radius, NaturalizationConfig.VegetationDensity vegetationDensity,
                            int messyEdgeExtension, boolean consumeResources, boolean overworldOnly,
                            int maxFlattenHeight, int erosionStrength, double roughnessAmount) {
            this.radius = radius;
            this.vegetationDensity = vegetationDensity;
            this.messyEdgeExtension = messyEdgeExtension;
            this.consumeResources = consumeResources;
            this.overworldOnly = overworldOnly;
            this.maxFlattenHeight = maxFlattenHeight;
            this.erosionStrength = erosionStrength;
            this.roughnessAmount = roughnessAmount;
        }

        // Create from global defaults
        public static PlayerSettings fromDefaults() {
            return new PlayerSettings(
                NaturalizationConfig.getRadius(),
                NaturalizationConfig.getVegetationDensity(),
                NaturalizationConfig.getMessyEdgeExtension(),
                NaturalizationConfig.shouldConsumeResources(),
                NaturalizationConfig.isOverworldOnly(),
                NaturalizationConfig.getMaxFlattenHeight(),
                NaturalizationConfig.getErosionStrength(),
                NaturalizationConfig.getRoughnessAmount()
            );
        }
    }

    /**
     * Get settings for a specific player. If player doesn't have custom settings,
     * returns settings based on global config defaults.
     */
    public static PlayerSettings getPlayerSettings(UUID playerUuid) {
        return playerSettings.computeIfAbsent(playerUuid, uuid -> PlayerSettings.fromDefaults());
    }

    /**
     * Update settings for a specific player.
     */
    public static void updatePlayerSettings(UUID playerUuid, int radius,
                                           NaturalizationConfig.VegetationDensity vegetationDensity,
                                           int messyEdgeExtension,
                                           boolean consumeResources,
                                           boolean overworldOnly,
                                           int maxFlattenHeight,
                                           int erosionStrength,
                                           double roughnessAmount) {
        playerSettings.put(playerUuid, new PlayerSettings(
            radius, vegetationDensity, messyEdgeExtension, consumeResources, overworldOnly,
            maxFlattenHeight, erosionStrength, roughnessAmount
        ));
    }

    /**
     * Remove player settings (e.g., when player leaves server for cleanup).
     */
    public static void removePlayerSettings(UUID playerUuid) {
        playerSettings.remove(playerUuid);
    }

    /**
     * Clear all player settings (e.g., server restart).
     */
    public static void clearAll() {
        playerSettings.clear();
    }

    /**
     * Get number of players with custom settings.
     */
    public static int getPlayerCount() {
        return playerSettings.size();
    }
}
