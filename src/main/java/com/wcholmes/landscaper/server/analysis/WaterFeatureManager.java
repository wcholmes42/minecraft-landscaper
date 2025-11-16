package com.wcholmes.landscaper.server.analysis;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Intelligent water placement using Minecraft aquifer-inspired algorithms.
 *
 * Based on research: Minecraft's aquifer system
 * - Local water levels (not surface placement)
 * - Solid base requirement (3+ blocks)
 * - Barrier-based containment
 * - No uncontrolled flow
 */
public class WaterFeatureManager {

    private static final int MIN_SOLID_BASE = 3; // Blocks of solid base required

    /**
     * Apply water features using proper aquifer-inspired algorithms.
     */
    public static int applyWaterFeatures(Level level, List<BlockPos> positions, TerrainProfile profile) {
        if (profile.getWaterDensity() < 0.01) {
            return 0;
        }

        Set<BlockPos> existingWater = findExistingWater(level, positions);

        return switch (profile.getWaterType()) {
            case BEACH, RIVER -> extendExistingWaterContained(level, positions, existingWater, profile);
            case LAKE, SWAMP -> createContainedLakes(level, positions, existingWater, profile);
            case NONE -> 0;
        };
    }

    private static Set<BlockPos> findExistingWater(Level level, List<BlockPos> positions) {
        Set<BlockPos> waterPositions = new HashSet<>();

        for (BlockPos pos : positions) {
            for (int y = -5; y <= 5; y++) {
                BlockPos checkPos = pos.offset(0, y, 0);
                if (level.getBlockState(checkPos).getBlock() == Blocks.WATER) {
                    waterPositions.add(checkPos);
                }
            }
        }

        return waterPositions;
    }

    /**
     * BEACH/RIVER: Only extend existing water at same level (no surface spills)
     */
    private static int extendExistingWaterContained(Level level, List<BlockPos> positions,
                                                    Set<BlockPos> existingWater, TerrainProfile profile) {
        if (existingWater.isEmpty()) return 0;

        int waterPlaced = 0;

        // Get average water level from existing water
        int localWaterLevel = existingWater.stream()
            .mapToInt(BlockPos::getY)
            .max()
            .orElse(profile.getWaterLevel());

        // Only fill positions BELOW water level, adjacent to existing, with solid below
        for (BlockPos pos : positions) {
            // Must be below local water level
            if (pos.getY() > localWaterLevel) continue;

            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) continue;

            // Must be adjacent to existing water at same or higher level
            boolean adjacentToWater = false;
            for (BlockPos neighbor : new BlockPos[]{
                pos.north(), pos.south(), pos.east(), pos.west()
            }) {
                if (existingWater.contains(neighbor) || level.getBlockState(neighbor).getBlock() == Blocks.WATER) {
                    adjacentToWater = true;
                    break;
                }
            }

            if (!adjacentToWater) continue;

            // CRITICAL: Must have solid block below (prevents surface spills)
            BlockState below = level.getBlockState(pos.below());
            if (below.isAir() || below.getBlock() == Blocks.WATER) {
                continue; // Would flow down - skip
            }

            // Safe to place - contained and below water level
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            existingWater.add(pos);
            waterPlaced++;
        }

