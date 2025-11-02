package com.myfirstmod.item;

import com.mojang.logging.LogUtils;
import com.myfirstmod.config.NaturalizationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class NaturalizationStaff extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Configuration for the naturalization effect
    private static final int RADIUS = 5;        // Horizontal radius
    private static final int HEIGHT_ABOVE = 3;  // Blocks above click point
    private static final int HEIGHT_BELOW = 10; // Blocks below click point

    public NaturalizationStaff(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        // Only run on server side
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        LOGGER.info("Naturalization Staff used at position: {}", clickedPos);

        naturalizeTerrain(level, clickedPos);

        return InteractionResult.SUCCESS;
    }

    private void naturalizeTerrain(Level level, BlockPos center) {
        int blocksChanged = 0;
        int landColumns = 0;
        int underwaterColumns = 0;

        LOGGER.info("Starting hybrid naturalization at {}", center);

        // Iterate through the area
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                // Check if within circular radius
                if (x * x + z * z <= RADIUS * RADIUS) {
                    // Find the top solid block in this column
                    BlockPos columnPos = center.offset(x, 0, z);

                    // Check if this specific column is underwater
                    BlockPos surfacePos = findSurface(level, columnPos);
                    if (surfacePos != null) {
                        boolean isColumnUnderwater = isUnderwater(level, surfacePos);
                        if (isColumnUnderwater) {
                            underwaterColumns++;
                        } else {
                            landColumns++;
                        }
                    }

                    // Naturalize this column (each column checked independently!)
                    blocksChanged += naturalizeColumn(level, columnPos);
                }
            }
        }

        // Smart logging based on what was actually naturalized
        String typeMessage;
        if (underwaterColumns > 0 && landColumns > 0) {
            typeMessage = String.format("Hybrid (land: %d columns, water: %d columns)", landColumns, underwaterColumns);
        } else if (underwaterColumns > 0) {
            typeMessage = "Underwater ocean floor";
        } else {
            typeMessage = "Land";
        }

        LOGGER.info("{} naturalization complete! Changed {} blocks", typeMessage, blocksChanged);
    }

    private int naturalizeColumn(Level level, BlockPos pos) {
        int changed = 0;

        // Find the surface level (topmost solid block)
        BlockPos surfacePos = findSurface(level, pos);

        if (surfacePos == null) {
            return 0; // No solid blocks found
        }

        // Check if this is an underwater environment
        boolean isUnderwater = isUnderwater(level, surfacePos);

        // Now naturalize from surface downward
        for (int i = HEIGHT_ABOVE; i >= -HEIGHT_BELOW; i--) {
            BlockPos targetPos = surfacePos.offset(0, i, 0);
            BlockState currentState = level.getBlockState(targetPos);

            // Skip air and liquids (we don't replace water)
            if (currentState.isAir() || currentState.liquid()) {
                continue;
            }

            // SAFETY CHECK: Only replace blocks in the config's safe list!
            if (!NaturalizationConfig.getSafeBlocks().contains(currentState.getBlock())) {
                // This block is not safe to replace (might be a chest, ore, etc.)
                continue;
            }

            // Determine what block should be here
            BlockState newState = determineNaturalBlock(i, isUnderwater);

            // Only change if different (idempotent)
            if (!currentState.is(newState.getBlock())) {
                level.setBlock(targetPos, newState, 3);
                changed++;
            }
        }

        return changed;
    }

    private boolean isUnderwater(Level level, BlockPos surface) {
        // Check if the block above the surface is water
        BlockState above = level.getBlockState(surface.above());
        return above.is(Blocks.WATER);
    }

    private BlockPos findSurface(Level level, BlockPos start) {
        // Search upward first to handle being underground
        for (int y = 0; y < HEIGHT_ABOVE + 10; y++) {
            BlockPos checkPos = start.offset(0, y, 0);
            BlockState above = level.getBlockState(checkPos.above());
            BlockState current = level.getBlockState(checkPos);

            // Found surface: solid block with air/liquid above
            if (!current.isAir() && !current.liquid() &&
                (above.isAir() || above.liquid())) {
                return checkPos;
            }
        }

        // If not found above, search downward
        for (int y = 0; y > -HEIGHT_BELOW - 10; y--) {
            BlockPos checkPos = start.offset(0, y, 0);
            BlockState above = level.getBlockState(checkPos.above());
            BlockState current = level.getBlockState(checkPos);

            // Found surface: solid block with air/liquid above
            if (!current.isAir() && !current.liquid() &&
                (above.isAir() || above.liquid())) {
                return checkPos;
            }
        }

        // Default to original position if no surface found
        return start;
    }

    private BlockState determineNaturalBlock(int relativeY, boolean isUnderwater) {
        if (isUnderwater) {
            // Underwater terrain layers (ocean floor)
            if (relativeY > 0) {
                return Blocks.AIR.defaultBlockState();
            } else if (relativeY == 0) {
                // Ocean floor surface - sand or gravel
                return Blocks.SAND.defaultBlockState();
            } else if (relativeY == -1) {
                // Layer below surface - mix it up with gravel
                return Blocks.GRAVEL.defaultBlockState();
            } else if (relativeY >= -3) {
                // Mid subsurface - sand
                return Blocks.SAND.defaultBlockState();
            } else if (relativeY >= -6) {
                // Deeper - clay layer
                return Blocks.CLAY.defaultBlockState();
            } else {
                // Deep ocean floor - stone
                return Blocks.STONE.defaultBlockState();
            }
        } else {
            // Land terrain layers (normal)
            if (relativeY > 0) {
                return Blocks.AIR.defaultBlockState();
            } else if (relativeY == 0) {
                // Surface layer - grass
                return Blocks.GRASS_BLOCK.defaultBlockState();
            } else if (relativeY >= -3) {
                // Upper subsurface - dirt
                return Blocks.DIRT.defaultBlockState();
            } else {
                // Deep subsurface - stone
                return Blocks.STONE.defaultBlockState();
            }
        }
    }
}
