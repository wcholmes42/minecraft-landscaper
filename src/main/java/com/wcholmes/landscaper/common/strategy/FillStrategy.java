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
 * Fill strategy - like ReplaceStrategy but ALSO fills air gaps with terrain blocks.
 * Uses the same depth logic (surface ±10 blocks) but replaces air in addition to terrain.
 */
public class FillStrategy extends BaseTerrainStrategy {

    @Override
    public int modify(Level level, BlockPos center, Player player, NaturalizationMode mode,
                      Map<Item, Integer> resourcesNeeded,
                      com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {

        int blocksChanged = 0;
        int landColumns = 0;
        int underwaterColumns = 0;

        // Use player-specific settings or fall back to global config
        int radius = playerSettings != null ? playerSettings.radius : NaturalizationConfig.getRadius();
        int effectiveRadius = radius - 1;

        BlockPos playerPos = player != null ? player.blockPosition() : null;
        boolean isCircle = NaturalizationConfig.isCircleShape();
        int messyEdgeExtension = playerSettings != null ? playerSettings.messyEdgeExtension : NaturalizationConfig.getMessyEdgeExtension();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting FILL {} at {} with radius {}, shape={}, messyEdgeExtension={}",
                mode.getDisplayName(), center, radius, isCircle ? "circle" : "square", messyEdgeExtension);
        }

        // Sample surrounding terrain to build natural palette
        int sampleRadius = Math.max(radius + 5, 10);
        TerrainPalette sampledPalette = sampleSurroundingTerrain(level, center, sampleRadius);
        LOGGER.info("Sampled terrain palette: {}", sampledPalette.getStats());

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

                    // Check if this column is underwater
                    BlockPos surfacePos = TerrainUtils.findSurface(level, columnPos);
                    if (surfacePos != null) {
                        boolean isColumnUnderwater = isUnderwater(level, surfacePos);
                        if (isColumnUnderwater) {
                            underwaterColumns++;
                        } else {
                            landColumns++;
                        }
                    }

