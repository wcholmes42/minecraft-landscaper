package com.wcholmes.landscaper.server.analysis;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Intelligent water feature placement based on terrain profile.
 *
 * Rules:
 * - BEACH/RIVER: Only extend existing water, never create new
 * - LAKE/POND: Can create new water bodies with appropriate size/depth
 * - Always honors existing water features
 */
public class WaterFeatureManager {

    /**
     * Apply water features to modified terrain based on profile analysis.
     * This should be called AFTER terrain modification is complete.
     *
     * @param level The world level
     * @param positions All positions in the modified area
     * @param profile Analyzed terrain profile
     * @return Number of water blocks placed
     */
    public static int applyWaterFeatures(Level level, List<BlockPos> positions, TerrainProfile profile) {
        if (profile.getWaterDensity() < 0.01) {
            return 0; // No water in sample area
        }

        // Find existing water in target area
        Set<BlockPos> existingWater = findExistingWater(level, positions);

        // Apply based on water type
        return switch (profile.getWaterType()) {
            case BEACH, RIVER -> extendExistingWater(level, positions, existingWater, profile);
            case LAKE, SWAMP -> createOrExtendWater(level, positions, existingWater, profile);
            case NONE -> 0;
        };
    }

    /**
     * Find all existing water blocks in the target area
     */
    private static Set<BlockPos> findExistingWater(Level level, List<BlockPos> positions) {
        Set<BlockPos> waterPositions = new HashSet<>();

        for (BlockPos pos : positions) {
            // Check current position and a few blocks up/down
            for (int y = -3; y <= 3; y++) {
                BlockPos checkPos = pos.offset(0, y, 0);
                BlockState state = level.getBlockState(checkPos);
                if (state.getBlock() == Blocks.WATER) {
                    waterPositions.add(checkPos);
                }
            }
        }

        return waterPositions;
    }

    /**
     * BEACH/RIVER: Only extend existing water, never create new
     */
    private static int extendExistingWater(Level level, List<BlockPos> positions,
                                          Set<BlockPos> existingWater, TerrainProfile profile) {
        if (existingWater.isEmpty()) {
            return 0; // No existing water to extend
        }

        int waterPlaced = 0;

        // For each position, check if it's adjacent to existing water
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);

            // Only fill air blocks at or below water level
            if (!state.isAir()) continue;

            // Check if adjacent to existing water
            boolean adjacentToWater = false;
            for (BlockPos neighbor : new BlockPos[]{
                pos.north(), pos.south(), pos.east(), pos.west(),
                pos.above(), pos.below()
            }) {
                if (existingWater.contains(neighbor) || level.getBlockState(neighbor).getBlock() == Blocks.WATER) {
                    adjacentToWater = true;
                    break;
                }
            }

