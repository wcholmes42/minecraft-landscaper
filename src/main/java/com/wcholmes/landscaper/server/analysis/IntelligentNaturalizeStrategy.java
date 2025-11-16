package com.wcholmes.landscaper.server.analysis;

import com.wcholmes.landscaper.common.config.PlayerConfig;
import com.wcholmes.landscaper.common.item.NaturalizationMode;
import com.wcholmes.landscaper.common.util.TerrainUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Intelligent terrain naturalization using analyzed terrain profile.
 * Replicates the natural style of surrounding terrain.
 */
public class IntelligentNaturalizeStrategy {

    /**
     * Apply intelligent naturalization based on analyzed terrain profile.
     *
     * @param level The world level
     * @param center Center position for modification
     * @param radius Radius of modification
     * @param profile Analyzed terrain profile from surrounding area
     * @param circleShape Whether to use circle shape
     * @param messyEdge Messy edge extension
     * @return Number of blocks modified
     */
    public static int apply(Level level, BlockPos center, int radius, TerrainProfile profile,
                           boolean circleShape, int messyEdge) {

        // Find actual surface
        BlockPos surface = TerrainUtils.findSurface(level, center);
        if (surface == null) return 0;

        // Get positions to modify
        List<BlockPos> positions = circleShape ?
            getCirclePositions(surface, radius, messyEdge) :
            getSquarePositions(surface, radius, messyEdge);

        int blocksChanged = 0;

        // Pass 1: Clear vegetation and air
        for (BlockPos pos : positions) {
            BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
            if (surfacePos == null) continue;

            // Clear vegetation above
            for (int y = 1; y <= 3; y++) {
                BlockPos vegPos = surfacePos.above(y);
                BlockState state = level.getBlockState(vegPos);
                if (!state.isAir() && isVegetation(state)) {
                    level.setBlock(vegPos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        // Pass 2: Apply terrain blocks using profile data (preserve features, match consistency)
        for (BlockPos pos : positions) {
            BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
            if (surfacePos == null) continue;

            int currentY = surfacePos.getY();

            // PRESERVE TERRAIN FEATURES: Check local elevation variation
            boolean isSignificantFeature = isTerrainFeature(level, surfacePos, profile);

            if (isSignificantFeature) {
                // This is a hill/mountain/feature - only replace surface block, DON'T modify height
                Block surfaceBlock = profile.getConsistencyAwareSurfaceBlock();
                level.setBlock(surfacePos, surfaceBlock.defaultBlockState(), 3);
                blocksChanged++;
                continue; // Preserve elevation
            }

            // Calculate target height based on profile's height distribution and smoothness
            int targetY = calculateTargetHeight(pos, surface, profile);
            int heightDiff = targetY - currentY;

            // Limit height changes to prevent aggressive modification (max ±2 blocks)
            heightDiff = Math.max(-2, Math.min(2, heightDiff));

            // Apply height changes with profile-based blocks
            if (heightDiff > 0) {
                // Build up using subsurface blocks
                for (int y = 0; y < heightDiff; y++) {
                    Block block = profile.getWeightedRandomSubsurfaceBlock();
                    level.setBlock(surfacePos.above(y + 1), block.defaultBlockState(), 3);
                    blocksChanged++;
                }
            } else if (heightDiff < 0) {
                // Dig down (limited)
                for (int y = 0; y < Math.abs(heightDiff); y++) {
                    level.setBlock(surfacePos.above(y), Blocks.AIR.defaultBlockState(), 3);
                    blocksChanged++;
                }
            }

            // Place new surface based on sampled area
            BlockPos newSurface = surfacePos.above(Math.max(0, heightDiff));

            // SURFACE LAYER - Use CONSISTENCY-AWARE selection (mono areas stay mono)
            Block surfaceBlock = profile.getConsistencyAwareSurfaceBlock();
            level.setBlock(newSurface, surfaceBlock.defaultBlockState(), 3);
            blocksChanged++;

            // SUBSURFACE LAYERS - Use subsurface palette
            for (int y = 1; y <= 5; y++) {
                Block block = profile.getWeightedRandomSubsurfaceBlock();
                level.setBlock(newSurface.below(y), block.defaultBlockState(), 3);
                blocksChanged++;
            }
        }

        // Pass 3: Add vegetation based on profile
        if (profile.getVegetationDensity() > 0) {
            for (BlockPos pos : positions) {
                BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
                if (surfacePos == null) continue;

                // Apply vegetation with sampled density
                if (ThreadLocalRandom.current().nextDouble() < profile.getVegetationDensity()) {
                    Block vegBlock = profile.getWeightedRandomVegetation();
                    if (vegBlock != null) {
                        BlockState surfaceState = level.getBlockState(surfacePos);
                        if (canSupportVegetation(surfaceState)) {
                            level.setBlock(surfacePos.above(), vegBlock.defaultBlockState(), 3);
                            blocksChanged++;
                        }
                    }
                }
            }
        }

        // Pass 4: Apply water features (respects water type rules)
        int waterBlocks = WaterFeatureManager.applyWaterFeatures(level, positions, profile);
        blocksChanged += waterBlocks;

        // Pass 5: Clean up item drops
        AABB bounds = new AABB(surface).inflate(radius + messyEdge);
        level.getEntitiesOfClass(ItemEntity.class, bounds).forEach(ItemEntity::discard);

        return blocksChanged;
    }

    /**
     * Calculate target height for a position based on profile characteristics
     */
    private static int calculateTargetHeight(BlockPos pos, BlockPos center, TerrainProfile profile) {
        // Distance from center (for natural variation)
        double distance = Math.sqrt(pos.distSqr(center));

        // Use profile's average height as base
        int baseHeight = profile.getAverageY();

        // Add variation based on profile's smoothness
        // Smoother terrain = less variation, rougher = more variation
        double variationAmount = (1.0 - profile.getSmoothness()) * profile.getHeightRange() * 0.3;

        // Simple noise-like variation (could use Perlin noise for better results)
        double variation = (Math.sin(pos.getX() * 0.1) + Math.cos(pos.getZ() * 0.1)) * variationAmount;

        return (int) (baseHeight + variation);
    }

    /**
     * Get circle-shaped positions
     */
    private static List<BlockPos> getCirclePositions(BlockPos center, int radius, int messyEdge) {
        List<BlockPos> positions = new ArrayList<>();
        int totalRadius = radius + messyEdge;

        for (int x = -totalRadius; x <= totalRadius; x++) {
            for (int z = -totalRadius; z <= totalRadius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= totalRadius) {
                    positions.add(center.offset(x, 0, z));
                }
            }
        }

        return positions;
    }

    /**
     * Get square-shaped positions
     */
    private static List<BlockPos> getSquarePositions(BlockPos center, int radius, int messyEdge) {
        List<BlockPos> positions = new ArrayList<>();
        int totalRadius = radius + messyEdge;

        for (int x = -totalRadius; x <= totalRadius; x++) {
            for (int z = -totalRadius; z <= totalRadius; z++) {
                positions.add(center.offset(x, 0, z));
            }
        }

        return positions;
    }

    /**
     * Detect terrain features (hills, peaks) using LOCAL elevation variation
     * Better than global average - works on mountains too
     */
    private static boolean isTerrainFeature(Level level, BlockPos pos, TerrainProfile profile) {
        int posY = pos.getY();

        // Check local neighborhood (5-block radius)
        int higherCount = 0;
        int lowerCount = 0;
        int totalChecked = 0;

        for (int x = -5; x <= 5; x += 2) {
            for (int z = -5; z <= 5; z += 2) {
                if (x == 0 && z == 0) continue;

                BlockPos neighbor = pos.offset(x, 0, z);
                BlockPos neighborSurface = TerrainUtils.findSurface(level, neighbor);
                if (neighborSurface == null) continue;

                int neighborY = neighborSurface.getY();
                if (neighborY > posY) higherCount++;
                if (neighborY < posY) lowerCount++;
                totalChecked++;
            }
        }

        if (totalChecked == 0) return false;

        // Feature detection: position is higher than 60%+ of local neighbors
        // OR has significant local variation (peak or valley)
        double higherRatio = (double) higherCount / totalChecked;
        double lowerRatio = (double) lowerCount / totalChecked;

        // Is a peak if most neighbors are lower
        boolean isPeak = lowerRatio > 0.6;

        // Is part of steep terrain if lots of elevation change
        boolean isSteep = (higherCount > 0 && lowerCount > 0) &&
                         ((higherRatio > 0.4 && lowerRatio > 0.4));

        return isPeak || isSteep;
    }

    private static boolean isVegetation(BlockState state) {
        if (state.isAir()) return false;
        Block block = state.getBlock();

        return block == Blocks.GRASS || block == Blocks.TALL_GRASS ||
               block == Blocks.FERN || block == Blocks.LARGE_FERN ||
               block == Blocks.SEAGRASS || block == Blocks.KELP ||
               block.getName().getString().contains("flower") ||
               block.getName().getString().contains("sapling");
    }

    private static boolean canSupportVegetation(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.GRASS_BLOCK ||
               block == Blocks.DIRT ||
               block == Blocks.PODZOL ||
               block == Blocks.MYCELIUM ||
               block == Blocks.SAND ||
               block == Blocks.MUD;
    }
}
