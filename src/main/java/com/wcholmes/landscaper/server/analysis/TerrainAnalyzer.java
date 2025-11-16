package com.wcholmes.landscaper.server.analysis;

import com.wcholmes.landscaper.common.util.TerrainUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Analyzes terrain in a chunk (16-block) radius to extract natural characteristics.
 */
public class TerrainAnalyzer {

    private static final int CHUNK_RADIUS = 16;
    private static final int SAMPLE_DENSITY = 2;

    /**
     * Analyze terrain around a center position.
     */
    public static TerrainProfile analyze(Level level, BlockPos center) {
        Map<Block, Integer> surfaceBlockCounts = new HashMap<>();
        Map<Block, Integer> subsurfaceBlockCounts = new HashMap<>();
        Map<Block, Integer> vegetationCounts = new HashMap<>();
        List<Integer> heights = new ArrayList<>();
        Map<Integer, Integer> heightDistribution = new HashMap<>();
        List<Double> slopes = new ArrayList<>();
        int waterBlockCount = 0;
        int totalBlocks = 0;
        int beachSandCount = 0;
        int flowingWaterCount = 0;
        int swampMudCount = 0;

        // Snow tracking
        List<Integer> snowElevations = new ArrayList<>();
        boolean hasSnowLayers = false;

        // Sample the chunk area
        for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x += SAMPLE_DENSITY) {
            for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z += SAMPLE_DENSITY) {
                BlockPos samplePos = center.offset(x, 0, z);
                BlockPos surface = TerrainUtils.findSurface(level, samplePos);
                if (surface == null) continue;

                int surfaceY = surface.getY();
                heights.add(surfaceY);
                heightDistribution.merge(surfaceY, 1, Integer::sum);

                // Sample SURFACE block (y=0) - count WHATEVER is actually on top
                // Don't filter - stone mountains need stone surface!
                BlockState surfaceState = level.getBlockState(surface);
                Block surfaceBlock = surfaceState.getBlock();
                if (!surfaceState.isAir()) {
                    surfaceBlockCounts.merge(surfaceBlock, 1, Integer::sum);
                }

                // Sample SUBSURFACE blocks (y=1-9) - layers below
                for (int y = 1; y < 10; y++) {
                    BlockPos blockPos = surface.below(y);
                    BlockState state = level.getBlockState(blockPos);
                    Block block = state.getBlock();

                    if (!state.isAir()) {
                        subsurfaceBlockCounts.merge(block, 1, Integer::sum);
                        totalBlocks++;

                        if (block == Blocks.WATER) {
                            waterBlockCount++;
                            if (!state.getFluidState().isSource()) {
                                flowingWaterCount++;
                            }
                        }

                        if (block == Blocks.SAND && surfaceY < 70) beachSandCount++;
                        if (block == Blocks.MUD) swampMudCount++;
                    }
                }

                // Analyze vegetation and snow
                for (int y = 1; y <= 3; y++) {
                    BlockState state = level.getBlockState(surface.above(y));
                    Block block = state.getBlock();

                    // Check for snow layers
                    if (block == Blocks.SNOW || block == Blocks.POWDER_SNOW) {
                        hasSnowLayers = true;
                        snowElevations.add(surfaceY);
                    }

                    if (isVegetation(state)) {
                        vegetationCounts.merge(block, 1, Integer::sum);
                    }
                }

                // Calculate slope
                if (x % 4 == 0 && z % 4 == 0) {
                    slopes.add(calculateSlope(level, surface));
                }
            }
        }

        // Statistics
        int minY = heights.stream().min(Integer::compare).orElse(64);
        int maxY = heights.stream().max(Integer::compare).orElse(64);
        int averageY = (int) heights.stream().mapToInt(Integer::intValue).average().orElse(64);
        int medianY = calculateMedian(heights);

        int surfaceBlocks = Math.max(1, (CHUNK_RADIUS * 2 / SAMPLE_DENSITY) * (CHUNK_RADIUS * 2 / SAMPLE_DENSITY));
        double vegetationDensity = vegetationCounts.values().stream().mapToInt(Integer::intValue).sum() / (double) surfaceBlocks;

        double avgSlope = slopes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double smoothness = 1.0 / (1.0 + avgSlope);

        TerrainProfile.WaterType waterType = detectWaterType(
            waterBlockCount, totalBlocks, beachSandCount, flowingWaterCount, swampMudCount, averageY
        );

        double waterDensity = totalBlocks > 0 ? (double) waterBlockCount / totalBlocks : 0.0;

        // Calculate snow elevation threshold (minimum Y where snow was found)
        int snowThreshold = snowElevations.isEmpty() ?
            9999 : // No snow found - set very high
            snowElevations.stream().min(Integer::compare).orElse(9999);

        return new TerrainProfile(
            surfaceBlockCounts, subsurfaceBlockCounts, vegetationCounts, vegetationDensity,
            minY, maxY, averageY, medianY, heightDistribution,
            smoothness, avgSlope, waterType, waterDensity, averageY,
            hasSnowLayers, snowThreshold
        );
    }

    private static boolean isVegetation(BlockState state) {
        if (state.isAir()) return false;
        Block block = state.getBlock();

        return state.is(BlockTags.FLOWERS) ||
               state.is(BlockTags.SMALL_FLOWERS) ||
               block == Blocks.GRASS || block == Blocks.TALL_GRASS ||
               block == Blocks.FERN || block == Blocks.LARGE_FERN ||
               block == Blocks.DEAD_BUSH || block == Blocks.SEAGRASS ||
               block == Blocks.TALL_SEAGRASS || block == Blocks.KELP ||
               block == Blocks.KELP_PLANT || block == Blocks.LILY_PAD ||
               block == Blocks.BROWN_MUSHROOM || block == Blocks.RED_MUSHROOM ||
               block == Blocks.SWEET_BERRY_BUSH || block == Blocks.SUGAR_CANE ||
               block.getName().getString().contains("sapling");
    }

    private static double calculateSlope(Level level, BlockPos surface) {
        int centerY = surface.getY();
        double totalDiff = 0;
        int count = 0;

        for (BlockPos neighbor : new BlockPos[]{
            surface.north(), surface.south(), surface.east(), surface.west()
        }) {
            BlockPos neighborSurface = TerrainUtils.findSurface(level, neighbor);
            if (neighborSurface != null) {
                totalDiff += Math.abs(neighborSurface.getY() - centerY);
                count++;
            }
        }

        return count > 0 ? totalDiff / count : 0.0;
    }

    private static int calculateMedian(List<Integer> values) {
        if (values.isEmpty()) return 64;
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ?
            (sorted.get(middle - 1) + sorted.get(middle)) / 2 :
            sorted.get(middle);
    }

    private static TerrainProfile.WaterType detectWaterType(
        int waterBlocks, int totalBlocks, int beachSand, int flowingWater, int swampMud, int avgY
    ) {
        if (waterBlocks == 0) return TerrainProfile.WaterType.NONE;

        double waterRatio = (double) waterBlocks / Math.max(1, totalBlocks);

        if (beachSand > 20 && avgY < 70 && waterRatio > 0.1)
            return TerrainProfile.WaterType.BEACH;

        if (swampMud > 10 && waterRatio > 0.15)
            return TerrainProfile.WaterType.SWAMP;

        if (flowingWater > waterBlocks / 3 && waterRatio < 0.3)
            return TerrainProfile.WaterType.RIVER;

        return TerrainProfile.WaterType.LAKE;
    }

    /**
     * Check if a block is a valid natural surface block (not stone/ore/etc.)
     */
    private static boolean isSurfaceBlock(Block block) {
        // Natural surface blocks that should be on top layer
        return block == Blocks.GRASS_BLOCK ||
               block == Blocks.DIRT ||
               block == Blocks.COARSE_DIRT ||
               block == Blocks.PODZOL ||
               block == Blocks.MYCELIUM ||
               block == Blocks.SAND ||
               block == Blocks.RED_SAND ||
               block == Blocks.GRAVEL ||
               block == Blocks.DIRT_PATH ||
               block == Blocks.FARMLAND ||
               block == Blocks.MUD ||
               block == Blocks.CLAY ||
               block == Blocks.SNOW_BLOCK ||
               block == Blocks.SNOW ||
               block == Blocks.POWDER_SNOW ||
               block == Blocks.MOSSY_COBBLESTONE; // Jungle/swamp surface
        // Explicitly EXCLUDE: Stone, ores, cobblestone, deepslate, etc.
    }
}
