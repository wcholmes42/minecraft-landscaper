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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class NaturalizationStaff extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Cooldown tracking for DoS protection
    private static final Map<UUID, Long> lastUseTime = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 1000; // 1 second cooldown
    private static final long COOLDOWN_CLEANUP_THRESHOLD = 300000; // 5 minutes

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

        // Cooldown check to prevent DoS/spam abuse
        if (player != null && !player.isCreative()) {
            UUID playerId = player.getUUID();
            long now = System.currentTimeMillis();

            // Periodic cleanup: remove entries older than 5 minutes (prevents memory leak)
            if (ThreadLocalRandom.current().nextInt(100) == 0) { // 1% chance per use
                lastUseTime.entrySet().removeIf(entry -> (now - entry.getValue()) > COOLDOWN_CLEANUP_THRESHOLD);
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

        LOGGER.info("🎯 RIGHT-CLICKED at {}: block={}, blockAbove={}, mode={}",
            clickedPos, clickedBlock.getBlock(), blockAbove.getBlock(), mode.getDisplayName());

        // If resource consumption is enabled, pre-calculate what's needed BEFORE placing blocks
        if (NaturalizationConfig.shouldConsumeResources() && player != null && !player.isCreative()) {
            Map<Item, Integer> resourcesNeeded = new HashMap<>();

            // Dry run to calculate resources (don't place blocks)
            calculateResourcesNeeded(level, clickedPos, mode, resourcesNeeded);

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
            boolean success = naturalizeTerrain(level, clickedPos, player, mode, new HashMap<>());

            // Consume only what we originally calculated (not double-counted)
            if (success) {
                consumeResources(player, resourcesNeeded);
            }

            return success ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        } else {
            // No resource consumption - just place blocks
            Map<Item, Integer> resourcesNeeded = new HashMap<>();
            boolean success = naturalizeTerrain(level, clickedPos, player, mode, resourcesNeeded);
            return success ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
    }

    private void calculateResourcesNeeded(Level level, BlockPos center, NaturalizationMode mode, Map<Item, Integer> resourcesNeeded) {
        // Dry run to calculate what resources would be needed without placing blocks
        int radius = NaturalizationConfig.getRadius();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos columnPos = center.offset(dx, 0, dz);
                BlockPos surfacePos = findSurface(level, columnPos);

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

        // Detect biome at surface position for biome-specific palettes
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(surfacePos);

        // Check if this is an underwater environment
        boolean isUnderwater = isUnderwater(level, surfacePos);

        LOGGER.info("Column at {}: isUnderwater={}", surfacePos, isUnderwater);

        // PASS 1: Clear blocks to AIR (destroys vegetation above, terrain at/below surface)
        for (int i = HEIGHT_ABOVE; i >= -HEIGHT_BELOW; i--) {
            BlockPos targetPos = surfacePos.offset(0, i, 0);
            BlockState currentState = level.getBlockState(targetPos);

            // Skip air and liquids (we don't replace water)
            if (currentState.isAir() || currentState.liquid()) {
                continue;
            }

            if (i > 0) {
                // ABOVE surface: Clear all vegetation (grass, flowers, saplings, kelp, seagrass, etc.)
                // Check if it's a plant-type block (extends BushBlock) or is replaceable
                boolean isVegetation = currentState.canBeReplaced() ||
                                      currentState.getBlock() instanceof net.minecraft.world.level.block.BushBlock ||
                                      currentState.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock ||
                                      currentState.is(Blocks.KELP) ||
                                      currentState.is(Blocks.KELP_PLANT) ||
                                      currentState.is(Blocks.SEAGRASS) ||
                                      currentState.is(Blocks.TALL_SEAGRASS);

                if (isVegetation) {
                    LOGGER.info("PASS1: Clearing vegetation at i={}, block={}", i, currentState.getBlock());
                    level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 2);
                }
            } else {
                // AT or BELOW surface: Only clear safe terrain blocks
                if (NaturalizationConfig.getSafeBlocks().contains(currentState.getBlock())) {
                    LOGGER.info("PASS1: Clearing safe block at i={}, block={}", i, currentState.getBlock());
                    level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 2);
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
            if (i == 0) {
                LOGGER.info("PASS2: Placing {} at surface (i=0), isUnderwater={}", newState.getBlock(), isUnderwater);
            }
            level.setBlock(targetPos, newState, 2);
            changed++;

            // Track resources needed for this block
            if (NaturalizationConfig.shouldConsumeResources()) {
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
                        // 7.5% chance to attempt placing vegetation
                        if (ThreadLocalRandom.current().nextDouble() < LAND_VEGETATION_CHANCE) {
                            BlockPos abovePos = targetPos.above();
                            // Only place if air above
                            if (level.getBlockState(abovePos).isAir()) {
                                // Get biome-specific vegetation
                                Block plantBlock = BiomePalette.getVegetationBlock(biome);
                                BlockState plantState = plantBlock.defaultBlockState();
                                // WFC check: ensure aesthetic variety (no identical neighbors)
                                if (shouldPlaceVegetation(level, abovePos, plantState)) {
                                    level.setBlock(abovePos, plantState, 2);
                                }
                            }
                        }
                    } else if (isUnderwater && (surfaceState.is(Blocks.SAND) || surfaceState.is(Blocks.GRAVEL) ||
                                                surfaceState.is(Blocks.MUD) || surfaceState.is(Blocks.CLAY))) {
                        // Underwater shoreline vegetation: kelp and seagrass
                        // Messy modes get 3x more vegetation (22.5% vs 7.5%)
                        double vegetationChance = mode.allowsVariation() ? 0.225 : 0.075;
                        if (ThreadLocalRandom.current().nextDouble() < vegetationChance) {
                            BlockPos abovePos = targetPos.above();
                            BlockState aboveState = level.getBlockState(abovePos);
                            // Only place if water above
                            if (aboveState.is(Blocks.WATER)) {
                                // 60% seagrass, 40% kelp
                                Block plantBlock = ThreadLocalRandom.current().nextDouble() < 0.60 ? Blocks.SEAGRASS : Blocks.KELP;
                                level.setBlock(abovePos, plantBlock.defaultBlockState(), 2);
                            }
                        }
                    }
                }
            }
        }

        // PASS 3: Clean up item drops in the area
        if (!level.isClientSide()) {
            int cleanupRadius = NaturalizationConfig.getRadius();
            BlockPos center = surfacePos;
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(cleanupRadius),
                entity -> true
            ).forEach(itemEntity -> itemEntity.discard());
        }

        return changed;
    }

    private BlockState getRandomVegetation() {
        // Weighted vegetation palette for natural aesthetics
        // Distribution: 40% short grass, 25% tall grass, 12.5% 2-tall plants, 7.5% fern, 5% common flowers, 2.5% rare flowers
        double roll = ThreadLocalRandom.current().nextDouble();

        // 40% short grass (most common)
        if (roll < 0.40) {
            return Blocks.GRASS.defaultBlockState();
        }
        // 25% tall grass
        else if (roll < 0.65) {
            return Blocks.TALL_GRASS.defaultBlockState();
        }
        // 12.5% 2-block tall plants (half of tall grass %)
        else if (roll < 0.775) {
            Block[] tallPlants = {Blocks.LARGE_FERN, Blocks.TALL_GRASS};
            return tallPlants[ThreadLocalRandom.current().nextInt(tallPlants.length)].defaultBlockState();
        }
        // 7.5% fern (halved from previous)
        else if (roll < 0.85) {
            return Blocks.FERN.defaultBlockState();
        }
        // 12.5% flowers total (down from 30%)
        // 8.3% common flowers
        else if (roll < 0.933) {
            Block[] commonFlowers = {Blocks.DANDELION, Blocks.POPPY};
            return commonFlowers[ThreadLocalRandom.current().nextInt(commonFlowers.length)].defaultBlockState();
        }
        // 4.2% rare flowers
        else {
            Block[] rareFlowers = {
                Blocks.BLUE_ORCHID,
                Blocks.ALLIUM,
                Blocks.AZURE_BLUET,
                Blocks.OXEYE_DAISY,
                Blocks.CORNFLOWER,
                Blocks.LILY_OF_THE_VALLEY
            };
            return rareFlowers[ThreadLocalRandom.current().nextInt(rareFlowers.length)].defaultBlockState();
        }
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
        return sameTypeCount < 2;
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

    private BlockPos findSurface(Level level, BlockPos start) {
        // Search upward first to handle being underground
        for (int y = 0; y < HEIGHT_ABOVE + 10; y++) {
            BlockPos checkPos = start.offset(0, y, 0);
            BlockState current = level.getBlockState(checkPos);

            // Found surface: block in safe list (replaceable terrain like dirt, grass, stone)
            // This skips trees, vegetation, and other non-terrain blocks
            if (NaturalizationConfig.getSafeBlocks().contains(current.getBlock())) {
                return checkPos;
            }
        }

        // If not found above, search downward
        for (int y = 0; y > -HEIGHT_BELOW - 10; y--) {
            BlockPos checkPos = start.offset(0, y, 0);
            BlockState current = level.getBlockState(checkPos);

            // Found surface: block in safe list (replaceable terrain)
            if (NaturalizationConfig.getSafeBlocks().contains(current.getBlock())) {
                return checkPos;
            }
        }

        // Return null if no surface found - caller will skip this column
        return null;
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
                    if (roll < 0.70) return Blocks.SAND.defaultBlockState();
                    else if (roll < 0.85) return Blocks.GRAVEL.defaultBlockState();
                    else if (roll < 0.95) return Blocks.MUD.defaultBlockState();
                    else return Blocks.COARSE_DIRT.defaultBlockState();
                } else {
                    return Blocks.SAND.defaultBlockState();
                }
            } else if (relativeY == -1) {
                // First subsurface - messy mode adds variation
                if (mode.allowsVariation()) {
                    double roll = ThreadLocalRandom.current().nextDouble();
                    // 60% gravel, 30% sand, 10% clay
                    if (roll < 0.60) return Blocks.GRAVEL.defaultBlockState();
                    else if (roll < 0.90) return Blocks.SAND.defaultBlockState();
                    else return Blocks.CLAY.defaultBlockState();
                } else {
                    return Blocks.GRAVEL.defaultBlockState();
                }
            } else if (relativeY >= -3) {
                // Mid subsurface - messy mode adds variation
                if (mode.allowsVariation()) {
                    double roll = ThreadLocalRandom.current().nextDouble();
                    // 50% sand, 30% gravel, 20% clay
                    if (roll < 0.50) return Blocks.SAND.defaultBlockState();
                    else if (roll < 0.80) return Blocks.GRAVEL.defaultBlockState();
                    else return Blocks.CLAY.defaultBlockState();
                } else {
                    return Blocks.SAND.defaultBlockState();
                }
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
                // SURFACE ONLY - use biome-specific palette
                Block surfaceBlock = BiomePalette.getSurfaceBlock(biome, mode, mode.allowsVariation());
                return surfaceBlock.defaultBlockState();
            } else if (relativeY >= -3) {
                // 1-3 blocks below surface - DIRT
                return Blocks.DIRT.defaultBlockState();
            } else {
                // DEEP subsurface (relativeY <= -4) - STONE only
                return Blocks.STONE.defaultBlockState();
            }
        }
    }

    private BlockState getSurfaceBlock(NaturalizationMode mode) {
        double roll = ThreadLocalRandom.current().nextDouble();

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
