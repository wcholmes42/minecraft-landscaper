package com.wcholmes.landscaper.common.strategy;

import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.item.NaturalizationMode;
import com.wcholmes.landscaper.common.util.TerrainUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Flood strategy - fills the selected area with water up to the clicked block's Y-level.
 * Creates lakes, ponds, moats by flooding air spaces with water.
 */
public class FloodStrategy extends BaseTerrainStrategy {

    @Override
    public int modify(Level level, BlockPos center, Player player, NaturalizationMode mode,
                      Map<Item, Integer> resourcesNeeded,
                      com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {

        int blocksChanged = 0;
        int waterBlocksPlaced = 0;

        // Use player-specific settings or fall back to global config
        int radius = playerSettings != null ? playerSettings.radius : NaturalizationConfig.getRadius();
        int effectiveRadius = radius - 1;

        BlockPos playerPos = player != null ? player.blockPosition() : null;
        boolean isCircle = NaturalizationConfig.isCircleShape();
        int messyEdgeExtension = playerSettings != null ? playerSettings.messyEdgeExtension : NaturalizationConfig.getMessyEdgeExtension();

        // Get target water level from clicked surface
        BlockPos targetSurfacePos = TerrainUtils.findSurface(level, center);
        if (targetSurfacePos == null) {
            if (player != null) {
                player.displayClientMessage(
                    Component.literal("Cannot flood - no valid surface found at target!"),
                    true
                );
            }
            return 0;
        }

        // Flood one block ABOVE the surface (so water sits on top, not replacing ground)
        int waterLevel = targetSurfacePos.getY() + 1;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting FLOOD at {} with waterLevel={}, radius={}",
                center, waterLevel, radius);
        }

        // Expand search range if messy edge is enabled
        int searchRadius = messyEdgeExtension > 0 ? effectiveRadius + messyEdgeExtension : effectiveRadius;

        int columnsProcessed = 0;

        // Iterate through the area
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                boolean withinRadius;

                if (messyEdgeExtension > 0) {
                    withinRadius = TerrainUtils.shouldApplyMessyEdge(x, z, radius, isCircle, center, messyEdgeExtension);
                } else {
                    withinRadius = isCircle ? (x * x + z * z <= effectiveRadius * effectiveRadius) : true;
                }

                if (withinRadius) {
                    columnsProcessed++;
                    BlockPos columnPos = center.offset(x, 0, z);

                    // Flood this column
                    int result = floodColumn(level, columnPos, waterLevel, playerPos);
                    blocksChanged += result;
                    waterBlocksPlaced += result;
                }
            }
        }

        String message = String.format("FLOOD complete! Water level Y=%d | %d water blocks | %d columns",
            waterLevel, waterBlocksPlaced, columnsProcessed);

        LOGGER.info(message);

        if (player != null) {
            player.displayClientMessage(Component.literal(message), true);
        }

        return blocksChanged;
    }

    @Override
    public void calculateResourcesNeeded(Level level, BlockPos center, NaturalizationMode mode,
                                          Map<Item, Integer> resourcesNeeded,
                                          com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {
        // Flood mode doesn't consume resources - water is free!
        // This method is intentionally empty
    }

    /**
     * Flood a single column with water up to the target water level.
     */
    private int floodColumn(Level level, BlockPos pos, int waterLevel, BlockPos playerPos) {
        int changed = 0;

        // Safety check: Don't modify columns directly under the player
        if (playerPos != null && pos.getX() == playerPos.getX() && pos.getZ() == playerPos.getZ()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Skipping column at {} - player is standing here", pos);
            }
            return 0;
        }

        // Find the surface or lowest point in this column
        BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
        if (surfacePos == null) {
            // No surface found, try flooding from bedrock up
            surfacePos = new BlockPos(pos.getX(), level.getMinBuildHeight(), pos.getZ());
        }

        // Fill with water from current position up to water level
        // Start from the lower of (surface, waterLevel) and go up to waterLevel
        int startY = Math.min(surfacePos.getY(), waterLevel);

        for (int y = startY; y <= waterLevel; y++) {
            BlockPos targetPos = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState currentState = level.getBlockState(targetPos);

            // Only place water in air blocks or replace existing water
            // Don't replace solid blocks - water should flow around them
            if (currentState.isAir() || currentState.is(Blocks.WATER)) {
                // Check if it's not already water
                if (!currentState.is(Blocks.WATER)) {
                    level.setBlock(targetPos, Blocks.WATER.defaultBlockState(), BLOCK_UPDATE_FLAG);
                    changed++;
                }
            }
        }

        return changed;
    }
}
