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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Intelligent terrain naturalization using analyzed terrain profile.
 * Replicates the natural style of surrounding terrain.
 */
public class IntelligentNaturalizeStrategy {
    private static final Logger LOGGER = LogManager.getLogger();

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

        // EMERGENCY SAFETY: Skip beach/river entirely until water issue resolved
        if (profile.getWaterType() == TerrainProfile.WaterType.BEACH ||
            profile.getWaterType() == TerrainProfile.WaterType.RIVER) {
            LOGGER.warn("⚠️  BEACH/RIVER detected at {} - terrain modification ABORTED for safety", center);
            LOGGER.warn("   Water type: {}, density: {}%", profile.getWaterType(), String.format("%.1f", profile.getWaterDensity() * 100));
            return 0; // ABORT - beaches are broken, skip completely
        }

        LOGGER.info("Starting intelligent naturalization at {} radius={}", center, radius);
        LOGGER.info("  Profile: consistency={}% homogeneous={} veryHomogeneous={}",
            String.format("%.0f", profile.getSurfaceConsistency() * 100),
            profile.isHomogeneous(), profile.isVeryHomogeneous());
        LOGGER.info("  Dominant block: {}", profile.getDominantSurfaceBlock().getName().getString());

        // Get positions to modify
        List<BlockPos> positions = circleShape ?
            getCirclePositions(surface, radius, messyEdge) :
            getSquarePositions(surface, radius, messyEdge);

        int blocksChanged = 0;

