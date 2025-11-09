package com.wcholmes.landscaper.util;

import com.wcholmes.landscaper.config.NaturalizationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * Shared utility methods for terrain operations used by both the staff and highlight renderer.
 * This ensures the highlight visualization matches the actual terrain modification exactly.
 */
public class TerrainUtils {

    // Surface search constants
    private static final int HEIGHT_ABOVE = 3;   // Blocks above click point
    private static final int HEIGHT_BELOW = 10;  // Blocks below click point
    private static final int SURFACE_SEARCH_UP = HEIGHT_ABOVE + 10;   // 13 blocks up
    private static final int SURFACE_SEARCH_DOWN = HEIGHT_BELOW + 10; // 20 blocks down

    // Messy edge constants
    private static final int MESSY_EDGE_FADE_DISTANCE = 2;
    private static final int MESSY_EDGE_MAX_EXTENSION = 3; // 0, 1, or 2 blocks

    /**
     * Finds the surface block at the given starting position by searching up then down.
     * Only recognizes blocks in the safe blocks list as valid surface blocks.
     * This ensures trees, structures, and other obstacles are skipped.
     *
     * @param level The level to search in
     * @param start The starting position to search from
     * @return The surface BlockPos, or null if none found
     */
    public static BlockPos findSurface(Level level, BlockPos start) {
        // Search upward first to handle being underground
        for (int y = 0; y < SURFACE_SEARCH_UP; y++) {
            BlockPos checkPos = start.offset(0, y, 0);
            BlockState current = level.getBlockState(checkPos);

            // Found surface: block in safe list (replaceable terrain like dirt, grass, stone)
            // This skips trees, vegetation, and other non-terrain blocks
            if (NaturalizationConfig.getSafeBlocks().contains(current.getBlock())) {
                return checkPos;
            }
        }

        // If not found above, search downward
        for (int y = 0; y > -SURFACE_SEARCH_DOWN; y--) {
            BlockPos checkPos = start.offset(0, y, 0);
            BlockState current = level.getBlockState(checkPos);

            // Found surface: block in safe list (replaceable terrain)
            if (NaturalizationConfig.getSafeBlocks().contains(current.getBlock())) {
                return checkPos;
            }
        }

        // Return null if no surface found - caller will skip this column
        return null;
    }

    /**
     * Determines if a block at the given offset should be included when messy edge is enabled.
     * Uses deterministic randomness based on absolute world position to ensure highlight matches effect.
     *
     * @param x X offset from center
     * @param z Z offset from center
     * @param radius The configured radius
     * @param isCircle Whether to use circle shape (vs square)
     * @param center The center BlockPos (for deterministic randomness)
     * @return true if this position should be included
     */
    public static boolean shouldApplyMessyEdge(int x, int z, int radius, boolean isCircle, BlockPos center) {
        // effectiveRadius calculation: radius 1 = 0, radius 2 = 1, etc.
        int effectiveRadius = radius - 1;

        // Calculate distance from center
        double distance = isCircle ? Math.sqrt(x * x + z * z) : Math.max(Math.abs(x), Math.abs(z));
        double edgeDistance = effectiveRadius - distance;

        // If we're more than 2 blocks from edge, always include
        if (edgeDistance > MESSY_EDGE_FADE_DISTANCE) {
            return true;
        }

        // Use deterministic random based on absolute world position
        // This ensures highlight and actual effect use the same random values
        // Combine center position with offset for unique seed per block
        long seed = ((long)center.getX() + x) * 31L + ((long)center.getZ() + z) * 37L;
        Random random = new Random(seed);

        // If we're exactly at or beyond effective radius, randomly extend by 1-2 blocks
        if (edgeDistance <= 0) {
            int extension = random.nextInt(MESSY_EDGE_MAX_EXTENSION); // 0, 1, or 2 blocks
            return distance <= effectiveRadius + extension;
        }

        // We're within 2 blocks of edge - randomly fade out
        // Closer to edge = higher chance of being excluded
        double fadeChance = (MESSY_EDGE_FADE_DISTANCE - edgeDistance) / 3.0; // 0% at edge-2, 33% at edge-1, 66% at edge
        return random.nextDouble() > fadeChance;
    }
}
