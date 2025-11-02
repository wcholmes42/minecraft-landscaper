package com.myfirstmod.item;

import com.mojang.logging.LogUtils;
import com.myfirstmod.config.NaturalizationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
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

    // NBT key for storing mode
    private static final String NBT_MODE = "NaturalizationMode";

    // Get the current mode from item NBT
    public static NaturalizationMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBT_MODE)) {
            int ordinal = tag.getInt(NBT_MODE);
            NaturalizationMode[] modes = NaturalizationMode.values();
            if (ordinal >= 0 && ordinal < modes.length) {
                return modes[ordinal];
            }
        }
        return NaturalizationMode.MESSY; // Default mode
    }

    // Set the mode on item NBT
    public static void setMode(ItemStack stack, NaturalizationMode mode) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(NBT_MODE, mode.ordinal());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        NaturalizationMode mode = getMode(stack);
        tooltip.add(Component.literal("§7Mode: §e" + mode.getDisplayName()));
        tooltip.add(Component.literal("§7Press §bV §7to cycle modes"));
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

        // Get current mode from staff
        NaturalizationMode mode = getMode(context.getItemInHand());

        LOGGER.info("Naturalization Staff used at position: {} in mode: {}", clickedPos, mode.getDisplayName());

        // Track resources needed if consume mode is enabled
        Map<Item, Integer> resourcesNeeded = new HashMap<>();

        // Perform the naturalization with current mode
        boolean success = naturalizeTerrain(level, clickedPos, player, mode, resourcesNeeded);

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

    private boolean naturalizeTerrain(Level level, BlockPos center, Player player, NaturalizationMode mode, Map<Item, Integer> resourcesNeeded) {
        int blocksChanged = 0;
        int landColumns = 0;
        int underwaterColumns = 0;

        // Get radius from config
        int radius = NaturalizationConfig.getRadius();

        LOGGER.info("Starting {} naturalization at {} with radius {}", mode.getDisplayName(), center, radius);

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
                    blocksChanged += naturalizeColumn(level, columnPos, mode, resourcesNeeded);
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

    private int naturalizeColumn(Level level, BlockPos pos, NaturalizationMode mode, Map<Item, Integer> resourcesNeeded) {
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

            // Determine what block should be here based on mode
            BlockState newState = determineNaturalBlock(i, isUnderwater, mode);

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

            // Add plants/decorations if mode requires it and this is the surface
            if (i == 0 && mode.shouldAddPlants()) {
                // Get the current surface block (might be newly placed or already existing)
                BlockState surfaceState = level.getBlockState(targetPos);

                // Apply to grass blocks (land) or sand blocks (beach) - 50% on beach
                if (!isUnderwater && surfaceState.is(Blocks.GRASS_BLOCK)) {
                    addSurfaceDecoration(level, targetPos, surfaceState);
                } else if (!isUnderwater && surfaceState.is(Blocks.SAND) && RANDOM.nextDouble() < 0.5) {
                    // 50% chance to bonemeal beach sand
                    addSurfaceDecoration(level, targetPos, surfaceState);
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

        // Return null if no surface found - caller will skip this column
        return null;
    }

    private BlockState determineNaturalBlock(int relativeY, boolean isUnderwater, NaturalizationMode mode) {
        if (isUnderwater) {
            // Underwater terrain layers (ocean floor) - unchanged
            if (relativeY > 0) {
                return Blocks.AIR.defaultBlockState();
            } else if (relativeY == 0) {
                return Blocks.SAND.defaultBlockState();
            } else if (relativeY == -1) {
                return Blocks.GRAVEL.defaultBlockState();
            } else if (relativeY >= -3) {
                return Blocks.SAND.defaultBlockState();
            } else if (relativeY >= -6) {
                return Blocks.CLAY.defaultBlockState();
            } else {
                return Blocks.STONE.defaultBlockState();
            }
        } else {
            // Land terrain layers
            if (relativeY > 0) {
                // Above surface - AIR only
                return Blocks.AIR.defaultBlockState();
            } else if (relativeY == 0) {
                // SURFACE LAYER - use mode
                return getSurfaceBlock(mode);
            } else if (relativeY == -1) {
                // ONE LAYER BELOW SURFACE - use mode
                return getSurfaceBlock(mode);
            } else {
                // DEEP subsurface (relativeY <= -2) - STONE only
                return Blocks.STONE.defaultBlockState();
            }
        }
    }

    private BlockState getSurfaceBlock(NaturalizationMode mode) {
        double roll = RANDOM.nextDouble();

        switch (mode) {
            case GRASS_ONLY:
            case GRASS_WITH_PLANTS:
                // MODE: Pure grass blocks only
                // Plants added separately if GRASS_WITH_PLANTS
                return Blocks.GRASS_BLOCK.defaultBlockState();

            case PATH:
                // MODE: Pure dirt paths only - great for trails
                return Blocks.DIRT_PATH.defaultBlockState();

            case MESSY_PATH:
                // MODE: Mostly paths (75%) with natural variation
                // 75% Dirt Path, 10% Gravel, 10% Grass, 5% Farmland
                if (roll < 0.75) {
                    return Blocks.DIRT_PATH.defaultBlockState();
                } else if (roll < 0.85) {
                    return Blocks.GRAVEL.defaultBlockState();
                } else if (roll < 0.95) {
                    return Blocks.GRASS_BLOCK.defaultBlockState();
                } else {
                    return Blocks.FARMLAND.defaultBlockState();
                }

            case MESSY:
            case MESSY_WITH_PLANTS:
            default:
                // MODE: Natural variation - realistic terrain
                // 85% Grass, 8% Gravel, 5% Path, 2% Farmland
                // Plants added separately if MESSY_WITH_PLANTS
                if (roll < 0.02) {
                    // 2% Farmland (super rare)
                    return Blocks.FARMLAND.defaultBlockState();
                } else if (roll < 0.07) {
                    // 5% Dirt Path (rare)
                    return Blocks.DIRT_PATH.defaultBlockState();
                } else if (roll < 0.15) {
                    // 8% Gravel patches (uncommon)
                    return Blocks.GRAVEL.defaultBlockState();
                } else {
                    // 85% Grass (common)
                    return Blocks.GRASS_BLOCK.defaultBlockState();
                }
        }
    }

    private void addSurfaceDecoration(Level level, BlockPos surfacePos, BlockState surfaceBlock) {
        // Apply actual Minecraft bonemeal effect
        if (level instanceof ServerLevel serverLevel) {
            Block block = surfaceBlock.getBlock();

            // Check if this block can be bonemealed (grass/sand blocks implement BonemealableBlock)
            if (block instanceof BonemealableBlock bonemealable) {
                if (bonemealable.isValidBonemealTarget(serverLevel, surfacePos, surfaceBlock, false)) {
                    if (bonemealable.isBonemealSuccess(serverLevel, serverLevel.random, surfacePos, surfaceBlock)) {
                        // Apply the bonemeal effect - spawns grass, flowers, kelp, etc!
                        bonemealable.performBonemeal(serverLevel, serverLevel.random, surfacePos, surfaceBlock);
                    }
                }
            }
        }
    }
}
