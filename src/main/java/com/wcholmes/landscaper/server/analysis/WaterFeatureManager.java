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

            // Only place if adjacent to existing water AND below water level
            if (adjacentToWater && pos.getY() <= profile.getWaterLevel()) {
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                existingWater.add(pos); // Add to set for next iteration
                waterPlaced++;
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

            // Random pond size (2-5 block radius)
            int pondRadius = random.nextInt(2, 6);

            // Random depth (1-3 blocks)
            int depth = random.nextInt(1, 4);

            // Create pond
            for (BlockPos pos : positions) {
                double distance = Math.sqrt(pos.distSqr(pondCenter));

                if (distance <= pondRadius) {
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
        }

        return waterPlaced;
    }
}
