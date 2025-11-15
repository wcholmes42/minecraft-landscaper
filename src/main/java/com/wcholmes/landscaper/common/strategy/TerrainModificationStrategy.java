package com.wcholmes.landscaper.common.strategy;

import com.wcholmes.landscaper.common.item.NaturalizationMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * Strategy interface for different terrain modification behaviors.
 * Each mode (REPLACE, FILL, FLATTEN, FLOOD, NATURALIZE) implements this differently.
 */
public interface TerrainModificationStrategy {

    /**
     * Modify terrain at the given position according to this strategy's algorithm.
     *
     * @param level The world level
     * @param center The center position clicked by the player
     * @param player The player using the staff
     * @param mode The naturalization mode
     * @param resourcesNeeded Map to track resources needed (for consumption)
     * @param playerSettings Player-specific settings (null to use global config)
     * @return Number of blocks changed
     */
    int modify(Level level, BlockPos center, Player player, NaturalizationMode mode,
               Map<Item, Integer> resourcesNeeded,
               com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings);

    /**
     * Calculate resources needed for this operation without actually modifying terrain.
     * Used for pre-flight checks when resource consumption is enabled.
     *
     * @param level The world level
     * @param center The center position
     * @param mode The naturalization mode
     * @param resourcesNeeded Map to populate with needed resources
     * @param playerSettings Player-specific settings
     */
    void calculateResourcesNeeded(Level level, BlockPos center, NaturalizationMode mode,
                                   Map<Item, Integer> resourcesNeeded,
                                   com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings);
}