            // Only place if adjacent to existing water AND below water level AND contained
            if (adjacentToWater && pos.getY() <= profile.getWaterLevel()) {
                // Final check: verify this water block is contained
                if (isWaterBlockContained(level, pos)) {
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                    existingWater.add(pos); // Add to set for next iteration
                    waterPlaced++;
                }
            }
        }

        return waterPlaced;
    }

    /**
     * LAKE/POND/SWAMP: Can create new water bodies with random size/depth
     */
    private static int createOrExtendWater(Level level, List<BlockPos> positions,
                                          Set<BlockPos> existingWater, TerrainProfile profile) {
        int waterPlaced = 0;

        // If existing water, extend it first
        if (!existingWater.isEmpty()) {
            waterPlaced += extendExistingWater(level, positions, existingWater, profile);
        }

        // Create new water bodies based on water density in profile
        // Higher water density = more likely to create ponds/puddles
        if (profile.getWaterDensity() > 0.1) {
            waterPlaced += createRandomPonds(level, positions, existingWater, profile);
        }

        return waterPlaced;
    }

    /**
     * Create random ponds/puddles in the area
     */
    private static int createRandomPonds(Level level, List<BlockPos> positions,
                                        Set<BlockPos> existingWater, TerrainProfile profile) {
        int waterPlaced = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Number of ponds based on water density
        int pondCount = (int) (profile.getWaterDensity() * 3); // 0-3 ponds

        for (int i = 0; i < pondCount; i++) {
            // Pick random center for pond
            BlockPos pondCenter = positions.get(random.nextInt(positions.size()));

            // Don't create pond if too close to existing water
            boolean tooClose = false;
            for (BlockPos existing : existingWater) {
                if (existing.distSqr(pondCenter) < 25) { // 5 block minimum distance
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;

            // Check if this location is a natural depression (containment check)
            if (!isNaturalDepression(level, pondCenter, 5)) {
                continue; // Skip - water would flow uncontained
            }

            // Random pond size (2-5 block radius)
            int pondRadius = random.nextInt(2, 6);

            // Random depth (1-3 blocks)
            int depth = random.nextInt(1, 4);

            // Collect pond positions first for validation
            List<BlockPos> pondPositions = new ArrayList<>();
            for (BlockPos pos : positions) {
                double distance = Math.sqrt(pos.distSqr(pondCenter));
                if (distance <= pondRadius) {
                    pondPositions.add(pos);
                }
            }

            // Verify entire pond is contained before placing ANY water
            if (!isPondContained(level, pondPositions, depth)) {
                continue; // Water would spill - skip this pond
            }

            // Safe to place - pond is contained
            for (BlockPos pos : pondPositions) {
                // Fill from surface down to depth
                BlockPos surfacePos = level.getBlockState(pos).isAir() ?
                    pos.below() : pos;

                for (int d = 0; d < depth; d++) {
                    BlockPos waterPos = surfacePos.below(d);
                    BlockState state = level.getBlockState(waterPos);

                    // Replace air or soft blocks with water
                    if (state.isAir() || state.getBlock() == Blocks.DIRT || state.getBlock() == Blocks.GRASS_BLOCK) {
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
     * Check if a position is in a natural depression (lower than surroundings)
     * This prevents placing water on flat/raised areas where it would flow away
     */
    private static boolean isNaturalDepression(Level level, BlockPos center, int checkRadius) {
        int centerY = center.getY();
        int higherNeighbors = 0;
        int totalNeighbors = 0;

        // Check surrounding blocks in a radius
        for (int x = -checkRadius; x <= checkRadius; x++) {
            for (int z = -checkRadius; z <= checkRadius; z++) {
                if (x == 0 && z == 0) continue; // Skip center

                BlockPos checkPos = center.offset(x, 0, z);
                BlockState state = level.getBlockState(checkPos);

                // Find solid block at this position
                BlockPos solidPos = checkPos;
                for (int y = -5; y <= 5; y++) {
                    BlockState checkState = level.getBlockState(checkPos.offset(0, y, 0));
                    if (!checkState.isAir() && checkState.getBlock() != Blocks.WATER) {
                        solidPos = checkPos.offset(0, y, 0);
                        break;
                    }
                }

                totalNeighbors++;
                if (solidPos.getY() >= centerY) {
                    higherNeighbors++;
                }
            }
        }

        // Must be lower than at least 60% of surroundings to be a depression
        return totalNeighbors > 0 && ((double) higherNeighbors / totalNeighbors) > 0.6;
    }

    /**
     * Verify pond is fully contained - water won't flow out
     */
    private static boolean isPondContained(Level level, List<BlockPos> pondPositions, int depth) {
        // For each water position, check if it has containment
        for (BlockPos waterPos : pondPositions) {
            // Check all horizontal neighbors
            for (BlockPos neighbor : new BlockPos[]{
                waterPos.north(), waterPos.south(),
                waterPos.east(), waterPos.west()
            }) {
                // If neighbor is outside pond area, check if it's solid (contains the water)
                if (!pondPositions.contains(neighbor)) {
                    // Check if there's a solid block at this level or below
                    boolean contained = false;
                    for (int d = 0; d <= depth; d++) {
                        BlockState state = level.getBlockState(neighbor.below(d));
                        if (!state.isAir() && state.getBlock() != Blocks.WATER) {
                            // Found solid block - this edge is contained
                            contained = true;
                            break;
                        }
                    }

                    if (!contained) {
                        // Water would flow out here - pond not contained
                        return false;
                    }
                }
            }
        }

        return true; // All edges contained
    }

    /**
     * Check if a single water block would be contained (won't flow infinitely)
     */
    private static boolean isWaterBlockContained(Level level, BlockPos waterPos) {
        // Check all 4 horizontal directions
        for (BlockPos neighbor : new BlockPos[]{
            waterPos.north(), waterPos.south(),
            waterPos.east(), waterPos.west()
        }) {
            BlockState neighborState = level.getBlockState(neighbor);

            // If neighbor is air, water would flow there - check if that's contained
            if (neighborState.isAir()) {
                // Check below the air block - if there's no solid block, water flows down
                BlockState below = level.getBlockState(neighbor.below());
                if (below.isAir() || below.getBlock() == Blocks.WATER) {
                    // Water would flow into air and potentially down - not contained
                    return false;
                }
            }
        }

        return true; // Water is contained
    }
}

