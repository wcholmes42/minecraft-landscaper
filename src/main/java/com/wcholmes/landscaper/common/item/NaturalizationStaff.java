package com.wcholmes.landscaper.common.item;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.strategy.*;
import com.wcholmes.landscaper.common.util.TerrainUtils;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class NaturalizationStaff extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Cooldown tracking for DoS protection
    private static final Map<UUID, Long> lastUseTime = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 1000; // 1 second cooldown
    private static final long COOLDOWN_CLEANUP_THRESHOLD = 300000; // 5 minutes
    private static long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL = 60000; // Run cleanup every 60 seconds

    // Constants for vegetation chances
    private static final double LAND_VEGETATION_CHANCE = 0.075; // 7.5%
    private static final double UNDERWATER_VEGETATION_CHANCE = 0.075; // 7.5%
    private static final double MESSY_UNDERWATER_VEGETATION_MULTIPLIER = 3.0; // 22.5%

    // Fixed configuration
    private static final int HEIGHT_ABOVE = 3;  // Blocks above click point
    private static final int HEIGHT_BELOW = 10; // Blocks below click point

    // Variation chances
    private static final double GRAVEL_CHANCE = 0.08;      // 8% chance for gravel patches
    private static final double PATH_CHANCE = 0.05;        // 5% chance for dirt paths
    private static final double FARMLAND_CHANCE = 0.02;    // 2% chance for farmland

    // Block update flags
    private static final int BLOCK_UPDATE_FLAG = 2; // No drops, send update to clients

    // Vegetation distribution percentages (WFC-inspired mix)
    private static final double VEGE_SHORT_GRASS = 0.40;     // 40% short grass
    private static final double VEGE_TALL_GRASS = 0.65;      // 25% tall grass (cumulative 65%)
    private static final double VEGE_LARGE_PLANTS = 0.775;   // 12.5% 2-block plants (cumulative 77.5%)
    private static final double VEGE_FERN = 0.85;            // 7.5% fern (cumulative 85%)
    private static final double VEGE_COMMON_FLOWER = 0.933;  // 8.3% common flowers (cumulative 93.3%)
    // Remaining 6.7% = rare flowers
    private static final int VEGE_MAX_ADJACENT_SAME = 2;     // Reject if 2+ same plants nearby

    // Underwater vegetation
    private static final double UNDERWATER_SEAGRASS_RATIO = 0.60; // 60% seagrass, 40% kelp

    // Terrain layer distribution - Underwater
    private static final double UNDERWATER_SURF_SAND = 0.70;        // 70% sand
    private static final double UNDERWATER_SURF_GRAVEL = 0.85;      // 15% gravel (cumulative)
    private static final double UNDERWATER_SURF_MUD = 0.95;         // 10% mud (cumulative)
    // Remaining 5% = coarse dirt

    private static final double UNDERWATER_MID_GRAVEL = 0.60;       // 60% gravel
    private static final double UNDERWATER_MID_SAND = 0.90;         // 30% sand (cumulative)
    // Remaining 10% = clay

    private static final double UNDERWATER_DEEP_SAND = 0.50;        // 50% sand
    private static final double UNDERWATER_DEEP_GRAVEL = 0.80;      // 30% gravel (cumulative)
    // Remaining 20% = clay

    // Terrain layer depths (relative to surface)
    private static final int TERRAIN_LAYER_SHALLOW = -3;  // 1-3 blocks below
    private static final int TERRAIN_LAYER_MID = -6;      // 4-6 blocks below
    // Below -6 = deep layer

    // Path mode distribution
    private static final double PATH_MODE_PATH = 0.75;      // 75% dirt path
    private static final double PATH_MODE_GRAVEL = 0.85;    // 10% gravel (cumulative)
    private static final double PATH_MODE_GRASS = 0.95;     // 10% grass (cumulative)
    // Remaining 5% = farmland

    // Strategy instances (reuse for performance)
    private static final TerrainModificationStrategy REPLACE_STRATEGY = new ReplaceStrategy();
    private static final TerrainModificationStrategy FILL_STRATEGY = new FillStrategy();
    private static final TerrainModificationStrategy FLATTEN_STRATEGY = new FlattenStrategy();
    private static final TerrainModificationStrategy FLOOD_STRATEGY = new FloodStrategy();
    private static final TerrainModificationStrategy NATURALIZE_STRATEGY = new NaturalizeStrategy();

    public NaturalizationStaff(Properties properties) {
        super(properties);
    }

    /**
     * Get the appropriate strategy for a given mode.
     */
    private static TerrainModificationStrategy getStrategy(NaturalizationMode mode) {
        switch (mode.getStrategyType()) {
            case REPLACE:
                return REPLACE_STRATEGY;
            case FILL:
                return FILL_STRATEGY;
            case FLATTEN:
                return FLATTEN_STRATEGY;
            case FLOOD:
                return FLOOD_STRATEGY;
            case NATURALIZE:
                return NATURALIZE_STRATEGY;
            default:
                return REPLACE_STRATEGY; // Fallback
        }
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

        // Get player-specific settings early (for multiplayer) or use global config (for singleplayer)
        com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings = null;
        if (player != null && level instanceof ServerLevel) {
            playerSettings = com.wcholmes.landscaper.common.config.PlayerConfig.getPlayerSettings(player.getUUID());
        }

        // Cooldown check to prevent DoS/spam abuse
        if (player != null && !player.isCreative()) {
            UUID playerId = player.getUUID();
            long now = System.currentTimeMillis();

            // Deterministic cleanup: remove entries older than 5 minutes every 60 seconds
            if ((now - lastCleanupTime) > CLEANUP_INTERVAL) {
                lastUseTime.entrySet().removeIf(entry -> (now - entry.getValue()) > COOLDOWN_CLEANUP_THRESHOLD);
                lastCleanupTime = now;
            }

            Long lastUse = lastUseTime.get(playerId);
            if (lastUse != null && (now - lastUse) < COOLDOWN_MS) {
                player.displayClientMessage(
                    Component.literal("Staff on cooldown! (" + (COOLDOWN_MS - (now - lastUse)) + "ms)"),
                    true
                );
                return InteractionResult.FAIL;
            }
            lastUseTime.put(playerId, now);
        }

        // Check if overworld-only is enabled (use player-specific setting)
        boolean overworldOnly = playerSettings != null ? playerSettings.overworldOnly : NaturalizationConfig.isOverworldOnly();
        if (overworldOnly && !level.dimension().equals(Level.OVERWORLD)) {
            if (player != null) {
                player.displayClientMessage(
                    Component.literal("The Naturalization Staff only works in the Overworld!"),
                    true // Action bar message
                );
            }
            return InteractionResult.FAIL;
        }

        // Allow clicking on any face (top, sides, bottom)
        // Future: can add mode-specific behavior for cliff faces here

        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedBlock = level.getBlockState(clickedPos);
        BlockState blockAbove = level.getBlockState(clickedPos.above());

        // Get current mode from staff
        NaturalizationMode mode = getMode(context.getItemInHand());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Staff used at pos={}, block={}, blockAbove={}, mode={}",
                clickedPos, clickedBlock.getBlock(), blockAbove.getBlock(), mode.getDisplayName());
        }

        // Use player-specific consumeResources setting
        boolean consumeResources = playerSettings != null ? playerSettings.consumeResources : NaturalizationConfig.shouldConsumeResources();

        // If resource consumption is enabled, pre-calculate what's needed BEFORE placing blocks
        if (consumeResources && player != null && !player.isCreative()) {
            Map<Item, Integer> resourcesNeeded = new HashMap<>();

            // Dry run to calculate resources (don't place blocks)
            calculateResourcesNeeded(level, clickedPos, mode, resourcesNeeded, playerSettings);

            // Check if player has the resources
            Map<Item, Integer> missingResources = getMissingResources(player, resourcesNeeded);
            if (!missingResources.isEmpty()) {
                player.displayClientMessage(
                    Component.literal("Not enough resources! Need: " + formatResources(missingResources)),
                    false
                );
                return InteractionResult.FAIL;
            }

            // Player has resources - place blocks
            // Pass empty map to prevent double-tracking
            boolean success = naturalizeTerrain(level, clickedPos, player, mode, new HashMap<>(), playerSettings);

            // Consume only what we originally calculated (not double-counted)
            if (success) {
                consumeResources(player, resourcesNeeded);
            }

            return success ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        } else {
            // No resource consumption - just place blocks
            Map<Item, Integer> resourcesNeeded = new HashMap<>();
            boolean success = naturalizeTerrain(level, clickedPos, player, mode, resourcesNeeded, playerSettings);
            return success ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
    }

    private void calculateResourcesNeeded(Level level, BlockPos center, NaturalizationMode mode, Map<Item, Integer> resourcesNeeded,
                                          com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {
        // Delegate to strategy
        TerrainModificationStrategy strategy = getStrategy(mode);
        strategy.calculateResourcesNeeded(level, center, mode, resourcesNeeded, playerSettings);
    }

    private boolean naturalizeTerrain(Level level, BlockPos center, Player player, NaturalizationMode mode, Map<Item, Integer> resourcesNeeded,
                                      com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {
        // Delegate to strategy
        TerrainModificationStrategy strategy = getStrategy(mode);
        int blocksChanged = strategy.modify(level, center, player, mode, resourcesNeeded, playerSettings);
        return blocksChanged > 0;
    }

    // Helper methods for resource management remain here (not strategy-specific)

    private Map<Item, Integer> getMissingResources(Player player, Map<Item, Integer> needed) {
        Map<Item, Integer> missing = new HashMap<>();
        Inventory inventory = player.getInventory();

        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int have = countItem(inventory, entry.getKey());
            int need = entry.getValue();

            if (have < need) {
                missing.put(entry.getKey(), need - have);
            }
        }

        return missing;
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
        // Mark inventory as changed to sync to client
        inventory.setChanged();
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
}
