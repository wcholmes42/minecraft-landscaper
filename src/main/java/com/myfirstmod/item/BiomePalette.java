package com.myfirstmod.item;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.ThreadLocalRandom;

public class BiomePalette {
    // No static Random - using ThreadLocalRandom for thread safety

    public static Block getSurfaceBlock(Holder<Biome> biome, NaturalizationMode mode, boolean allowVariation) {
        // Path-only modes override biome
        if (mode.isPathOnly()) {
            return getPathBlock(mode, allowVariation);
        }

        // Get biome-specific surface block
        // Check ocean/beach biomes first (should use sand even if not detected as underwater)
        if (biome.is(Biomes.BEACH) || biome.is(Biomes.STONY_SHORE) || biome.is(Biomes.SNOWY_BEACH)) {
            return getBeachSurfaceBlock(allowVariation);
        } else if (biome.is(Biomes.OCEAN) || biome.is(Biomes.DEEP_OCEAN) || biome.is(Biomes.COLD_OCEAN) ||
                   biome.is(Biomes.DEEP_COLD_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.DEEP_LUKEWARM_OCEAN) ||
                   biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN)) {
            return getOceanSurfaceBlock(allowVariation);
        } else if (biome.is(Biomes.RIVER) || biome.is(Biomes.FROZEN_RIVER)) {
            return getRiverSurfaceBlock(allowVariation);
        } else if (biome.is(Biomes.DESERT) || biome.is(Biomes.BADLANDS) || biome.is(Biomes.ERODED_BADLANDS) || biome.is(Biomes.WOODED_BADLANDS)) {
            return getDesertSurfaceBlock(allowVariation);
        } else if (biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) || biome.is(Biomes.WINDSWEPT_SAVANNA)) {
            return getSavannaSurfaceBlock(allowVariation);
        } else if (biome.is(Biomes.TAIGA) || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)) {
            return getTaigaSurfaceBlock(allowVariation);
        } else if (biome.is(Biomes.JUNGLE) || biome.is(Biomes.SPARSE_JUNGLE) || biome.is(Biomes.BAMBOO_JUNGLE)) {
            return getJungleSurfaceBlock(allowVariation);
        } else if (biome.is(Biomes.MUSHROOM_FIELDS)) {
            return getMushroomSurfaceBlock(allowVariation);
        } else if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            return getSwampSurfaceBlock(allowVariation);
        } else {
            // Default: plains/forest/generic biomes
            return getDefaultSurfaceBlock(allowVariation);
        }
    }

    public static Block getVegetationBlock(Holder<Biome> biome) {
        double roll = ThreadLocalRandom.current().nextDouble();

        if (biome.is(Biomes.DESERT) || biome.is(Biomes.BADLANDS) || biome.is(Biomes.ERODED_BADLANDS) || biome.is(Biomes.WOODED_BADLANDS)) {
            // Desert: 80% dead bush, 20% cactus
            return roll < 0.80 ? Blocks.DEAD_BUSH : Blocks.CACTUS;
        } else if (biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) || biome.is(Biomes.WINDSWEPT_SAVANNA)) {
            // Savanna: 90% tall grass, 10% acacia saplings
            return roll < 0.90 ? Blocks.TALL_GRASS : Blocks.ACACIA_SAPLING;
        } else if (biome.is(Biomes.TAIGA) || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)) {
            // Taiga: 60% ferns, 30% grass, 10% spruce saplings
            if (roll < 0.60) return Blocks.FERN;
            else if (roll < 0.90) return Blocks.GRASS;
            else return Blocks.SPRUCE_SAPLING;
        } else if (biome.is(Biomes.JUNGLE) || biome.is(Biomes.SPARSE_JUNGLE) || biome.is(Biomes.BAMBOO_JUNGLE)) {
            // Jungle: 50% ferns, 30% tall grass, 10% jungle saplings, 10% vines
            if (roll < 0.50) return Blocks.FERN;
            else if (roll < 0.80) return Blocks.TALL_GRASS;
            else if (roll < 0.90) return Blocks.JUNGLE_SAPLING;
            else return Blocks.VINE;
        } else if (biome.is(Biomes.MUSHROOM_FIELDS)) {
            // Mushroom: 60% red mushrooms, 40% brown mushrooms
            return roll < 0.60 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM;
        } else if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            // Swamp: 40% tall grass, 30% ferns, 20% mushrooms, 10% lily pads
            if (roll < 0.40) return Blocks.TALL_GRASS;
            else if (roll < 0.70) return Blocks.FERN;
            else if (roll < 0.90) return ThreadLocalRandom.current().nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM;
            else return Blocks.LILY_PAD;
        } else {
            // Default plains/forest: existing variety (grass + flowers)
            return getDefaultVegetation();
        }
    }

    private static Block getBeachSurfaceBlock(boolean allowVariation) {
        if (!allowVariation) return Blocks.SAND;
        double roll = ThreadLocalRandom.current().nextDouble();
        // 90% sand, 5% gravel, 5% coarse dirt
        if (roll < 0.90) return Blocks.SAND;
        else if (roll < 0.95) return Blocks.GRAVEL;
        else return Blocks.COARSE_DIRT;
    }

    private static Block getOceanSurfaceBlock(boolean allowVariation) {
        if (!allowVariation) return Blocks.SAND;
        double roll = ThreadLocalRandom.current().nextDouble();
        // 80% sand, 15% gravel, 5% clay
        if (roll < 0.80) return Blocks.SAND;
        else if (roll < 0.95) return Blocks.GRAVEL;
        else return Blocks.CLAY;
    }

    private static Block getRiverSurfaceBlock(boolean allowVariation) {
        if (!allowVariation) return Blocks.SAND;
        double roll = ThreadLocalRandom.current().nextDouble();
        // 70% sand, 20% gravel, 10% clay
        if (roll < 0.70) return Blocks.SAND;
        else if (roll < 0.90) return Blocks.GRAVEL;
        else return Blocks.CLAY;
    }

    private static Block getDesertSurfaceBlock(boolean allowVariation) {
        if (!allowVariation) return Blocks.SAND;
        double roll = ThreadLocalRandom.current().nextDouble();
        // 85% sand, 10% red sand, 5% sandstone
        if (roll < 0.85) return Blocks.SAND;
        else if (roll < 0.95) return Blocks.RED_SAND;
        else return Blocks.SANDSTONE;
    }

    private static Block getSavannaSurfaceBlock(boolean allowVariation) {
        if (!allowVariation) return Blocks.GRASS_BLOCK;
        double roll = ThreadLocalRandom.current().nextDouble();
        // 80% grass, 15% coarse dirt, 5% red sand
        if (roll < 0.80) return Blocks.GRASS_BLOCK;
        else if (roll < 0.95) return Blocks.COARSE_DIRT;
        else return Blocks.RED_SAND;
    }

    private static Block getTaigaSurfaceBlock(boolean allowVariation) {
        if (!allowVariation) return Blocks.GRASS_BLOCK;
        double roll = ThreadLocalRandom.current().nextDouble();
        // 75% grass, 20% podzol, 5% coarse dirt
        if (roll < 0.75) return Blocks.GRASS_BLOCK;
        else if (roll < 0.95) return Blocks.PODZOL;
        else return Blocks.COARSE_DIRT;
    }

    private static Block getJungleSurfaceBlock(boolean allowVariation) {
        if (!allowVariation) return Blocks.GRASS_BLOCK;
        double roll = ThreadLocalRandom.current().nextDouble();
        // 70% grass, 25% podzol, 5% mossy cobblestone
        if (roll < 0.70) return Blocks.GRASS_BLOCK;
        else if (roll < 0.95) return Blocks.PODZOL;
        else return Blocks.MOSSY_COBBLESTONE;
    }

    private static Block getMushroomSurfaceBlock(boolean allowVariation) {
        if (!allowVariation) return Blocks.MYCELIUM;
        double roll = ThreadLocalRandom.current().nextDouble();
        // 90% mycelium, 10% dirt
        return roll < 0.90 ? Blocks.MYCELIUM : Blocks.DIRT;
    }

    private static Block getSwampSurfaceBlock(boolean allowVariation) {
        if (!allowVariation) return Blocks.GRASS_BLOCK;
        double roll = ThreadLocalRandom.current().nextDouble();
        // 75% grass, 15% mud, 10% clay
        if (roll < 0.75) return Blocks.GRASS_BLOCK;
        else if (roll < 0.90) return Blocks.MUD;
        else return Blocks.CLAY;
    }

    private static Block getDefaultSurfaceBlock(boolean allowVariation) {
        if (!allowVariation) return Blocks.GRASS_BLOCK;
        double roll = ThreadLocalRandom.current().nextDouble();
        // 85% grass, 8% gravel, 5% path, 2% farmland (original messy mode)
        if (roll < 0.02) return Blocks.FARMLAND;
        else if (roll < 0.07) return Blocks.DIRT_PATH;
        else if (roll < 0.15) return Blocks.GRAVEL;
        else return Blocks.GRASS_BLOCK;
    }

    private static Block getPathBlock(NaturalizationMode mode, boolean allowVariation) {
        if (mode == NaturalizationMode.PATH) {
            return Blocks.DIRT_PATH;
        } else {
            // MESSY_PATH
            if (!allowVariation) return Blocks.DIRT_PATH;
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll < 0.75) return Blocks.DIRT_PATH;
            else if (roll < 0.85) return Blocks.GRAVEL;
            else if (roll < 0.95) return Blocks.GRASS_BLOCK;
            else return Blocks.FARMLAND;
        }
    }

    private static Block getDefaultVegetation() {
        // Original WFC-inspired variety for plains/forests
        double roll = ThreadLocalRandom.current().nextDouble();

        // 70% grass variants
        if (roll < 0.40) {
            return Blocks.GRASS;
        } else if (roll < 0.65) {
            return Blocks.TALL_GRASS;
        } else if (roll < 0.775) {
            // 12.5% 2-block tall plants
            return ThreadLocalRandom.current().nextBoolean() ? Blocks.LARGE_FERN : Blocks.TALL_GRASS;
        } else if (roll < 0.85) {
            // 7.5% fern
            return Blocks.FERN;
        } else if (roll < 0.933) {
            // 8.3% common flowers
            return ThreadLocalRandom.current().nextBoolean() ? Blocks.DANDELION : Blocks.POPPY;
        } else {
            // 6.7% rare flowers
            Block[] rareFlowers = {
                Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET,
                Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY
            };
            return rareFlowers[ThreadLocalRandom.current().nextInt(rareFlowers.length)];
        }
    }
}
