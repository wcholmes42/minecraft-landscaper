package com.wcholmes.landscaper.common.strategy;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dynamically sampled terrain palette based on surrounding blocks.
 * Stores block frequencies by depth layer for natural terrain blending.
 */
public class TerrainPalette {
    // Depth-stratified block frequencies
    private final Map<Block, Integer> surfaceBlocks = new HashMap<>();
    private final Map<Block, Integer> subsurfaceBlocks = new HashMap<>();
    private final Map<Block, Integer> deepBlocks = new HashMap<>();

    private int surfaceTotal = 0;
    private int subsurfaceTotal = 0;
    private int deepTotal = 0;

    /**
     * Record a surface block sample (relativeY = 0).
     */
    public void addSurfaceBlock(Block block) {
        surfaceBlocks.merge(block, 1, Integer::sum);
        surfaceTotal++;
    }

    /**
     * Record a subsurface block sample (relativeY = -1 to -2).
     */
    public void addSubsurfaceBlock(Block block) {
        subsurfaceBlocks.merge(block, 1, Integer::sum);
        subsurfaceTotal++;
    }

    /**
     * Record a deep layer block sample (relativeY = -3 to -7).
     */
    public void addDeepBlock(Block block) {
        deepBlocks.merge(block, 1, Integer::sum);
        deepTotal++;
    }

    /**
     * Get a random surface block weighted by frequency.
     */
    public Block getSurfaceBlock() {
        return getWeightedRandomBlock(surfaceBlocks, surfaceTotal, Blocks.GRASS_BLOCK);
    }

    /**
     * Get a random subsurface block weighted by frequency.
     */
    public Block getSubsurfaceBlock() {
        return getWeightedRandomBlock(subsurfaceBlocks, subsurfaceTotal, Blocks.DIRT);
    }

    /**
     * Get a random deep layer block weighted by frequency.
     */
    public Block getDeepBlock() {
        return getWeightedRandomBlock(deepBlocks, deepTotal, Blocks.STONE);
    }

    /**
     * Select a block randomly weighted by its frequency in the samples.
     */
    private Block getWeightedRandomBlock(Map<Block, Integer> blockMap, int total, Block fallback) {
        if (blockMap.isEmpty() || total == 0) {
            return fallback;
        }

        int random = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;

        for (Map.Entry<Block, Integer> entry : blockMap.entrySet()) {
            cumulative += entry.getValue();
            if (random < cumulative) {
                return entry.getKey();
            }
        }

        // Fallback (shouldn't reach here)
        return blockMap.keySet().iterator().next();
    }

    /**
     * Get count of samples for debugging.
     */
    public String getStats() {
        return String.format("Surface: %d samples (%d unique), Subsurface: %d samples (%d unique), Deep: %d samples (%d unique)",
            surfaceTotal, surfaceBlocks.size(),
            subsurfaceTotal, subsurfaceBlocks.size(),
            deepTotal, deepBlocks.size());
    }

    /**
     * Check if palette has enough samples to be reliable.
     */
    public boolean hasValidSamples() {
        return surfaceTotal >= 10 && subsurfaceTotal >= 10 && deepTotal >= 10;
    }
}
