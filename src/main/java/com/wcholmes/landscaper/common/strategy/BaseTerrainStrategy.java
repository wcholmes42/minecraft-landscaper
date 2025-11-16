package com.wcholmes.landscaper.common.strategy;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.item.BiomePalette;
import com.wcholmes.landscaper.common.item.NaturalizationMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base class for terrain modification strategies with shared utility methods.
 */
public abstract class BaseTerrainStrategy implements TerrainModificationStrategy {
    protected static final Logger LOGGER = LogUtils.getLogger();

    // Constants for terrain depths
    protected static final int HEIGHT_ABOVE = 3;
    protected static final int HEIGHT_BELOW = 10;
    protected static final int TERRAIN_LAYER_SHALLOW = -3;
    protected static final int TERRAIN_LAYER_MID = -6;

    // Block update flags
    protected static final int BLOCK_UPDATE_FLAG = 2; // No drops, send update to clients

    // Vegetation constants
    protected static final double LAND_VEGETATION_CHANCE = 0.075; // 7.5%
    protected static final double UNDERWATER_VEGETATION_CHANCE = 0.075; // 7.5%
    protected static final double MESSY_UNDERWATER_VEGETATION_MULTIPLIER = 3.0; // 22.5%
    protected static final double UNDERWATER_SEAGRASS_RATIO = 0.60; // 60% seagrass, 40% kelp
    protected static final int VEGE_MAX_ADJACENT_SAME = 2;

    // Underwater terrain layer distributions
    protected static final double UNDERWATER_SURF_SAND = 0.70;
    protected static final double UNDERWATER_SURF_GRAVEL = 0.85;
    protected static final double UNDERWATER_SURF_MUD = 0.95;
    protected static final double UNDERWATER_MID_GRAVEL = 0.60;
    protected static final double UNDERWATER_MID_SAND = 0.90;
    protected static final double UNDERWATER_DEEP_SAND = 0.50;
    protected static final double UNDERWATER_DEEP_GRAVEL = 0.80;

    /**
     * Check if a position is underwater (water block above surface).
     */
    protected boolean isUnderwater(Level level, BlockPos surface) {
        BlockState above = level.getBlockState(surface.above());
        return above.is(Blocks.WATER);
    }