                    // Process this column with fill logic
                    int result = fillColumn(level, columnPos, mode, resourcesNeeded, playerPos, playerSettings, center, sampledPalette);
                    blocksChanged += result;
                }
            }
        }

        // Clean up item drops
        cleanupItemDrops(level, center, radius);

        // Smart logging
        String typeMessage;
        if (underwaterColumns > 0 && landColumns > 0) {
            typeMessage = String.format("Hybrid FILL (land: %d columns, water: %d columns)", landColumns, underwaterColumns);
        } else if (underwaterColumns > 0) {
            typeMessage = "Underwater FILL";
        } else {
            typeMessage = "Land FILL";
        }

        LOGGER.info("{} complete! Changed {} blocks | {} columns | searchRadius={}",
            typeMessage, blocksChanged, columnsProcessed, searchRadius);

        if (player != null) {
            player.displayClientMessage(
                Component.literal(typeMessage + ": " + blocksChanged + " blocks | " + columnsProcessed + " columns | R:" + searchRadius),
                true
            );
        }

        return blocksChanged;
    }

    @Override
    public void calculateResourcesNeeded(Level level, BlockPos center, NaturalizationMode mode,
                                          Map<Item, Integer> resourcesNeeded,
                                          com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {
        int radius = playerSettings != null ? playerSettings.radius : NaturalizationConfig.getRadius();
        int effectiveRadius = radius - 1;

        for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
            for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                BlockPos columnPos = center.offset(dx, 0, dz);
                BlockPos surfacePos = TerrainUtils.findSurface(level, columnPos);

                if (surfacePos == null) continue;

                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(surfacePos);
                boolean isUnderwater = isUnderwater(level, surfacePos);

                // Simulate what blocks would be placed (including air!)
                for (int i = 0; i >= -HEIGHT_BELOW; i--) {
                    BlockPos targetPos = surfacePos.offset(0, i, 0);
                    BlockState currentState = level.getBlockState(targetPos);

                    // DIFFERENT FROM REPLACE: We also fill air and water!
                    if (currentState.liquid()) continue; // Still skip water

                    // If air or safe block, we'll place something
                    if (currentState.isAir() || NaturalizationConfig.getSafeBlocks().contains(currentState.getBlock())) {
                        BlockState newState = determineNaturalBlock(i, isUnderwater, mode, biome); // Resource estimation uses fallback

                        if (!currentState.is(newState.getBlock())) {
                            Item resourceItem = getResourceItemForBlock(newState);
                            if (resourceItem != null) {
                                resourcesNeeded.merge(resourceItem, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Fill a single column - like naturalizeColumn but ALSO fills air gaps.
     */
    private int fillColumn(Level level, BlockPos pos, NaturalizationMode mode, Map<Item, Integer> resourcesNeeded,
                           BlockPos playerPos, com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings,
                           BlockPos center, TerrainPalette sampledPalette) {
        int changed = 0;

        // Find the surface level
        BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
        if (surfacePos == null) {
            return 0;
        }

        // Safety check: Don't modify columns directly under the player
        if (playerPos != null && pos.getX() == playerPos.getX() && pos.getZ() == playerPos.getZ()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Skipping column at {} - player is standing here", pos);
            }
            return 0;
        }

        // Detect biome and underwater status
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(surfacePos);
        boolean isUnderwater = isUnderwater(level, surfacePos);

        // PASS 1: Clear vegetation above surface (still clear plants)
        for (int i = HEIGHT_ABOVE; i > 0; i--) { // Only ABOVE surface
            BlockPos targetPos = surfacePos.offset(0, i, 0);
            BlockState currentState = level.getBlockState(targetPos);

            if (currentState.isAir() || currentState.liquid()) {
                continue;
            }

            // Clear vegetation
            boolean isVegetation = currentState.canBeReplaced() ||
                currentState.getBlock() instanceof net.minecraft.world.level.block.BushBlock ||
                currentState.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock ||
                currentState.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock ||
                currentState.is(Blocks.KELP) ||
                currentState.is(Blocks.KELP_PLANT) ||
                currentState.is(Blocks.SEAGRASS) ||
                currentState.is(Blocks.TALL_SEAGRASS);

            if (isVegetation) {
                level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);

                // Clear double-height plants
                if (currentState.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock) {
                    BlockPos abovePos = targetPos.above();
                    BlockState aboveState = level.getBlockState(abovePos);
                    if (aboveState.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock) {
                        level.setBlock(abovePos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);
                    }

                    BlockPos belowPos = targetPos.below();
                    BlockState belowState = level.getBlockState(belowPos);
                    if (belowState.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock) {
                        level.setBlock(belowPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);
                    }
                }
            }
        }

        // PASS 2: Fill/replace terrain blocks from surface downward (INCLUDING AIR!)
        for (int i = 0; i >= -HEIGHT_BELOW; i--) {
            BlockPos targetPos = surfacePos.offset(0, i, 0);
            BlockState currentState = level.getBlockState(targetPos);

            // KEY DIFFERENCE: We fill air blocks too!
            // Skip only water (we don't want to replace water with blocks)
            if (currentState.liquid()) {
                continue;
            }

            // If it's air, safe block, or vegetation at surface - fill it!
            boolean shouldFill = currentState.isAir() ||
                                NaturalizationConfig.getSafeBlocks().contains(currentState.getBlock()) ||
                                (i == 0 && (currentState.canBeReplaced() ||
                                           currentState.getBlock() instanceof net.minecraft.world.level.block.BushBlock));

            if (shouldFill) {
                BlockState newState = determineNaturalBlock(i, isUnderwater, mode, biome, sampledPalette);

                // Place the block
                level.setBlock(targetPos, newState, BLOCK_UPDATE_FLAG);
                changed++;

                // Track resources
                boolean consumeResources = playerSettings != null ? playerSettings.consumeResources : NaturalizationConfig.shouldConsumeResources();
                if (consumeResources) {
                    Item resourceItem = getResourceItemForBlock(newState);
                    if (resourceItem != null) {
                        resourcesNeeded.merge(resourceItem, 1, Integer::sum);
                    }
                }
            }

            // Handle surface decorations
            if (i == 0) {
                BlockState surfaceState = level.getBlockState(targetPos);

                if (mode.shouldAddPlants()) {
                    // Land vegetation
                    if (!isUnderwater && (surfaceState.is(Blocks.GRASS_BLOCK) || surfaceState.is(Blocks.SAND) ||
                        surfaceState.is(Blocks.PODZOL) || surfaceState.is(Blocks.MYCELIUM) || surfaceState.is(Blocks.MUD))) {
                        NaturalizationConfig.VegetationDensity density = playerSettings != null ?
                            playerSettings.vegetationDensity : NaturalizationConfig.getVegetationDensity();
                        if (ThreadLocalRandom.current().nextDouble() < density.getChance()) {
                            BlockPos abovePos = targetPos.above();
                            if (level.getBlockState(abovePos).isAir()) {
                                Block plantBlock = BiomePalette.getVegetationBlock(biome);
                                BlockState plantState = plantBlock.defaultBlockState();
                                if (shouldPlaceVegetation(level, abovePos, plantState)) {
                                    level.setBlock(abovePos, plantState, BLOCK_UPDATE_FLAG);
                                    if (LOGGER.isDebugEnabled()) {
                                        LOGGER.debug("Placed vegetation {} at {} (offset from center: {}, {})",
                                            plantBlock.getName().getString(), abovePos,
                                            abovePos.getX() - center.getX(), abovePos.getZ() - center.getZ());
                                    }
                                }
                            }
                        }
                    }
                    // Underwater vegetation
                    else if (isUnderwater && (surfaceState.is(Blocks.SAND) || surfaceState.is(Blocks.GRAVEL) ||
                        surfaceState.is(Blocks.MUD) || surfaceState.is(Blocks.CLAY))) {
                        NaturalizationConfig.VegetationDensity density = playerSettings != null ?
                            playerSettings.vegetationDensity : NaturalizationConfig.getVegetationDensity();
                        double baseChance = density.getChance();
                        double vegetationChance = mode.allowsVariation() ?
                            baseChance * MESSY_UNDERWATER_VEGETATION_MULTIPLIER :
                            baseChance;
                        if (ThreadLocalRandom.current().nextDouble() < vegetationChance) {
                            BlockPos abovePos = targetPos.above();
                            BlockState aboveState = level.getBlockState(abovePos);
                            if (aboveState.is(Blocks.WATER)) {
                                Block plantBlock = ThreadLocalRandom.current().nextDouble() < UNDERWATER_SEAGRASS_RATIO ? Blocks.SEAGRASS : Blocks.KELP;
                                level.setBlock(abovePos, plantBlock.defaultBlockState(), BLOCK_UPDATE_FLAG);
                            }
                        }
                    }
                }
            }
        }

        return changed;
    }
}
