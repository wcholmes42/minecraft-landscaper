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
 * Analyzes terrain in a 3-chunk (48-block) radius to extract natural characteristics.
 * Larger sample area provides better statistical accuracy for terrain replication.
 */
public class TerrainAnalyzer {

    private static final int CHUNK_RADIUS = 48; // 3 chunks = 48 blocks radius
    private static final int SAMPLE_DENSITY = 2; // Sample every 2 blocks

    /**
     * Analyze terrain around a center position.
     * Samples 48-block radius (3 chunks) in all directions.
     */
    public static TerrainProfile analyze(Level level, BlockPos center) {
        Map<Block, Double> surfaceBlockWeights = new HashMap<>(); // Changed to weighted
        Map<Block, Double> subsurfaceBlockWeights = new HashMap<>(); // Changed to weighted
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

        // Sample the chunk area with DISTANCE WEIGHTING
        for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x += SAMPLE_DENSITY) {
            for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z += SAMPLE_DENSITY) {
                BlockPos samplePos = center.offset(x, 0, z);
                BlockPos surface = TerrainUtils.findSurface(level, samplePos);
                if (surface == null) continue;

                // Calculate distance weight - closer blocks weighted MORE heavily
                double distance = Math.sqrt(x * x + z * z);
                double distanceWeight = Math.exp(-(distance * distance) / (2 * 20 * 20)); // Gaussian
                // Closer = weight ~1.0, at edge = weight ~0.1

                int surfaceY = surface.getY();
                heights.add(surfaceY);
                heightDistribution.merge(surfaceY, 1, Integer::sum);

                // Sample SURFACE block with DISTANCE WEIGHTING
                BlockState surfaceState = level.getBlockState(surface);
                Block surfaceBlock = surfaceState.getBlock();

                // NEVER sample water as surface block!
                if (!surfaceState.isAir() && surfaceBlock != Blocks.WATER) {
                    // Check if this is a NATURAL surface block or exposed subsurface
                    if (isNaturalSurfaceBlock(surfaceBlock)) {
                        // Natural surface - count with distance weight
                        surfaceBlockWeights.merge(surfaceBlock, distanceWeight, Double::sum);
                    } else {
                        // Exposed subsurface (stone outcrop, ore, etc.)
                        // Look at neighbors to find what SHOULD be the surface
                        Block naturalSurface = findNaturalSurfaceNearby(level, surface);
                        if (naturalSurface != null) {
                            surfaceBlockWeights.merge(naturalSurface, distanceWeight, Double::sum);
                        } else {
                            // No natural surface nearby - this IS the natural surface (stone mountain)
                            surfaceBlockWeights.merge(surfaceBlock, distanceWeight, Double::sum);
                        }
                    }
                }

                // Sample SUBSURFACE blocks (y=1-9) - layers below
                for (int y = 1; y < 10; y++) {
                    BlockPos blockPos = surface.below(y);
                    BlockState state = level.getBlockState(blockPos);
                    Block block = state.getBlock();

                    if (!state.isAir()) {
                        subsurfaceBlockWeights.merge(block, distanceWeight, Double::sum);
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

                // Analyze vegetation, snow, and trees
                for (int y = 1; y <= 10; y++) { // Extended to detect trees
                    BlockState state = level.getBlockState(surface.above(y));
                    Block block = state.getBlock();

                    // Check for snow layers
                    if (block == Blocks.SNOW || block == Blocks.POWDER_SNOW) {
                        hasSnowLayers = true;
                        snowElevations.add(surfaceY);
                    }

                    // Detect tree logs (indicates trees present)
                    if (isTreeLog(block)) {
                        // Count corresponding sapling type
                        Block sapling = getSaplingFromLog(block);
                        if (sapling != null) {
                            vegetationCounts.merge(sapling, 3, Integer::sum); // Weight trees heavily
                        }
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

        // Convert weighted maps to integer maps (for compatibility)
        Map<Block, Integer> surfaceBlockCounts = convertWeightsToIntegers(surfaceBlockWeights);
        Map<Block, Integer> subsurfaceBlockCounts = convertWeightsToIntegers(subsurfaceBlockWeights);

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
        // FIXED: Return NONE if no water detected
        if (waterBlocks == 0) {
            return TerrainProfile.WaterType.NONE;
        }

        double waterRatio = (double) waterBlocks / Math.max(1, totalBlocks);

        // Need minimum water presence to classify type
        if (waterRatio < 0.05) {
            return TerrainProfile.WaterType.NONE; // Too little water to classify
        }

        if (beachSand > 20 && avgY < 70 && waterRatio > 0.1)
            return TerrainProfile.WaterType.BEACH;

        if (swampMud > 10 && waterRatio > 0.15)
            return TerrainProfile.WaterType.SWAMP;

        if (flowingWater > waterBlocks / 3 && waterRatio > 0.05) // Added minimum threshold
            return TerrainProfile.WaterType.RIVER;

        return TerrainProfile.WaterType.LAKE;
    }

    /**
     * Convert weighted map to integer map (scale weights to counts)
     */
    private static Map<Block, Integer> convertWeightsToIntegers(Map<Block, Double> weights) {
        Map<Block, Integer> counts = new HashMap<>();

        // Scale weights to reasonable integer range (multiply by 100)
        for (Map.Entry<Block, Double> entry : weights.entrySet()) {
            int scaledCount = (int) Math.round(entry.getValue() * 100);
            if (scaledCount > 0) {
                counts.put(entry.getKey(), scaledCount);
            }
        }

        return counts;
    }

    /**
     * Check if block is a tree log
     */
    private static boolean isTreeLog(Block block) {
        return block == Blocks.OAK_LOG || block == Blocks.BIRCH_LOG ||
               block == Blocks.SPRUCE_LOG || block == Blocks.JUNGLE_LOG ||
               block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG ||
               block == Blocks.CHERRY_LOG || block == Blocks.MANGROVE_LOG;
    }

    /**
     * Get sapling type from log type
     */
    private static Block getSaplingFromLog(Block log) {
        if (log == Blocks.OAK_LOG) return Blocks.OAK_SAPLING;
        if (log == Blocks.BIRCH_LOG) return Blocks.BIRCH_SAPLING;
        if (log == Blocks.SPRUCE_LOG) return Blocks.SPRUCE_SAPLING;
        if (log == Blocks.JUNGLE_LOG) return Blocks.JUNGLE_SAPLING;
        if (log == Blocks.ACACIA_LOG) return Blocks.ACACIA_SAPLING;
        if (log == Blocks.DARK_OAK_LOG) return Blocks.DARK_OAK_SAPLING;
        if (log == Blocks.CHERRY_LOG) return Blocks.CHERRY_SAPLING;
        if (log == Blocks.MANGROVE_PROPAGULE) return Blocks.MANGROVE_PROPAGULE;
        return null;
    }

    /**
     * Check if block is a NATURAL surface block (belongs on top layer naturally)
     */
    private static boolean isNaturalSurfaceBlock(Block block) {
        return block == Blocks.GRASS_BLOCK ||
               block == Blocks.DIRT ||
               block == Blocks.PODZOL ||
               block == Blocks.MYCELIUM ||
               block == Blocks.SAND ||
               block == Blocks.RED_SAND ||
               block == Blocks.GRAVEL ||
               block == Blocks.SNOW_BLOCK ||
               block == Blocks.MUD ||
               block == Blocks.CLAY ||
               block == Blocks.MOSS_BLOCK ||
               block == Blocks.DIRT_PATH ||
               block == Blocks.FARMLAND;
        // Note: Stone, ores, cobblestone are NOT natural surface (exposed subsurface)
    }

    /**
     * Find natural surface blocks in nearby area (for exposed subsurface detection)
     * Returns null if surrounding area is also exposed subsurface (true stone mountain)
     */
    private static Block findNaturalSurfaceNearby(Level level, BlockPos center) {
        Map<Block, Integer> nearbyNaturalBlocks = new HashMap<>();

        // Check CHUNK_RADIUS (48 blocks - 3 chunks) for natural surface blocks
        for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x += SAMPLE_DENSITY * 2) {
            for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z += SAMPLE_DENSITY * 2) {
                if (x == 0 && z == 0) continue;

                BlockPos checkPos = center.offset(x, 0, z);
                BlockState state = level.getBlockState(checkPos);
                Block block = state.getBlock();

                if (isNaturalSurfaceBlock(block)) {
                    nearbyNaturalBlocks.merge(block, 1, Integer::sum);
                }
            }
        }

        // If we found natural surface blocks nearby, return the most common one
        if (!nearbyNaturalBlocks.isEmpty()) {
            return nearbyNaturalBlocks.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        }

        // No natural surface nearby - exposed subsurface IS the surface here
        return null;
    }
}


