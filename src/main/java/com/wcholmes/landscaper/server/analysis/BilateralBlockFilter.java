package com.wcholmes.landscaper.server.analysis;

import com.wcholmes.landscaper.common.util.TerrainUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Bilateral filter for block placement - preserves terrain edges while smoothing.
 *
 * Based on image processing bilateral filter:
 * - Spatial weight: Distance from center (Gaussian)
 * - Range weight: Block type similarity
 * - Combines both: Preserves sharp changes, smooths gradual ones
 */
public class BilateralBlockFilter {

    private static final double SPATIAL_SIGMA = 2.0; // Spatial Gaussian spread
    private static final int KERNEL_RADIUS = 3; // 3-block radius (7x7 kernel)

    /**
     * Select block using bilateral filter - blends with neighbors while preserving edges
     *
     * @param level The world level
     * @param pos Position to place block
     * @param proposedBlock Block from profile analysis
     * @return Filtered block (blended with neighbors)
     */
    public static Block filterBlock(Level level, BlockPos pos, Block proposedBlock) {
        Map<Block, Double> blockWeights = new HashMap<>();
        double totalWeight = 0.0;

        // Sample 7x7 neighborhood (3-block radius)
        for (int x = -KERNEL_RADIUS; x <= KERNEL_RADIUS; x++) {
            for (int z = -KERNEL_RADIUS; z <= KERNEL_RADIUS; z++) {
                BlockPos neighborPos = pos.offset(x, 0, z);
                BlockPos neighborSurface = TerrainUtils.findSurface(level, neighborPos);
                if (neighborSurface == null) continue;

                BlockState neighborState = level.getBlockState(neighborSurface);
                Block neighborBlock = neighborState.getBlock();
                if (neighborState.isAir()) continue;

                // Calculate SPATIAL weight (Gaussian - distance-based)
                double distance = Math.sqrt(x * x + z * z);
                double spatialWeight = gaussianWeight(distance, SPATIAL_SIGMA);

                // Calculate RANGE weight (block similarity)
                double rangeWeight = blockSimilarity(proposedBlock, neighborBlock);

                // BILATERAL FILTER: Multiply spatial and range weights
                double bilateralWeight = spatialWeight * rangeWeight;

                blockWeights.merge(neighborBlock, bilateralWeight, Double::sum);
                totalWeight += bilateralWeight;
            }
        }

        if (blockWeights.isEmpty()) {
            return proposedBlock; // Fallback
        }

        // Return block with highest combined weight (most similar to neighbors)
        // NEVER return water or air!
        Block selected = blockWeights.entrySet().stream()
            .filter(e -> e.getKey() != Blocks.WATER && e.getKey() != Blocks.AIR)
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(proposedBlock);

        // Safety check
        if (selected == Blocks.WATER || selected == Blocks.AIR) {
            return proposedBlock; // Fallback to proposed
        }

        return selected;
    }

    /**
     * Gaussian weight function - higher weight for closer blocks
     * Weight follows bell curve: nearby = high, distant = low
     */
    private static double gaussianWeight(double distance, double sigma) {
        return Math.exp(-(distance * distance) / (2 * sigma * sigma));
    }

    /**
     * Block similarity function - how similar are two block types?
     * Returns 0.0 (completely different) to 1.0 (identical)
     */
    private static double blockSimilarity(Block block1, Block block2) {
        if (block1.equals(block2)) {
            return 1.0; // Identical blocks
        }

        // Block families - similar blocks get high similarity score
        if (isSameFamily(block1, block2)) {
            return 0.7; // Same family (e.g., grass_block and dirt)
        }

        // Different families
        return 0.1; // Low similarity (preserves edges)
    }

    /**
     * Check if blocks are in the same family (related types)
     */
    private static boolean isSameFamily(Block b1, Block b2) {
        // Grass family
        if (isGrassFamily(b1) && isGrassFamily(b2)) return true;

        // Stone family
        if (isStoneFamily(b1) && isStoneFamily(b2)) return true;

        // Sand family
        if (isSandFamily(b1) && isSandFamily(b2)) return true;

        return false;
    }

    private static boolean isGrassFamily(Block block) {
        return block.getName().getString().contains("grass") ||
               block.getName().getString().contains("dirt") ||
               block.getName().getString().contains("podzol") ||
               block.getName().getString().contains("mycelium");
    }

    private static boolean isStoneFamily(Block block) {
        return block.getName().getString().contains("stone") ||
               block.getName().getString().contains("cobble") ||
               block.getName().getString().contains("andesite") ||
               block.getName().getString().contains("granite") ||
               block.getName().getString().contains("diorite");
    }

    private static boolean isSandFamily(Block block) {
        return block.getName().getString().contains("sand") ||
               block.getName().getString().contains("gravel");
    }
}
