package com.wcholmes.landscaper.common.strategy;

import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.item.BiomePalette;
import com.wcholmes.landscaper.common.item.NaturalizationMode;
import com.wcholmes.landscaper.common.util.TerrainUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Flatten strategy - levels all terrain to the clicked block's Y-level.
 * Removes blocks above target, fills blocks below target.
 * Uses max_flatten_height config to limit upward checking.
 */
public class FlattenStrategy extends BaseTerrainStrategy {

    @Override
    public int modify(Level level, BlockPos center, Player player, NaturalizationMode mode,
                      Map<Item, Integer> resourcesNeeded,
                      com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {

        int blocksChanged = 0;
        int columnsRaised = 0;
        int columnsLowered = 0;
        int columnsUnchanged = 0;

        // Use player-specific settings or fall back to global config
        int radius = playerSettings != null ? playerSettings.radius : NaturalizationConfig.getRadius();
        int effectiveRadius = radius - 1;

        BlockPos playerPos = player != null ? player.blockPosition() : null;
        boolean isCircle = NaturalizationConfig.isCircleShape();
        int messyEdgeExtension = playerSettings != null ? playerSettings.messyEdgeExtension : NaturalizationConfig.getMessyEdgeExtension();

        // Get target height from clicked surface
        BlockPos targetSurfacePos = TerrainUtils.findSurface(level, center);
        if (targetSurfacePos == null) {
            if (player != null) {
                player.displayClientMessage(
                    Component.literal("Cannot flatten - no valid surface found at target!"),
                    true
                );
            }
            return 0;
        }

        int targetHeight = targetSurfacePos.getY();
        int maxFlattenHeight = playerSettings != null ? playerSettings.maxFlattenHeight : NaturalizationConfig.getMaxFlattenHeight();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting FLATTEN at {} with targetHeight={}, radius={}, maxHeight={}",
                center, targetHeight, radius, maxFlattenHeight);
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

                    // Flatten this column
                    FlattenResult result = flattenColumn(level, columnPos, targetHeight, maxFlattenHeight, mode,
                                                        resourcesNeeded, playerPos, playerSettings);
                    blocksChanged += result.blocksChanged;

                    if (result.heightChange > 0) columnsRaised++;
                    else if (result.heightChange < 0) columnsLowered++;
                    else columnsUnchanged++;
                }
            }
        }

        // Clean up item drops
        cleanupItemDrops(level, center, radius);

        String message = String.format("FLATTEN complete! Target Y=%d | %d blocks | ↑%d ↓%d =%d columns",
            targetHeight, blocksChanged, columnsRaised, columnsLowered, columnsUnchanged);

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
        int radius = playerSettings != null ? playerSettings.radius : NaturalizationConfig.getRadius();
        int effectiveRadius = radius - 1;

        // Get target height
        BlockPos targetSurfacePos = TerrainUtils.findSurface(level, center);
        if (targetSurfacePos == null) return;

        int targetHeight = targetSurfacePos.getY();
        int maxFlattenHeight = playerSettings != null ? playerSettings.maxFlattenHeight : NaturalizationConfig.getMaxFlattenHeight();

        for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
            for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                BlockPos columnPos = center.offset(dx, 0, dz);
                BlockPos surfacePos = TerrainUtils.findSurface(level, columnPos);

                if (surfacePos == null) continue;

                int currentHeight = surfacePos.getY();
                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(surfacePos);

                if (currentHeight < targetHeight) {
                    // Would raise - need to add blocks
                    for (int y = currentHeight + 1; y <= targetHeight; y++) {
                        BlockState newState;
                        if (y == targetHeight) {
                            // Surface block
                            Block surfaceBlock = BiomePalette.getSurfaceBlock(biome, mode, mode.allowsVariation());
                            newState = surfaceBlock.defaultBlockState();
                        } else {
                            // Subsurface (dirt)
                            newState = Blocks.DIRT.defaultBlockState();
                        }

                        Item resourceItem = getResourceItemForBlock(newState);
                        if (resourceItem != null) {
                            resourcesNeeded.merge(resourceItem, 1, Integer::sum);
                        }
                    }
                }
                // Note: Lowering doesn't consume resources, it removes blocks
            }
        }
    }

    /**
     * Result of flattening a single column.
     */
    private static class FlattenResult {
        int blocksChanged;
        int heightChange; // positive = raised, negative = lowered, 0 = unchanged

        FlattenResult(int blocksChanged, int heightChange) {
            this.blocksChanged = blocksChanged;
            this.heightChange = heightChange;
        }
    }

    /**
     * Flatten a single column to the target height.
     */
    private FlattenResult flattenColumn(Level level, BlockPos pos, int targetHeight, int maxFlattenHeight,
                                       NaturalizationMode mode, Map<Item, Integer> resourcesNeeded,
                                       BlockPos playerPos,
                                       com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {
        int changed = 0;

        // Safety check: Don't modify columns directly under the player
        if (playerPos != null && pos.getX() == playerPos.getX() && pos.getZ() == playerPos.getZ()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Skipping column at {} - player is standing here", pos);
            }
            return new FlattenResult(0, 0);
        }

        // Find current surface
        BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
        if (surfacePos == null) {
            return new FlattenResult(0, 0);
        }

        int currentHeight = surfacePos.getY();
        int heightDiff = targetHeight - currentHeight;

        // Detect biome for surface blocks
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(new BlockPos(pos.getX(), targetHeight, pos.getZ()));

        if (currentHeight > targetHeight) {
            // LOWER: Remove blocks from current height down to target height

            // Only check up to maxFlattenHeight above target
            int maxAllowedHeight = targetHeight + maxFlattenHeight;
            if (currentHeight > maxAllowedHeight) {
                // Surface is too high, skip this column
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Skipping column at {} - surface Y={} exceeds max height {} (target+{})",
                        pos, currentHeight, maxAllowedHeight, maxFlattenHeight);
                }
                return new FlattenResult(0, 0);
            }

            // Clear all blocks from current surface down to (but not including) target
            for (int y = currentHeight; y > targetHeight; y--) {
                BlockPos targetPos = new BlockPos(pos.getX(), y, pos.getZ());
                BlockState currentState = level.getBlockState(targetPos);

                if (!currentState.isAir()) {
                    level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);
                    changed++;
                }
            }

            // Place surface block at target height
            BlockPos targetPos = new BlockPos(pos.getX(), targetHeight, pos.getZ());
            Block surfaceBlock = BiomePalette.getSurfaceBlock(biome, mode, mode.allowsVariation());
            level.setBlock(targetPos, surfaceBlock.defaultBlockState(), BLOCK_UPDATE_FLAG);
            changed++;

            // Track resources
            trackResources(resourcesNeeded, playerSettings, surfaceBlock.defaultBlockState());

        } else if (currentHeight < targetHeight) {
            // RAISE: Add blocks from current height up to target height

            // Fill from current+1 to target
            for (int y = currentHeight + 1; y <= targetHeight; y++) {
                BlockPos targetPos = new BlockPos(pos.getX(), y, pos.getZ());
                BlockState newState;

                if (y == targetHeight) {
                    // Surface block
                    Block surfaceBlock = BiomePalette.getSurfaceBlock(biome, mode, mode.allowsVariation());
                    newState = surfaceBlock.defaultBlockState();
                } else {
                    // Subsurface (dirt)
                    newState = Blocks.DIRT.defaultBlockState();
                }

                level.setBlock(targetPos, newState, BLOCK_UPDATE_FLAG);
                changed++;

                // Track resources
                trackResources(resourcesNeeded, playerSettings, newState);
            }

        } else {
            // Already at target height - just replace surface block
            BlockPos targetPos = new BlockPos(pos.getX(), targetHeight, pos.getZ());
            BlockState currentState = level.getBlockState(targetPos);

            Block surfaceBlock = BiomePalette.getSurfaceBlock(biome, mode, mode.allowsVariation());
            BlockState newState = surfaceBlock.defaultBlockState();

            if (!currentState.is(newState.getBlock())) {
                level.setBlock(targetPos, newState, BLOCK_UPDATE_FLAG);
                changed++;
                trackResources(resourcesNeeded, playerSettings, newState);
            }
        }

        // Add vegetation if mode supports it
        if (mode.shouldAddPlants() && changed > 0) {
            BlockPos surfaceTargetPos = new BlockPos(pos.getX(), targetHeight, pos.getZ());
            BlockState surfaceState = level.getBlockState(surfaceTargetPos);

            if (surfaceState.is(Blocks.GRASS_BLOCK) || surfaceState.is(Blocks.SAND) ||
                surfaceState.is(Blocks.PODZOL) || surfaceState.is(Blocks.MYCELIUM) || surfaceState.is(Blocks.MUD)) {

                NaturalizationConfig.VegetationDensity density = playerSettings != null ?
                    playerSettings.vegetationDensity : NaturalizationConfig.getVegetationDensity();

                if (ThreadLocalRandom.current().nextDouble() < density.getChance()) {
                    BlockPos abovePos = surfaceTargetPos.above();
                    if (level.getBlockState(abovePos).isAir()) {
                        Block plantBlock = BiomePalette.getVegetationBlock(biome);
                        BlockState plantState = plantBlock.defaultBlockState();
                        if (shouldPlaceVegetation(level, abovePos, plantState)) {
                            level.setBlock(abovePos, plantState, BLOCK_UPDATE_FLAG);
                        }
                    }
                }
            }
        }

        return new FlattenResult(changed, heightDiff);
    }

    private void trackResources(Map<Item, Integer> resourcesNeeded,
                                com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings,
                                BlockState state) {
        boolean consumeResources = playerSettings != null ? playerSettings.consumeResources : NaturalizationConfig.shouldConsumeResources();
        if (consumeResources) {
            Item resourceItem = getResourceItemForBlock(state);
            if (resourceItem != null) {
                resourcesNeeded.merge(resourceItem, 1, Integer::sum);
            }
        }
    }
}
