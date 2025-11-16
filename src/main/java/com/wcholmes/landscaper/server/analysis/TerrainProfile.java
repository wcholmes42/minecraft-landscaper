package com.wcholmes.landscaper.server.analysis;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stores comprehensive terrain analysis data from a sampled area.
 */
public class TerrainProfile {

    private final Map<Block, Integer> surfaceBlockPalette;
    private final Map<Block, Integer> subsurfaceBlockPalette;
    private final Map<Block, Double> surfaceBlockFrequency;
    private final Map<Block, Integer> vegetationPalette;
    private final double vegetationDensity;
    private final int minY;
    private final int maxY;
    private final int averageY;
    private final int medianY;
    private final Map<Integer, Integer> heightDistribution;
    private final double smoothness;
    private final double slopeVariation;
    private final WaterType waterType;
    private final double waterDensity;
    private final int waterLevel;
    private final boolean hasSnow;
    private final int snowElevationThreshold;

    public enum WaterType {
        NONE, BEACH, RIVER, LAKE, SWAMP
    }

    public TerrainProfile(Map<Block, Integer> surfaceBlockPalette,
                         Map<Block, Integer> subsurfaceBlockPalette,
                         Map<Block, Integer> vegetationPalette,
                         double vegetationDensity,
                         int minY, int maxY, int averageY, int medianY,
                         Map<Integer, Integer> heightDistribution,
                         double smoothness,
                         double slopeVariation,
                         WaterType waterType,
                         double waterDensity,
                         int waterLevel,
                         boolean hasSnow,
                         int snowElevationThreshold) {
        this.surfaceBlockPalette = surfaceBlockPalette;
        this.subsurfaceBlockPalette = subsurfaceBlockPalette;
        this.vegetationPalette = vegetationPalette;
        this.vegetationDensity = vegetationDensity;
        this.minY = minY;
        this.maxY = maxY;
        this.averageY = averageY;
        this.medianY = medianY;
        this.heightDistribution = heightDistribution;
        this.smoothness = smoothness;
        this.slopeVariation = slopeVariation;
        this.waterType = waterType;
        this.waterDensity = waterDensity;
        this.waterLevel = waterLevel;
        this.hasSnow = hasSnow;
        this.snowElevationThreshold = snowElevationThreshold;

        // Calculate normalized surface block frequencies
        this.surfaceBlockFrequency = new HashMap<>();
        int totalSurfaceBlocks = surfaceBlockPalette.values().stream().mapToInt(Integer::intValue).sum();
        if (totalSurfaceBlocks > 0) {
            for (Map.Entry<Block, Integer> entry : surfaceBlockPalette.entrySet()) {
                surfaceBlockFrequency.put(entry.getKey(), (double) entry.getValue() / totalSurfaceBlocks);
            }
        }
    }

    // Getters
    public Map<Block, Integer> getSurfaceBlockPalette() { return surfaceBlockPalette; }
    public Map<Block, Integer> getSubsurfaceBlockPalette() { return subsurfaceBlockPalette; }
    public Map<Block, Integer> getBlockPalette() { return surfaceBlockPalette; } // For compatibility
    public Map<Block, Double> getBlockFrequency() { return surfaceBlockFrequency; }
    public Map<Block, Integer> getVegetationPalette() { return vegetationPalette; }
    public double getVegetationDensity() { return vegetationDensity; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public int getAverageY() { return averageY; }
    public int getMedianY() { return medianY; }
    public int getHeightRange() { return maxY - minY; }
    public Map<Integer, Integer> getHeightDistribution() { return heightDistribution; }
    public double getSmoothness() { return smoothness; }
    public double getSlopeVariation() { return slopeVariation; }
    public WaterType getWaterType() { return waterType; }
    public double getWaterDensity() { return waterDensity; }
    public int getWaterLevel() { return waterLevel; }
    public boolean hasSnow() { return hasSnow; }
    public int getSnowElevationThreshold() { return snowElevationThreshold; }

    public Block getDominantSurfaceBlock() {
        return surfaceBlockPalette.entrySet().stream()
            .filter(e -> !e.getKey().getName().getString().contains("air") &&
                        !e.getKey().getName().getString().contains("water"))
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(Blocks.GRASS_BLOCK);
    }

    public Block getDominantVegetation() {
        return vegetationPalette.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * Calculate surface block consistency (0.0 = diverse, 1.0 = mono-block)
     * Returns the frequency of the dominant block
     */
    public double getSurfaceConsistency() {
        if (surfaceBlockFrequency.isEmpty()) return 1.0;

        // Consistency = frequency of most common block
        return surfaceBlockFrequency.values().stream()
            .max(Double::compare)
            .orElse(0.0);
    }

    /**
     * Check if terrain is homogeneous (one block type dominates >90%)
     */
    public boolean isHomogeneous() {
        return getSurfaceConsistency() > 0.90;
    }

    /**
     * Check if terrain is VERY homogeneous (>95% - pure mono-block)
     */
    public boolean isVeryHomogeneous() {
        return getSurfaceConsistency() > 0.95;
    }

    /**
     * Get surface block with consistency-aware selection:
     * - Homogeneous areas (>90% one type): Always use dominant block
     * - Diverse areas: Use weighted random
     */
    public Block getConsistencyAwareSurfaceBlock() {
        if (isHomogeneous()) {
            // Mono-block area - use dominant block EXCLUSIVELY (no variation)
            return getDominantSurfaceBlock();
        } else {
            // Diverse area - use weighted random
            return getWeightedRandomSurfaceBlock();
        }
    }

    /**
     * Get weighted random SURFACE block (for top layer)
     */
    public Block getWeightedRandomSurfaceBlock() {
        return getWeightedRandom(surfaceBlockPalette, Blocks.GRASS_BLOCK);
    }

    /**
     * Get weighted random SUBSURFACE block (for layers below)
     */
    public Block getWeightedRandomSubsurfaceBlock() {
        return getWeightedRandom(subsurfaceBlockPalette, Blocks.DIRT);
    }

    private Block getWeightedRandom(Map<Block, Integer> palette, Block defaultBlock) {
        int totalWeight = palette.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight == 0) return defaultBlock;

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;

        for (Map.Entry<Block, Integer> entry : palette.entrySet()) {
            cumulative += entry.getValue();
            if (random < cumulative) {
                return entry.getKey();
            }
        }

        return palette.keySet().iterator().next();
    }

    public Block getWeightedRandomVegetation() {
        if (vegetationPalette.isEmpty()) return null;

        int totalWeight = vegetationPalette.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight == 0) return null;

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;

        for (Map.Entry<Block, Integer> entry : vegetationPalette.entrySet()) {
            cumulative += entry.getValue();
            if (random < cumulative) {
                return entry.getKey();
            }
        }

        return vegetationPalette.keySet().iterator().next();
    }

    @Override
    public String toString() {
        return String.format(
            "TerrainProfile{surface=%d, subsurface=%d, veg=%d, density=%.1f%%, height=%d-%d(avg=%d), smooth=%.2f, water=%s(%.1f%%)}",
            surfaceBlockPalette.size(), subsurfaceBlockPalette.size(), vegetationPalette.size(), vegetationDensity * 100,
            minY, maxY, averageY, smoothness, waterType, waterDensity * 100
        );
    }
}