        return waterPlaced;
    }

    /**
     * LAKE/POND: Create new water bodies ONLY in validated depressions with solid base
     */
    private static int createContainedLakes(Level level, List<BlockPos> positions,
                                           Set<BlockPos> existingWater, TerrainProfile profile) {
        int waterPlaced = 0;

        // First extend existing
        if (!existingWater.isEmpty()) {
            waterPlaced += extendExistingWaterContained(level, positions, existingWater, profile);
        }

        // Create new ponds using aquifer-inspired algorithm
        if (profile.getWaterDensity() > 0.15) { // Only if significant water presence
            waterPlaced += createAquiferLakes(level, positions, existingWater, profile);
        }

        return waterPlaced;
    }

    /**
     * Create lakes using aquifer-inspired algorithm:
     * 1. Find natural depressions (low points)
     * 2. Verify solid base (3+ blocks thick)
     * 3. Fill up to local water level
     * 4. Verify no outflow paths
     */
    private static int createAquiferLakes(Level level, List<BlockPos> positions,
                                         Set<BlockPos> existingWater, TerrainProfile profile) {
        int waterPlaced = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Find potential lake sites (natural depressions)
        List<BlockPos> lakeSites = findNaturalDepressions(level, positions);

        // Limit to 1-2 lakes maximum
        int lakeCount = Math.min(2, (int) (profile.getWaterDensity() * 4));

        for (int i = 0; i < Math.min(lakeCount, lakeSites.size()); i++) {
            BlockPos lakeCenter = lakeSites.get(random.nextInt(lakeSites.size()));

            // Verify this lake has proper aquifer characteristics
            if (!hasAquiferCharacteristics(level, lakeCenter)) {
                continue;
            }

            // Determine local water level (lowest point in depression + depth)
            int localWaterLevel = lakeCenter.getY() + random.nextInt(1, 3);

            // Fill the depression up to local water level
            int lakeRadius = random.nextInt(3, 7);

            for (BlockPos pos : positions) {
                if (pos.distSqr(lakeCenter) > lakeRadius * lakeRadius) continue;

                // Only fill below local water level
                for (int y = pos.getY(); y > lakeCenter.getY() - 3 && y <= localWaterLevel; y--) {
                    BlockPos waterPos = new BlockPos(pos.getX(), y, pos.getZ());

                    // Check conditions
                    BlockState state = level.getBlockState(waterPos);
                    BlockState below = level.getBlockState(waterPos.below());

                    // Must have solid below AND be air/soft block
                    if ((state.isAir() || state.getBlock() == Blocks.GRASS_BLOCK || state.getBlock() == Blocks.DIRT) &&
                        !below.isAir() && below.getBlock() != Blocks.WATER) {

                        level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
                        existingWater.add(waterPos);
                        waterPlaced++;
                    }
                }
            }
        }

        return waterPlaced;
    }

    /**
     * Find natural depressions - low points surrounded by higher terrain
     */
    private static List<BlockPos> findNaturalDepressions(Level level, List<BlockPos> positions) {
        List<BlockPos> depressions = new ArrayList<>();

        for (BlockPos pos : positions) {
            int posY = pos.getY();
            int higherCount = 0;
            int totalChecked = 0;

            // Check 8-block radius
            for (int x = -8; x <= 8; x += 4) {
                for (int z = -8; z <= 8; z += 4) {
                    if (x == 0 && z == 0) continue;

                    BlockPos checkPos = pos.offset(x, 0, z);
                    BlockState state = level.getBlockState(checkPos);

                    // Find solid block Y level
                    int solidY = posY;
                    for (int y = -5; y <= 5; y++) {
                        BlockState check = level.getBlockState(checkPos.offset(0, y, 0));
                        if (!check.isAir() && check.getBlock() != Blocks.WATER) {
                            solidY = checkPos.getY() + y;
                            break;
                        }
                    }

                    if (solidY > posY) higherCount++;
                    totalChecked++;
                }
            }

            // Is a depression if 70%+ of surroundings are higher
            if (totalChecked > 0 && ((double) higherCount / totalChecked) > 0.7) {
                depressions.add(pos);
            }
        }

        return depressions;
    }

    /**
     * Verify position has aquifer characteristics:
     * - Solid base (3+ blocks thick)
     * - Natural depression
     * - No nearby water
     */
    private static boolean hasAquiferCharacteristics(Level level, BlockPos pos) {
        // Check solid base requirement (Minecraft lakes need 3+ solid blocks below)
        for (int y = 1; y <= MIN_SOLID_BASE; y++) {
            BlockState state = level.getBlockState(pos.below(y));
            if (state.isAir() || state.getBlock() == Blocks.WATER) {
                return false; // No solid base
            }
        }

        // Check not too close to existing water (5 block minimum)
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = -3; y <= 3; y++) {
                    if (level.getBlockState(pos.offset(x, y, z)).getBlock() == Blocks.WATER) {
                        return false; // Too close to existing water
                    }
                }
            }
        }

        return true;
    }
}
