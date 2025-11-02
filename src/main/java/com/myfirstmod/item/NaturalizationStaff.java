package com.myfirstmod.item;

import com.mojang.logging.LogUtils;
import com.myfirstmod.config.NaturalizationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class NaturalizationStaff extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RANDOM = new Random();

    // Fixed configuration
    private static final int HEIGHT_ABOVE = 3;  // Blocks above click point
    private static final int HEIGHT_BELOW = 10; // Blocks below click point

    // Variation chances
    private static final double GRAVEL_CHANCE = 0.08;      // 8% chance for gravel patches
    private static final double PATH_CHANCE = 0.05;        // 5% chance for dirt paths
    private static final double FARMLAND_CHANCE = 0.02;    // 2% chance for farmland

    public NaturalizationStaff(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        // Only run on server side
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Check if overworld-only is enabled
        if (NaturalizationConfig.isOverworldOnly() && !level.dimension().equals(Level.OVERWORLD)) {
            if (player != null) {
                player.displayClientMessage(
                    Component.literal("The Naturalization Staff only works in the Overworld!"),
                    true // Action bar message
                );
            }
            return InteractionResult.FAIL;
        }

        BlockPos clickedPos = context.getClickedPos();
        LOGGER.info("Naturalization Staff used at position: {}", clickedPos);

        // Track resources needed if consume mode is enabled
        Map<Item, Integer> resourcesNeeded = new HashMap<>();

        // Perform the naturalization
        boolean success = naturalizeTerrain(level, clickedPos, player, resourcesNeeded);

        // Handle resource consumption
        if (NaturalizationConfig.shouldConsumeResources() && player != null && !player.isCreative()) {
            if (!hasResources(player, resourcesNeeded)) {
                player.displayClientMessage(
                    Component.literal("Not enough resources! Need: " + formatResources(resourcesNeeded)),
                    false
                );
                return InteractionResult.FAIL;
            }

            // Consume the resources
            consumeResources(player, resourcesNeeded);
        }

        return success ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private boolean naturalizeTerrain(Level level, BlockPos center, Player player, Map<Item, Integer> resourcesNeeded) {
        int blocksChanged = 0;
        int landColumns = 0;
        int underwaterColumns = 0;

        // Get radius from config
        int radius = NaturalizationConfig.getRadius();

        LOGGER.info("Starting hybrid naturalization at {} with radius {}", center, radius);

        // Iterate through the area
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Check if within circular radius
                if (x * x + z * z <= radius * radius) {
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
                    blocksChanged += naturalizeColumn(level, columnPos, resourcesNeeded);
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

        // Show message to player
        if (player != null) {
            player.displayClientMessage(
                Component.literal(typeMessage + " naturalization: " + blocksChanged + " blocks changed"),
                true
            );
        }

        return blocksChanged > 0;
    }

    private int naturalizeColumn(Level level, BlockPos pos, Map<Item, Integer> resourcesNeeded) {
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

                // Track resources needed for this block
                if (NaturalizationConfig.shouldConsumeResources()) {
                    Item resourceItem = getResourceItemForBlock(newState);
                    if (resourceItem != null) {
                        resourcesNeeded.merge(resourceItem, 1, Integer::sum);
                    }
                }
            }
        }

        return changed;
    }

    private Item getResourceItemForBlock(BlockState state) {
        // Map blocks to their item equivalents
        if (state.is(Blocks.GRASS_BLOCK)) return Items.DIRT; // Grass requires dirt
        if (state.is(Blocks.DIRT)) return Items.DIRT;
        if (state.is(Blocks.DIRT_PATH)) return Items.DIRT; // Dirt path requires dirt
        if (state.is(Blocks.FARMLAND)) return Items.DIRT; // Farmland requires dirt
        if (state.is(Blocks.STONE)) return Items.COBBLESTONE; // Stone requires cobblestone
        if (state.is(Blocks.SAND)) return Items.SAND;
        if (state.is(Blocks.GRAVEL)) return Items.GRAVEL;
        if (state.is(Blocks.CLAY)) return Items.CLAY_BALL;
        return null; // Unknown block
    }

    private boolean hasResources(Player player, Map<Item, Integer> needed) {
        Inventory inventory = player.getInventory();
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int count = countItem(inventory, entry.getKey());
            if (count < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void consumeResources(Player player, Map<Item, Integer> needed) {
        Inventory inventory = player.getInventory();
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            removeItems(inventory, entry.getKey(), entry.getValue());
        }
    }

    private int countItem(Inventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeItems(Inventory inventory, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
            }
        }
    }

    private String formatResources(Map<Item, Integer> resources) {
        return resources.entrySet().stream()
            .map(e -> e.getValue() + "x " + e.getKey().getDescription().getString())
            .reduce((a, b) -> a + ", " + b)
            .orElse("None");
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
            // Land terrain layers with natural variation
            if (relativeY > 0) {
                return Blocks.AIR.defaultBlockState();
            } else if (relativeY == 0) {
                // Surface layer - grass with rare variants!
                double roll = RANDOM.nextDouble();

                // Super rare: Farmland (2% chance)
                if (roll < FARMLAND_CHANCE) {
                    return Blocks.FARMLAND.defaultBlockState();
                }
                // Rare: Dirt Path (5% chance)
                else if (roll < FARMLAND_CHANCE + PATH_CHANCE) {
                    return Blocks.DIRT_PATH.defaultBlockState();
                }
                // Uncommon: Gravel patches (8% chance)
                else if (roll < FARMLAND_CHANCE + PATH_CHANCE + GRAVEL_CHANCE) {
                    return Blocks.GRAVEL.defaultBlockState();
                }
                // Common: Grass (85% of the time)
                else {
                    return Blocks.GRASS_BLOCK.defaultBlockState();
                }
            } else if (relativeY == -1) {
                // Reduced dirt layer - only 1 layer now (was 3)
                return Blocks.DIRT.defaultBlockState();
            } else {
                // Deep subsurface - stone (starts at -2 now instead of -4)
                return Blocks.STONE.defaultBlockState();
            }
        }
    }
}
