package com.wcholmes.landscaper.common.strategy;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.item.BiomePalette;
import com.wcholmes.landscaper.common.item.NaturalizationMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base class for terrain modification strategies with shared utility methods.
 */
public abstract class BaseTerrainStrategy implements TerrainModificationStrategy {
    protected static final Logger LOGGER = LogUtils.getLogger();

    // Constants for terrain depths
    protected static final int HEIGHT_ABOVE = 3;
    protected static final int HEIGHT_BELOW = 10;
    protected static final int TERRAIN_LAYER_SHALLOW = -3;
    protected static final int TERRAIN_LAYER_MID = -6;

    // Block update flags
    protected static final int BLOCK_UPDATE_FLAG = 2; // No drops, send update to clients

    // Vegetation constants
    protected static final double LAND_VEGETATION_CHANCE = 0.075; // 7.5%
    protected static final double UNDERWATER_VEGETATION_CHANCE = 0.075; // 7.5%
    protected static final double MESSY_UNDERWATER_VEGETATION_MULTIPLIER = 3.0; // 22.5%
    protected static final double UNDERWATER_SEAGRASS_RATIO = 0.60; // 60% seagrass, 40% kelp
    protected static final int VEGE_MAX_ADJACENT_SAME = 2;

    // Underwater terrain layer distributions
    protected static final double UNDERWATER_SURF_SAND = 0.70;
    protected static final double UNDERWATER_SURF_GRAVEL = 0.85;
    protected static final double UNDERWATER_SURF_MUD = 0.95;
    protected static final double UNDERWATER_MID_GRAVEL = 0.60;
    protected static final double UNDERWATER_MID_SAND = 0.90;
    protected static final double UNDERWATER_DEEP_SAND = 0.50;
    protected static final double UNDERWATER_DEEP_GRAVEL = 0.80;

    /**
     * Check if a position is underwater (water block above surface).
     */
    protected boolean isUnderwater(Level level, BlockPos surface) {
        BlockState above = level.getBlockState(surface.above());
        return above.is(Blocks.WATER);
    }

    /**
     * Determine the natural block to place at a given relative height.
     */
    protected BlockState determineNaturalBlock(int relativeY, boolean isUnderwater, NaturalizationMode mode,
                                               net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        if (isUnderwater) {
            // Underwater terrain layers
            if (relativeY > 0) {
                return Blocks.AIR.defaultBlockState();
            } else if (relativeY == 0) {
                // Surface layer - messy mode adds variation
                if (mode.allowsVariation()) {
                    double roll = ThreadLocalRandom.current().nextDouble();
                    if (roll < UNDERWATER_SURF_SAND) return Blocks.SAND.defaultBlockState();
                    else if (roll < UNDERWATER_SURF_GRAVEL) return Blocks.GRAVEL.defaultBlockState();
                    else if (roll < UNDERWATER_SURF_MUD) return Blocks.MUD.defaultBlockState();
                    else return Blocks.COARSE_DIRT.defaultBlockState();
                } else {
                    return Blocks.SAND.defaultBlockState();
                }
            } else if (relativeY == -1) {
                // First subsurface - messy mode adds variation
                if (mode.allowsVariation()) {
                    double roll = ThreadLocalRandom.current().nextDouble();
                    if (roll < UNDERWATER_MID_GRAVEL) return Blocks.GRAVEL.defaultBlockState();
                    else if (roll < UNDERWATER_MID_SAND) return Blocks.SAND.defaultBlockState();
                    else return Blocks.CLAY.defaultBlockState();
                } else {
                    return Blocks.GRAVEL.defaultBlockState();
                }
            } else if (relativeY >= TERRAIN_LAYER_SHALLOW) {
                // Mid subsurface - messy mode adds variation
                if (mode.allowsVariation()) {
                    double roll = ThreadLocalRandom.current().nextDouble();
                    if (roll < UNDERWATER_DEEP_SAND) return Blocks.SAND.defaultBlockState();
                    else if (roll < UNDERWATER_DEEP_GRAVEL) return Blocks.GRAVEL.defaultBlockState();
                    else return Blocks.CLAY.defaultBlockState();
                } else {
                    return Blocks.SAND.defaultBlockState();
                }
            } else if (relativeY >= TERRAIN_LAYER_MID) {
                return Blocks.CLAY.defaultBlockState();
            } else {
                return Blocks.STONE.defaultBlockState();
            }
        } else {
            // Land terrain layers
            if (relativeY > 0) {
                return Blocks.AIR.defaultBlockState();
            } else if (relativeY == 0) {
                // Surface - use biome-specific palette
                Block surfaceBlock = BiomePalette.getSurfaceBlock(biome, mode, mode.allowsVariation());
                return surfaceBlock.defaultBlockState();
            } else if (relativeY >= TERRAIN_LAYER_SHALLOW) {
                // 1-3 blocks below surface - DIRT
                return Blocks.DIRT.defaultBlockState();
            } else {
                // Deep subsurface - STONE
                return Blocks.STONE.defaultBlockState();
            }
        }
    }

    /**
     * Check if vegetation should be placed at this position (WFC-inspired).
     * Prevents clustering of identical plants.
     */
    protected boolean shouldPlaceVegetation(Level level, BlockPos pos, BlockState plantToPlace) {
        BlockPos[] neighbors = {
            pos.north(), pos.south(), pos.east(), pos.west(),
            pos.north().east(), pos.north().west(),
            pos.south().east(), pos.south().west()
        };

        int sameTypeCount = 0;
        for (BlockPos neighbor : neighbors) {
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.is(plantToPlace.getBlock())) {
                sameTypeCount++;
            }
        }

        return sameTypeCount < VEGE_MAX_ADJACENT_SAME;
    }

    /**
     * Map a block state to its resource item equivalent.
     */
    protected Item getResourceItemForBlock(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)) return Items.DIRT;
        if (state.is(Blocks.DIRT)) return Items.DIRT;
        if (state.is(Blocks.DIRT_PATH)) return Items.DIRT;
        if (state.is(Blocks.FARMLAND)) return Items.DIRT;
        if (state.is(Blocks.PODZOL)) return Items.DIRT;
        if (state.is(Blocks.MYCELIUM)) return Items.DIRT;
        if (state.is(Blocks.MUD)) return Items.MUD;
        if (state.is(Blocks.COARSE_DIRT)) return Items.DIRT;
        if (state.is(Blocks.STONE)) return Items.COBBLESTONE;
        if (state.is(Blocks.MOSSY_COBBLESTONE)) return Items.MOSSY_COBBLESTONE;
        if (state.is(Blocks.SAND)) return Items.SAND;
        if (state.is(Blocks.RED_SAND)) return Items.RED_SAND;
        if (state.is(Blocks.SANDSTONE)) return Items.SANDSTONE;
        if (state.is(Blocks.GRAVEL)) return Items.GRAVEL;
        if (state.is(Blocks.CLAY)) return Items.CLAY_BALL;
        if (state.is(Blocks.WATER)) return null; // Water doesn't consume resources
        return null;
    }

    /**
     * Clean up item drops in the area (prevents duplication exploits).
     */
    protected void cleanupItemDrops(Level level, BlockPos center, int radius) {
        if (!level.isClientSide()) {
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(radius),
                entity -> true
            ).forEach(itemEntity -> itemEntity.discard());
        }
    }
}
