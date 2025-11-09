package com.wcholmes.landscaper.common.item;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.common.config.NaturalizationConfig;
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

        // Only allow clicking on the TOP face of blocks
        if (context.getClickedFace() != net.minecraft.core.Direction.UP) {
            if (player != null) {
                player.displayClientMessage(
                    Component.literal("You must click on the top of a block!"),
                    true // Action bar message
                );
            }
            return InteractionResult.FAIL;
        }

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
        // Dry run to calculate what resources would be needed without placing blocks
        // Use player-specific radius or fall back to global config
        int radius = playerSettings != null ? playerSettings.radius : NaturalizationConfig.getRadius();
        int effectiveRadius = radius - 1; // radius 1 = 0 range, radius 2 = 1 range, etc.

        for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
            for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                BlockPos columnPos = center.offset(dx, 0, dz);
                BlockPos surfacePos = TerrainUtils.findSurface(level, columnPos);

                if (surfacePos == null) continue;

                // Detect biome for this column
                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(surfacePos);
                boolean isUnderwater = isUnderwater(level, surfacePos);

                // Simulate what blocks would be placed
                for (int i = 0; i >= -HEIGHT_BELOW; i--) {
                    BlockPos targetPos = surfacePos.offset(0, i, 0);
                    BlockState currentState = level.getBlockState(targetPos);

                    if (currentState.isAir() || currentState.liquid()) continue;
                    if (!NaturalizationConfig.getSafeBlocks().contains(currentState.getBlock())) continue;

                    BlockState newState = determineNaturalBlock(i, isUnderwater, mode, biome);

                    // Check if placing grass-like block but something above - would place dirt instead
                    if (i == 0 && (newState.is(Blocks.GRASS_BLOCK) || newState.is(Blocks.PODZOL) ||
                                  newState.is(Blocks.MYCELIUM) || newState.is(Blocks.MUD))) {
                        BlockState blockAbove = level.getBlockState(targetPos.above());
                        if (!blockAbove.isAir() && !blockAbove.liquid()) {
                            newState = Blocks.DIRT.defaultBlockState();
                        }
                    }

                    if (!currentState.is(newState.getBlock())) {
                        Item resourceItem = getResourceItemForBlock(newState);
                        if (resourceItem != null) {
                            resourcesNeeded.merge(resourceItem, 1, Integer::sum);
                        }
                    }
                }
            }
        }
    }

    private boolean naturalizeTerrain(Level level, BlockPos center, Player player, NaturalizationMode mode, Map<Item, Integer> resourcesNeeded,
                                      com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {
        int blocksChanged = 0;
        int landColumns = 0;
        int underwaterColumns = 0;

        // Use player-specific settings or fall back to global config
        int radius = playerSettings != null ? playerSettings.radius : NaturalizationConfig.getRadius();
        int effectiveRadius = radius - 1; // radius 1 = 0 range, radius 2 = 1 range, etc.

        // Get player position for safety checks (prevent falling through world)
        BlockPos playerPos = player != null ? player.blockPosition() : null;

        boolean isCircle = NaturalizationConfig.isCircleShape();
        int messyEdgeExtension = playerSettings != null ? playerSettings.messyEdgeExtension : NaturalizationConfig.getMessyEdgeExtension();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting {} naturalization at {} with radius {}, shape={}, messyEdgeExtension={}",
                mode.getDisplayName(), center, radius, isCircle ? "circle" : "square", messyEdgeExtension);
        }

        // Expand search range if messy edge is enabled
        int searchRadius = messyEdgeExtension > 0 ? effectiveRadius + messyEdgeExtension : effectiveRadius;

        int vegetationCleared = 0;
        int columnsProcessed = 0;

        // Iterate through the area
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                boolean withinRadius;

                if (messyEdgeExtension > 0) {
                    // Use messy edge logic for all blocks in expanded range
                    withinRadius = TerrainUtils.shouldApplyMessyEdge(x, z, radius, isCircle, center, messyEdgeExtension);
                } else {
                    // Standard circle or square check
                    withinRadius = isCircle ? (x * x + z * z <= effectiveRadius * effectiveRadius) : true;
                }

                if (withinRadius) {
                    columnsProcessed++;

                    // Find the top solid block in this column
                    BlockPos columnPos = center.offset(x, 0, z);

                    // Check if this specific column is underwater
                    BlockPos surfacePos = TerrainUtils.findSurface(level, columnPos);
                    if (surfacePos != null) {
                        boolean isColumnUnderwater = isUnderwater(level, surfacePos);
                        if (isColumnUnderwater) {
                            underwaterColumns++;
                        } else {
                            landColumns++;
                        }
                    }

                    // Naturalize this column (each column checked independently!)
                    int result = naturalizeColumn(level, columnPos, mode, resourcesNeeded, playerPos, playerSettings);
                    blocksChanged += result;
                    if (result > 0) vegetationCleared++;
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

        LOGGER.info("{} naturalization complete! Changed {} blocks | {} columns | searchRadius={}", typeMessage, blocksChanged, columnsProcessed, searchRadius);

        // Show message to player with debug info
        if (player != null) {
            player.displayClientMessage(
                Component.literal(typeMessage + " naturalization: " + blocksChanged + " blocks | " + columnsProcessed + " columns | R:" + searchRadius),
                true
            );
        }

        return blocksChanged > 0;
    }

    private int naturalizeColumn(Level level, BlockPos pos, NaturalizationMode mode, Map<Item, Integer> resourcesNeeded, BlockPos playerPos,
                                 com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {
        int changed = 0;

        // Find the surface level (topmost solid block)
        BlockPos surfacePos = TerrainUtils.findSurface(level, pos);

        if (surfacePos == null) {
            return 0; // No solid blocks found
        }

        // Safety check: Don't modify columns directly under the player (prevent falling)
        if (playerPos != null && pos.getX() == playerPos.getX() && pos.getZ() == playerPos.getZ()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Skipping column at {} - player is standing here", pos);
            }
            return 0;
        }

        // Detect biome at surface position for biome-specific palettes
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(surfacePos);

        // Check if this is an underwater environment
        boolean isUnderwater = isUnderwater(level, surfacePos);

        // Column processing (logged at DEBUG level only to avoid spam)

        // PASS 1: Clear blocks to AIR (destroys vegetation above, terrain at/below surface)
        for (int i = HEIGHT_ABOVE; i >= -HEIGHT_BELOW; i--) {
            BlockPos targetPos = surfacePos.offset(0, i, 0);
            BlockState currentState = level.getBlockState(targetPos);

            // Skip air and liquids (we don't replace water)
            if (currentState.isAir() || currentState.liquid()) {
                continue;
            }

            if (i >= 0) {
                // AT or ABOVE surface: Check for vegetation first before checking terrain
                boolean isVegetation = currentState.canBeReplaced() ||
                                      currentState.getBlock() instanceof net.minecraft.world.level.block.BushBlock ||
                                      currentState.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock ||
                                      currentState.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock ||
                                      currentState.is(Blocks.KELP) ||
                                      currentState.is(Blocks.KELP_PLANT) ||
                                      currentState.is(Blocks.SEAGRASS) ||
                                      currentState.is(Blocks.TALL_SEAGRASS);

                if (isVegetation) {
                    level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);

                    // For double-height plants (tall grass, large fern, etc.), clear BOTH halves
                    if (currentState.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock) {
                        // Clear the block above (upper half)
                        BlockPos abovePos = targetPos.above();
                        BlockState aboveState = level.getBlockState(abovePos);
                        if (aboveState.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock) {
                            level.setBlock(abovePos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);
                        }

                        // Clear the block below (lower half) - in case we encountered the upper half first
                        BlockPos belowPos = targetPos.below();
                        BlockState belowState = level.getBlockState(belowPos);
                        if (belowState.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock) {
                            level.setBlock(belowPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);
                        }
                    }
                } else if (i == 0) {
                    // AT surface (i=0): Also clear terrain if it's in safe blocks list
                    if (NaturalizationConfig.getSafeBlocks().contains(currentState.getBlock())) {
                        level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);
                    }
                }
            } else {
                // BELOW surface (i < 0): Only clear safe terrain blocks
                if (NaturalizationConfig.getSafeBlocks().contains(currentState.getBlock())) {
                    level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);
                }
            }
        }

        // PASS 2: Place terrain blocks from surface downward
        for (int i = 0; i >= -HEIGHT_BELOW; i--) {
            BlockPos targetPos = surfacePos.offset(0, i, 0);

            // Use column-wide underwater detection (whole column is underwater or not)
            BlockState newState = determineNaturalBlock(i, isUnderwater, mode, biome);

            // Special check: If placing grass-like block at surface - DON'T convert to dirt
            // Grass with vegetation above is fine! Only convert if there's a tree/structure
            // For now, just place grass - vegetation gets cleared/replaced properly
            // if (i == 0 && !isUnderwater && (newState.is(Blocks.GRASS_BLOCK) || newState.is(Blocks.PODZOL) ||
            //               newState.is(Blocks.MYCELIUM) || newState.is(Blocks.MUD))) {
            //     BlockState blockAbove = level.getBlockState(targetPos.above());
            //     // Skip this check - it was causing grass→dirt conversion issues near water
            // }

            // Place the block (flag 2 = no drops)
            level.setBlock(targetPos, newState, BLOCK_UPDATE_FLAG);
            changed++;

            // Track resources needed for this block (use player-specific setting)
            boolean consumeResources = playerSettings != null ? playerSettings.consumeResources : NaturalizationConfig.shouldConsumeResources();
            if (consumeResources) {
                Item resourceItem = getResourceItemForBlock(newState);
                if (resourceItem != null) {
                    resourcesNeeded.merge(resourceItem, 1, Integer::sum);
                }
            }

            // Handle surface decorations based on mode
            if (i == 0) {
                BlockState surfaceState = level.getBlockState(targetPos);

                if (mode.shouldAddPlants()) {
                    // Add plants mode - Biome-specific WFC-inspired vegetation
                    if (!isUnderwater && (surfaceState.is(Blocks.GRASS_BLOCK) || surfaceState.is(Blocks.SAND) ||
                        surfaceState.is(Blocks.PODZOL) || surfaceState.is(Blocks.MYCELIUM) || surfaceState.is(Blocks.MUD))) {
                        // Use player-specific vegetation density
                        NaturalizationConfig.VegetationDensity density = playerSettings != null ?
                            playerSettings.vegetationDensity : NaturalizationConfig.getVegetationDensity();
                        if (ThreadLocalRandom.current().nextDouble() < density.getChance()) {
                            BlockPos abovePos = targetPos.above();
                            // Only place if air above
                            if (level.getBlockState(abovePos).isAir()) {
                                // Get biome-specific vegetation
                                Block plantBlock = BiomePalette.getVegetationBlock(biome);
                                BlockState plantState = plantBlock.defaultBlockState();
                                // WFC check: ensure aesthetic variety (no identical neighbors)
                                if (shouldPlaceVegetation(level, abovePos, plantState)) {
                                    level.setBlock(abovePos, plantState, BLOCK_UPDATE_FLAG);
                                }
                            }
                        }
                    } else if (isUnderwater && (surfaceState.is(Blocks.SAND) || surfaceState.is(Blocks.GRAVEL) ||
                                                surfaceState.is(Blocks.MUD) || surfaceState.is(Blocks.CLAY))) {
                        // Underwater shoreline vegetation: kelp and seagrass
                        // Messy modes get 3x more vegetation
                        NaturalizationConfig.VegetationDensity density = playerSettings != null ?
                            playerSettings.vegetationDensity : NaturalizationConfig.getVegetationDensity();
                        double baseChance = density.getChance();
                        double vegetationChance = mode.allowsVariation() ?
                            baseChance * MESSY_UNDERWATER_VEGETATION_MULTIPLIER :
                            baseChance;
                        if (ThreadLocalRandom.current().nextDouble() < vegetationChance) {
                            BlockPos abovePos = targetPos.above();
                            BlockState aboveState = level.getBlockState(abovePos);
                            // Only place if water above
                            if (aboveState.is(Blocks.WATER)) {
                                // 60% seagrass, 40% kelp
                                Block plantBlock = ThreadLocalRandom.current().nextDouble() < UNDERWATER_SEAGRASS_RATIO ? Blocks.SEAGRASS : Blocks.KELP;
                                level.setBlock(abovePos, plantBlock.defaultBlockState(), BLOCK_UPDATE_FLAG);
                            }
                        }
                    }
                }
            }
        }

        // PASS 3: Clean up item drops in the area
        if (!level.isClientSide()) {
            int cleanupRadius = playerSettings != null ? playerSettings.radius : NaturalizationConfig.getRadius();
            BlockPos center = surfacePos;
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(cleanupRadius),
                entity -> true
            ).forEach(itemEntity -> itemEntity.discard());
        }

        return changed;
    }


    private boolean shouldPlaceVegetation(Level level, BlockPos pos, BlockState plantToPlace) {
        // Wave Function Collapse inspired aesthetics:
        // Check neighbors to avoid placing identical plants adjacent
        // This creates natural variation and avoids artificial-looking clusters

        BlockPos[] neighbors = {
            pos.north(), pos.south(), pos.east(), pos.west(),
            pos.north().east(), pos.north().west(),
            pos.south().east(), pos.south().west()
        };

        // Count identical plants in immediate vicinity
        int sameTypeCount = 0;
        for (BlockPos neighbor : neighbors) {
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.is(plantToPlace.getBlock())) {
                sameTypeCount++;
            }
        }

        // Reject if 2+ identical plants nearby (enforces variety)
        return sameTypeCount < VEGE_MAX_ADJACENT_SAME;
    }

    private Item getResourceItemForBlock(BlockState state) {
        // Map blocks to their item equivalents
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
        return null; // Unknown block
    }

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

    private boolean isUnderwater(Level level, BlockPos surface) {
        // Check if the block above the surface is water
        BlockState above = level.getBlockState(surface.above());
        return above.is(Blocks.WATER);
    }

    private BlockState determineNaturalBlock(int relativeY, boolean isUnderwater, NaturalizationMode mode, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        if (isUnderwater) {
            // Underwater terrain layers
            if (relativeY > 0) {
                return Blocks.AIR.defaultBlockState();
            } else if (relativeY == 0) {
                // Surface layer - messy mode adds variation
                if (mode.allowsVariation()) {
                    double roll = ThreadLocalRandom.current().nextDouble();
                    // 70% sand, 15% gravel, 10% mud, 5% coarse dirt
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
                    // 60% gravel, 30% sand, 10% clay
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
                    // 50% sand, 30% gravel, 20% clay
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
                // Above surface - AIR only
                return Blocks.AIR.defaultBlockState();
            } else if (relativeY == 0) {
                // SURFACE ONLY - use biome-specific palette
                Block surfaceBlock = BiomePalette.getSurfaceBlock(biome, mode, mode.allowsVariation());
                return surfaceBlock.defaultBlockState();
            } else if (relativeY >= TERRAIN_LAYER_SHALLOW) {
                // 1-3 blocks below surface - DIRT
                return Blocks.DIRT.defaultBlockState();
            } else {
                // DEEP subsurface (relativeY <= -4) - STONE only
                return Blocks.STONE.defaultBlockState();
            }
        }
    }


}
