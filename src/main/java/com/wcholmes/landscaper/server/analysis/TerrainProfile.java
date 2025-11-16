package com.wcholmes.landscaper.server.analysis;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stores comprehensive terrain analysis data from a sampled area.
 */
public class TerrainProfile {

    private final Map<Block, Integer> blockPalette;
    private final Map<Block, Double> blockFrequency;
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

    public enum WaterType {
        NONE, BEACH, RIVER, LAKE, SWAMP
    }

    public TerrainProfile(Map<Block, Integer> blockPalette,
                         Map<Block, Integer> vegetationPalette,
                         double vegetationDensity,
                         int minY, int maxY, int averageY, int medianY,
                         Map<Integer, Integer> heightDistribution,
                         double smoothness,
                         double slopeVariation,
                         WaterType waterType,
                         double waterDensity,
                         int waterLevel) {
        this.blockPalette = blockPalette;
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

        // Calculate normalized frequencies
        this.blockFrequency = new HashMap<>();
        int totalBlocks = blockPalette.values().stream().mapToInt(Integer::intValue).sum();
        if (totalBlocks > 0) {
            for (Map.Entry<Block, Integer> entry : blockPalette.entrySet()) {
                blockFrequency.put(entry.getKey(), (double) entry.getValue() / totalBlocks);
            }
        }
    }

    // Getters
    public Map<Block, Integer> getBlockPalette() { return blockPalette; }
    public Map<Block, Double> getBlockFrequency() { return blockFrequency; }
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

    public Block getDominantBlock() {
        return blockPalette.entrySet().stream()
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
     * Get weighted random block from palette (for terrain generation)
     */
    public Block getWeightedRandomBlock() {
        int totalWeight = blockPalette.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight == 0) return Blocks.GRASS_BLOCK;

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;

        for (Map.Entry<Block, Integer> entry : blockPalette.entrySet()) {
            cumulative += entry.getValue();
            if (random < cumulative) {
                return entry.getKey();
            }
        }

        return blockPalette.keySet().iterator().next();
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
            "TerrainProfile{blocks=%d, veg=%d, density=%.1f%%, height=%d-%d(avg=%d), smooth=%.2f, water=%s(%.1f%%)}",
            blockPalette.size(), vegetationPalette.size(), vegetationDensity * 100,
            minY, maxY, averageY, smoothness, waterType, waterDensity * 100
        );
    }
}