        // Pass 1: Clear vegetation AND WATER (preserve snow only!)
        for (BlockPos pos : positions) {
            BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
            if (surfacePos == null) continue;

            // Clear vegetation and WATER above surface (EXCEPT snow)
            for (int y = 0; y <= 3; y++) {
                BlockPos clearPos = surfacePos.above(y);
                BlockState state = level.getBlockState(clearPos);
                Block block = state.getBlock();

                // Don't remove snow layers!
                if (block == Blocks.SNOW || block == Blocks.POWDER_SNOW) {
                    continue;
                }

                // REMOVE water and vegetation
                if (block == Blocks.WATER || (!state.isAir() && isVegetation(state))) {
                    level.setBlock(clearPos, Blocks.AIR.defaultBlockState(), 3);
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

            if (isSignificantFeature || profile.isVeryHomogeneous()) {
                // Preserve elevation if:
                // - This is a hill/mountain/feature, OR
                // - Area is very homogeneous (>95% one block) - keep it flat/stable
                // Only replace surface block to match dominant type (with bilateral blend)
                Block proposed = profile.getConsistencyAwareSurfaceBlock();
                Block filtered = BilateralBlockFilter.filterBlock(level, surfacePos, proposed);
                level.setBlock(surfacePos, filtered.defaultBlockState(), 3);
                blocksChanged++;
                continue; // NO height modification
            }

            // Calculate target height based on profile's height distribution and smoothness
            int targetY = calculateTargetHeight(pos, surface, profile);
            int heightDiff = targetY - currentY;

            // Limit height changes to prevent aggressive modification (max ±1 block for safety)
            heightDiff = Math.max(-1, Math.min(1, heightDiff));

            // Apply height changes with profile-based blocks
            if (heightDiff > 0) {
                // Build up using CONSISTENCY-AWARE subsurface blocks
                for (int y = 0; y < heightDiff; y++) {
                    Block block = profile.getConsistencyAwareSubsurfaceBlock();
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

            // SURFACE LAYER - Use bilateral filter for smooth blending
            Block proposedSurface = profile.getConsistencyAwareSurfaceBlock();
            Block filteredSurface = BilateralBlockFilter.filterBlock(level, newSurface, proposedSurface);
            level.setBlock(newSurface, filteredSurface.defaultBlockState(), 3);
            blocksChanged++;

            // SUBSURFACE LAYERS - Use CONSISTENCY-AWARE subsurface (stone mountains stay stone!)
            for (int y = 1; y <= 5; y++) {
                Block block = profile.getConsistencyAwareSubsurfaceBlock();
                level.setBlock(newSurface.below(y), block.defaultBlockState(), 3);
                blocksChanged++;
            }
        }

        // Pass 3: Add vegetation based on profile (skip if at/above snow elevation)
        if (profile.getVegetationDensity() > 0) {
            for (BlockPos pos : positions) {
                BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
                if (surfacePos == null) continue;

                // Don't place vegetation above snow line
                if (profile.hasSnow() && surfacePos.getY() >= profile.getSnowElevationThreshold()) {
                    continue;
                }

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

        // Pass 3.5: Apply snow layers at appropriate elevations
        if (profile.hasSnow()) {
            for (BlockPos pos : positions) {
                BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
                if (surfacePos == null) continue;

                // Apply snow if at or above snow threshold
                if (surfacePos.getY() >= profile.getSnowElevationThreshold()) {
                    BlockPos snowPos = surfacePos.above();
                    BlockState aboveState = level.getBlockState(snowPos);

                    // Only place on solid blocks, don't replace existing snow
                    if (aboveState.isAir()) {
                        level.setBlock(snowPos, Blocks.SNOW.defaultBlockState(), 3);
                        blocksChanged++;
                    }
                }
            }
        }

        LOGGER.info("Pass 3 complete (vegetation) - positions processed: {}", positions.size());

        // Pass 4: Repair overhangs (fill floating blocks with support)
        int overhangsFilled = repairOverhangs(level, positions, profile);
        blocksChanged += overhangsFilled;
        LOGGER.info("Pass 4 complete (overhang repair) - filled: {}", overhangsFilled);

        // Pass 5: Apply water features (respects water type rules)
        int waterBlocks = WaterFeatureManager.applyWaterFeatures(level, positions, profile);
        blocksChanged += waterBlocks;
        LOGGER.info("Pass 5 complete (water) - placed: {} (should be 0!)", waterBlocks);
        if (waterBlocks > 0) {
            LOGGER.error("❌ WATER WAS PLACED! This should NEVER happen! Count: {}", waterBlocks);
        }

        // Pass 6: Clean up item drops
        AABB bounds = new AABB(surface).inflate(radius + messyEdge);
        level.getEntitiesOfClass(ItemEntity.class, bounds).forEach(ItemEntity::discard);

        LOGGER.info("Naturalization complete! Total blocks changed: {}", blocksChanged);

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

        // Feature detection: More aggressive to preserve terrain
        double higherRatio = (double) higherCount / totalChecked;
        double lowerRatio = (double) lowerCount / totalChecked;

        // Is a peak if 50%+ neighbors are lower (more aggressive)
        boolean isPeak = lowerRatio > 0.5;

        // Is a valley if 50%+ neighbors are higher
        boolean isValley = higherRatio > 0.5;

        // Is part of steep/varied terrain if mixed elevations
        boolean isSteep = (higherCount > 0 && lowerCount > 0) &&
                         ((higherRatio > 0.3 && lowerRatio > 0.3));

        return isPeak || isValley || isSteep;
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

    /**
     * Repair overhangs - fill air gaps under solid blocks for natural terrain
     */
    private static int repairOverhangs(Level level, List<BlockPos> positions, TerrainProfile profile) {
        int blocksFilled = 0;

        for (BlockPos pos : positions) {
            BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
            if (surfacePos == null) continue;

            // Check downward from surface for air gaps (overhangs)
            for (int y = 1; y <= 10; y++) {
                BlockPos checkPos = surfacePos.below(y);
                BlockState state = level.getBlockState(checkPos);
                BlockState above = level.getBlockState(checkPos.above());

                // Found air with solid block above = overhang/floating block
                if (state.isAir() && !above.isAir() && above.getBlock() != Blocks.WATER) {
                    // Fill with CONSISTENCY-AWARE subsurface block (stone mountains get stone!)
                    Block fillBlock = profile.getConsistencyAwareSubsurfaceBlock();
                    level.setBlock(checkPos, fillBlock.defaultBlockState(), 3);
                    blocksFilled++;
                }

                // Stop at first solid block (no gaps below this)
                if (!state.isAir() && state.getBlock() != Blocks.WATER) {
                    break;
                }
            }
        }

        return blocksFilled;
    }
}