    /**
     * Sample surrounding terrain to build a palette of natural blocks.
     * Samples actual terrain blocks at the target elevation, handling underwater areas correctly.
     */
    protected TerrainPalette sampleSurroundingTerrain(Level level, BlockPos center, int sampleRadius) {
        TerrainPalette palette = new TerrainPalette();

        LOGGER.info("=== TERRAIN SAMPLING DEBUG START ===");
        LOGGER.info("Sample center: {}, radius: {}", center, sampleRadius);

        // Check what we're standing in/on
        BlockState centerState = level.getBlockState(center);
        LOGGER.info("Center block: {}", centerState.getBlock().getDescriptionId());

        // Search for solid floor - handle both clicking water and clicking underwater blocks
        BlockPos targetFloor = center;
        boolean foundFloor = false;

        // If we clicked water or air, search down for floor
        if (centerState.isAir() || centerState.is(Blocks.WATER) || centerState.is(Blocks.LAVA)) {
            LOGGER.info("Center is air/water/lava, searching down for solid floor...");
            for (int y = 0; y >= -30; y--) {
                BlockPos checkPos = center.offset(0, y, 0);
                BlockState checkState = level.getBlockState(checkPos);
                Block checkBlock = checkState.getBlock();

                if (!checkState.isAir() && !checkState.is(Blocks.WATER) && !checkState.is(Blocks.LAVA)) {
                    targetFloor = checkPos;
                    foundFloor = true;
                    LOGGER.info("Found solid floor at Y={}, block: {}", checkPos.getY(), checkBlock.getDescriptionId());
                    break;
                }
            }
        } else {
            // We clicked a solid block - use it as the floor
            targetFloor = center;
            foundFloor = true;
            LOGGER.info("Center is solid block, using as floor: {}", centerState.getBlock().getDescriptionId());
        }

        if (!foundFloor) {
            LOGGER.error("Could not find solid floor! Using center position.");
            targetFloor = center;
        }

        // Determine if this is an underwater environment - check area around the floor
        boolean isUnderwater = false;
        int waterBlocksFound = 0;

        // Check directly above floor
        for (int y = 0; y <= 10; y++) {
            BlockPos checkPos = targetFloor.offset(0, y, 0);
            if (level.getBlockState(checkPos).is(Blocks.WATER)) {
                waterBlocksFound++;
                isUnderwater = true;
                LOGGER.info("Water found {} blocks above floor", y);
                break;
            }
        }

        // If no water directly above, check surrounding area for water at same level
        if (!isUnderwater) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = targetFloor.offset(dx, 0, dz);
                    if (level.getBlockState(checkPos).is(Blocks.WATER)) {
                        waterBlocksFound++;
                    }
                    // Also check 1 block above
                    if (level.getBlockState(checkPos.above()).is(Blocks.WATER)) {
                        waterBlocksFound++;
                    }
                }
            }
            if (waterBlocksFound >= 5) {
                isUnderwater = true;
                LOGGER.info("Water found nearby ({} blocks) - treating as underwater", waterBlocksFound);
            }
        }

        LOGGER.info("Underwater detection: {} (water blocks found: {})", isUnderwater, waterBlocksFound);

        int targetY = targetFloor.getY();
        LOGGER.info("TARGET SAMPLING DEPTH: Y={} (underwater={})", targetY, isUnderwater);

        int totalSampled = 0;
        int skippedAir = 0;
        int skippedWater = 0;
        int skippedUnsafe = 0;

        // Sample in a square pattern around the center at the TARGET DEPTH
        for (int xOffset = -sampleRadius; xOffset <= sampleRadius; xOffset++) {
            for (int zOffset = -sampleRadius; zOffset <= sampleRadius; zOffset++) {
                BlockPos sampleColumn = center.offset(xOffset, 0, zOffset);

                // Sample blocks at depths relative to our target floor level
                for (int depthOffset = 0; depthOffset >= -10; depthOffset--) {
                    BlockPos sampleAt = new BlockPos(sampleColumn.getX(), targetY + depthOffset, sampleColumn.getZ());
                    BlockState state = level.getBlockState(sampleAt);
                    Block block = state.getBlock();

                    // Skip air, water, lava
                    if (state.isAir()) {
                        skippedAir++;
                        continue;
                    }
                    if (block == Blocks.WATER || block == Blocks.LAVA) {
                        skippedWater++;
                        continue;
                    }

                    // Only sample safe/natural blocks
                    if (!NaturalizationConfig.getSafeBlocks().contains(block)) {
                        skippedUnsafe++;
                        continue;
                    }

                    totalSampled++;

                    // Categorize by depth relative to target floor
                    if (depthOffset == 0) {
                        palette.addSurfaceBlock(block);
                    } else if (depthOffset >= -2) {
                        palette.addSubsurfaceBlock(block);
                    } else if (depthOffset >= -7) {
                        palette.addDeepBlock(block);
                    }
                }
            }
        }

        LOGGER.info("Sampling complete: {} blocks sampled, {} air skipped, {} water skipped, {} unsafe skipped",
            totalSampled, skippedAir, skippedWater, skippedUnsafe);
        LOGGER.info("PALETTE STATS: {}", palette.getStats());
        LOGGER.info("Has valid samples: {}", palette.hasValidSamples());
        if (palette.hasValidSamples()) {
            LOGGER.info("DETAILED BREAKDOWN: {}", palette.getDetailedBreakdown());
        }
        LOGGER.info("=== TERRAIN SAMPLING DEBUG END ===");

        return palette;
    }

    /**
     * Determine the natural block to place at a given relative height.
     * Uses sampled terrain palette when available, falls back to biome palettes.
     * New strata depth system:
     * - relativeY 0: 1 surface block (sampled from surrounding terrain)
     * - relativeY -1 to -2: 2 subsurface blocks (sampled from surrounding terrain)
     * - relativeY -3 to -7: 5 randomized deeper blocks (sampled with stone gradient)
     * - relativeY < -7: Stone bedrock
     */
    protected BlockState determineNaturalBlock(int relativeY, boolean isUnderwater, NaturalizationMode mode,
                                               net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
                                               TerrainPalette sampledPalette) {
        // Air above surface
        if (relativeY > 0) {
            return Blocks.AIR.defaultBlockState();
        }

        // Use sampled palette if available and has valid samples
        boolean useSampledPalette = sampledPalette != null && sampledPalette.hasValidSamples();

        // Surface block (relativeY == 0)
        if (relativeY == 0) {
            if (useSampledPalette) {
                Block sampledBlock = sampledPalette.getSurfaceBlock();
                return sampledBlock.defaultBlockState();
            } else if (isUnderwater) {
                // Fallback: Underwater surface - sand/gravel mix
                if (mode.allowsVariation()) {
                    double roll = ThreadLocalRandom.current().nextDouble();
                    if (roll < UNDERWATER_SURF_SAND) return Blocks.SAND.defaultBlockState();
                    else if (roll < UNDERWATER_SURF_GRAVEL) return Blocks.GRAVEL.defaultBlockState();
                    else if (roll < UNDERWATER_SURF_MUD) return Blocks.MUD.defaultBlockState();
                    else return Blocks.COARSE_DIRT.defaultBlockState();
                } else {
                    return Blocks.SAND.defaultBlockState();
                }
            } else {
                // Fallback: Use biome-specific palette
                Block surfaceBlock = BiomePalette.getSurfaceBlock(biome, mode, mode.allowsVariation());
                return surfaceBlock.defaultBlockState();
            }
        }

        // Subsurface layer (relativeY -1 to -2)
        if (relativeY >= -2) {
            if (useSampledPalette) {
                Block sampledBlock = sampledPalette.getSubsurfaceBlock();
                return sampledBlock.defaultBlockState();
            } else {
                // Fallback: Use biome-specific palette
                Block subsurfaceBlock = BiomePalette.getSubsurfaceBlock(biome, relativeY, isUnderwater, mode.allowsVariation());
                return subsurfaceBlock.defaultBlockState();
            }
        }

        // Deep layer (relativeY -3 to -7) - randomized with stone gradient
        if (relativeY >= -7) {
            if (useSampledPalette) {
                // Use sampled blocks with increasing stone chance as depth increases
                double stoneChance = 0.20 + (Math.abs(relativeY) - 3) * 0.12;
                if (ThreadLocalRandom.current().nextDouble() < stoneChance) {
                    return Blocks.STONE.defaultBlockState();
                }
                Block sampledBlock = sampledPalette.getDeepBlock();
                return sampledBlock.defaultBlockState();
            } else {
                // Fallback: Use biome-specific palette
                Block deepBlock = BiomePalette.getDeepLayerBlock(biome, relativeY, isUnderwater, mode.allowsVariation());
                return deepBlock.defaultBlockState();
            }
        }

        // Bedrock layer (relativeY < -7) - pure stone
        return Blocks.STONE.defaultBlockState();
    }

    /**
     * Legacy method without sampled palette - uses biome palettes only.
     */
    protected BlockState determineNaturalBlock(int relativeY, boolean isUnderwater, NaturalizationMode mode,
                                               net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        return determineNaturalBlock(relativeY, isUnderwater, mode, biome, null);
    }

    /**
     * Check if vegetation should be placed at this position (WFC-inspired).
     * Prevents clustering of identical plants.
     */
    protected boolean shouldPlaceVegetation(Level level, BlockPos pos, BlockState plantToPlace) {
        BlockPos[] neighbors = {
            pos.north(), pos.south(), pos.east(), pos.west(),
            pos.north().east(), pos.north().west(),
            pos.south().east(), pos.south().west()
        };

        int sameTypeCount = 0;
        for (BlockPos neighbor : neighbors) {
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.is(plantToPlace.getBlock())) {
                sameTypeCount++;
            }
        }

        return sameTypeCount < VEGE_MAX_ADJACENT_SAME;
    }

    /**
     * Map a block state to its resource item equivalent.
     */
    protected Item getResourceItemForBlock(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)) return Items.DIRT;
        if (state.is(Blocks.DIRT)) return Items.DIRT;
        if (state.is(Blocks.DIRT_PATH)) return Items.DIRT;
        if (state.is(Blocks.FARMLAND)) return Items.DIRT;
        if (state.is(Blocks.PODZOL)) return Items.DIRT;
        if (state.is(Blocks.MYCELIUM)) return Items.DIRT;
        if (state.is(Blocks.MUD)) return Items.MUD;
        if (state.is(Blocks.COARSE_DIRT)) return Items.DIRT;
        if (state.is(Blocks.STONE)) return Items.COBBLESTONE;
        if (state.is(Blocks.MOSSY_COBBLESTONE)) return Items.MOSSY_COBBLESTONE;
        if (state.is(Blocks.SAND)) return Items.SAND;
        if (state.is(Blocks.RED_SAND)) return Items.RED_SAND;
        if (state.is(Blocks.SANDSTONE)) return Items.SANDSTONE;
        if (state.is(Blocks.GRAVEL)) return Items.GRAVEL;
        if (state.is(Blocks.CLAY)) return Items.CLAY_BALL;
        if (state.is(Blocks.WATER)) return null; // Water doesn't consume resources
        return null;
    }

    /**
     * Clean up item drops in the area (prevents duplication exploits).
     */
    protected void cleanupItemDrops(Level level, BlockPos center, int radius) {
        if (!level.isClientSide()) {
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(radius),
                entity -> true
            ).forEach(itemEntity -> itemEntity.discard());
        }
    }
}
