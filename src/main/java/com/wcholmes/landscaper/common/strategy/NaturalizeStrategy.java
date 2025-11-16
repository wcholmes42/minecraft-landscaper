package com.wcholmes.landscaper.common.strategy;

import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.item.BiomePalette;
import com.wcholmes.landscaper.common.item.NaturalizationMode;
import com.wcholmes.landscaper.common.util.TerrainUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Naturalize/Erosion strategy - creates organic terrain variations using multiple noise layers.
 * Combines Perlin noise (smooth curves), roughness (weathering), and height-based variation
 * to create natural-looking depressions and mounds like native Minecraft terrain generation.
 */
public class NaturalizeStrategy extends BaseTerrainStrategy {

    @Override
    public int modify(Level level, BlockPos center, Player player, NaturalizationMode mode,
                      Map<Item, Integer> resourcesNeeded,
                      com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {

        int blocksChanged = 0;
        int landColumns = 0;
        int underwaterColumns = 0;

        // Use player-specific settings or fall back to global config
        int radius = playerSettings != null ? playerSettings.radius : NaturalizationConfig.getRadius();
        int effectiveRadius = radius - 1;

        BlockPos playerPos = player != null ? player.blockPosition() : null;
        boolean isCircle = NaturalizationConfig.isCircleShape();
        int messyEdgeExtension = playerSettings != null ? playerSettings.messyEdgeExtension : NaturalizationConfig.getMessyEdgeExtension();

        // Get naturalize settings
        int erosionStrength = playerSettings != null ? playerSettings.erosionStrength : NaturalizationConfig.getErosionStrength();
        double roughnessAmount = playerSettings != null ? playerSettings.roughnessAmount : NaturalizationConfig.getRoughnessAmount();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting NATURALIZE at {} with radius={}, erosion={}, roughness={}",
                center, radius, erosionStrength, roughnessAmount);
        }

        // Sample surrounding terrain to build natural palette
        int sampleRadius = Math.min(radius + 3, 8); // Sample close to operation area for better matching
        TerrainPalette sampledPalette = sampleSurroundingTerrain(level, center, sampleRadius);
        LOGGER.info("Sampled terrain palette: {} - Valid: {}", sampledPalette.getStats(), sampledPalette.hasValidSamples());
        if (sampledPalette.hasValidSamples()) {
            LOGGER.info("Using sampled palette: {}", sampledPalette.getDetailedBreakdown());
        } else {
            LOGGER.warn("Insufficient samples, falling back to biome palettes");
        }

        // Generate world seed for consistent noise
        // Use a deterministic seed based on center position if not ServerLevel
        long worldSeed = 0;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            worldSeed = serverLevel.getSeed();
        } else {
            // Client-side or non-server level - use position-based seed
            worldSeed = ((long) center.getX() * 31L) + ((long) center.getZ() * 37L);
        }

        // Expand search range if messy edge is enabled
        int searchRadius = messyEdgeExtension > 0 ? effectiveRadius + messyEdgeExtension : effectiveRadius;

        int columnsProcessed = 0;

        // Iterate through the area
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                boolean withinRadius;

                if (messyEdgeExtension > 0) {
                    withinRadius = TerrainUtils.shouldApplyMessyEdge(x, z, radius, isCircle, center, messyEdgeExtension);
                } else {
                    withinRadius = isCircle ? (x * x + z * z <= effectiveRadius * effectiveRadius) : true;
                }

                if (withinRadius) {
                    columnsProcessed++;
                    BlockPos columnPos = center.offset(x, 0, z);

                    // Calculate height variation for this position
                    int heightOffset = calculateHeightOffset(columnPos, worldSeed, erosionStrength, roughnessAmount);

                    // Check if this column is underwater
                    BlockPos surfacePos = TerrainUtils.findSurface(level, columnPos);
                    if (surfacePos != null) {
                        boolean isColumnUnderwater = isUnderwater(level, surfacePos);
                        if (isColumnUnderwater) {
                            underwaterColumns++;
                        } else {
                            landColumns++;
                        }
                    }

                    // Naturalize this column with height offset
                    int result = naturalizeColumn(level, columnPos, heightOffset, mode, resourcesNeeded,
                                                 playerPos, playerSettings, center, sampledPalette);
                    blocksChanged += result;
                }
            }
        }

        // Clean up item drops
        cleanupItemDrops(level, center, radius);

        // Smart logging
        String typeMessage;
        if (underwaterColumns > 0 && landColumns > 0) {
            typeMessage = String.format("Hybrid NATURALIZE (land: %d columns, water: %d columns)", landColumns, underwaterColumns);
        } else if (underwaterColumns > 0) {
            typeMessage = "Underwater NATURALIZE";
        } else {
            typeMessage = "Land NATURALIZE";
        }

        LOGGER.info("{} complete! Changed {} blocks | {} columns | searchRadius={}",
            typeMessage, blocksChanged, columnsProcessed, searchRadius);

        if (player != null) {
            player.displayClientMessage(
                Component.literal(typeMessage + ": " + blocksChanged + " blocks | " + columnsProcessed + " columns | R:" + searchRadius),
                true
            );
        }

        return blocksChanged;
    }

    @Override
    public void calculateResourcesNeeded(Level level, BlockPos center, NaturalizationMode mode,
                                          Map<Item, Integer> resourcesNeeded,
                                          com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings) {
        int radius = playerSettings != null ? playerSettings.radius : NaturalizationConfig.getRadius();
        int effectiveRadius = radius - 1;

        // This is an approximation - actual resources depend on height variations
        // For now, estimate average case
        for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
            for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                BlockPos columnPos = center.offset(dx, 0, dz);
                BlockPos surfacePos = TerrainUtils.findSurface(level, columnPos);

                if (surfacePos == null) continue;

                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(surfacePos);
                boolean isUnderwater = isUnderwater(level, surfacePos);

                // Estimate resources for HEIGHT_BELOW blocks (conservative estimate)
                for (int i = 0; i >= -HEIGHT_BELOW; i--) {
                    BlockState newState = determineNaturalBlock(i, isUnderwater, mode, biome); // Resource estimation uses fallback
                    Item resourceItem = getResourceItemForBlock(newState);
                    if (resourceItem != null) {
                        resourcesNeeded.merge(resourceItem, 1, Integer::sum);
                    }
                }
            }
        }
    }

    /**
     * Calculate height offset using multi-layered noise for natural variation.
     */
    private int calculateHeightOffset(BlockPos pos, long seed, int erosionStrength, double roughnessAmount) {
        double x = pos.getX();
        double z = pos.getZ();

        // Layer 1: Large-scale Perlin noise for smooth organic curves (rolling hills)
        // Scale: larger = smoother, smaller = more variation
        double largeScale = 16.0; // One hill/valley per ~16 blocks
        double noise1 = improvedPerlinNoise(x / largeScale, z / largeScale, seed);

        // Layer 2: Medium-scale noise for secondary variation
        double mediumScale = 8.0;
        double noise2 = improvedPerlinNoise(x / mediumScale, z / mediumScale, seed + 1000);

        // Layer 3: Small-scale noise for roughness/weathering
        double smallScale = 3.0;
        double noise3 = improvedPerlinNoise(x / smallScale, z / smallScale, seed + 2000);

        // Combine layers with different weights
        // noise1 (large) = primary shape (60%)
        // noise2 (medium) = secondary variation (25%)
        // noise3 (small) = roughness detail (15%)
        double combinedNoise = (noise1 * 0.60) + (noise2 * 0.25) + (noise3 * 0.15 * roughnessAmount);

        // Map noise (-1 to 1) to height offset (-erosionStrength to +erosionStrength)
        int heightOffset = (int) Math.round(combinedNoise * erosionStrength);

        // Clamp to prevent extreme variations
        return Mth.clamp(heightOffset, -erosionStrength, erosionStrength);
    }

    /**
     * Improved Perlin-style noise function.
     * Returns value between -1.0 and 1.0.
     */
    private double improvedPerlinNoise(double x, double z, long seed) {
        // Integer coordinates
        int xi = Mth.floor(x);
        int zi = Mth.floor(z);

        // Fractional coordinates
        double xf = x - xi;
        double zf = z - zi;

        // Smooth curves (fade function)
        double u = fade(xf);
        double v = fade(zf);

        // Hash coordinates with seed
        int aa = hash(xi, zi, seed);
        int ab = hash(xi, zi + 1, seed);
        int ba = hash(xi + 1, zi, seed);
        int bb = hash(xi + 1, zi + 1, seed);

        // Interpolate between grid points
        double x1 = lerp(u, grad(aa, xf, zf), grad(ba, xf - 1, zf));
        double x2 = lerp(u, grad(ab, xf, zf - 1), grad(bb, xf - 1, zf - 1));

        return lerp(v, x1, x2);
    }

    /**
     * Fade function for smooth interpolation (6t^5 - 15t^4 + 10t^3).
     */
    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /**
     * Linear interpolation.
     */
    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /**
     * Hash function for pseudo-random gradients.
     */
    private int hash(int x, int z, long seed) {
        // Mix coordinates with seed
        long h = seed;
        h = h * 31 + x;
        h = h * 31 + z;
        h = h ^ (h >>> 32);
        h = h * 0x27d4eb2d; // Magic constant for good distribution
        return (int) (h & 0x7FFFFFFF);
    }

    /**
     * Gradient function for Perlin noise.
     */
    private double grad(int hash, double x, double z) {
        // Use hash to select gradient direction
        int h = hash & 7; // 8 gradients
        double u = h < 4 ? x : z;
        double v = h < 4 ? z : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /**
     * Naturalize a single column with height offset.
     */
    private int naturalizeColumn(Level level, BlockPos pos, int heightOffset, NaturalizationMode mode,
                                  Map<Item, Integer> resourcesNeeded, BlockPos playerPos,
                                  com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings,
                                  BlockPos center, TerrainPalette sampledPalette) {
        int changed = 0;

        // Find the surface level
        BlockPos surfacePos = TerrainUtils.findSurface(level, pos);
        if (surfacePos == null) {
            return 0;
        }

        // Safety check: Don't modify columns directly under the player
        if (playerPos != null && pos.getX() == playerPos.getX() && pos.getZ() == playerPos.getZ()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Skipping column at {} - player is standing here", pos);
            }
            return 0;
        }

        // Apply height offset to surface
        BlockPos adjustedSurface = surfacePos.offset(0, heightOffset, 0);

        // Detect biome and underwater status
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(adjustedSurface);
        boolean isUnderwater = isUnderwater(level, surfacePos); // Check original surface

        // Clear vegetation above original surface
        for (int i = HEIGHT_ABOVE; i > 0; i--) {
            BlockPos targetPos = surfacePos.offset(0, i, 0);
            BlockState currentState = level.getBlockState(targetPos);

            if (currentState.isAir() || currentState.liquid()) continue;

            boolean isVegetation = currentState.canBeReplaced() ||
                currentState.getBlock() instanceof net.minecraft.world.level.block.BushBlock ||
                currentState.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock ||
                currentState.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock ||
                currentState.is(Blocks.KELP) || currentState.is(Blocks.KELP_PLANT) ||
                currentState.is(Blocks.SEAGRASS) || currentState.is(Blocks.TALL_SEAGRASS);

            if (isVegetation) {
                level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);
            }
        }

        // Clear old terrain from original surface down
        for (int i = 0; i >= -HEIGHT_BELOW; i--) {
            BlockPos targetPos = surfacePos.offset(0, i, 0);
            BlockState currentState = level.getBlockState(targetPos);

            if (currentState.isAir() || currentState.liquid()) continue;

            if (NaturalizationConfig.getSafeBlocks().contains(currentState.getBlock())) {
                level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), BLOCK_UPDATE_FLAG);
            }
        }

        // Place new terrain at adjusted height
        for (int i = 0; i >= -HEIGHT_BELOW; i--) {
            BlockPos targetPos = adjustedSurface.offset(0, i, 0);

            BlockState newState = determineNaturalBlock(i, isUnderwater, mode, biome, sampledPalette);

            level.setBlock(targetPos, newState, BLOCK_UPDATE_FLAG);
            changed++;

            // Track resources
            boolean consumeResources = playerSettings != null ? playerSettings.consumeResources : NaturalizationConfig.shouldConsumeResources();
            if (consumeResources) {
                Item resourceItem = getResourceItemForBlock(newState);
                if (resourceItem != null) {
                    resourcesNeeded.merge(resourceItem, 1, Integer::sum);
                }
            }

            // Add vegetation at new surface
            if (i == 0 && mode.shouldAddPlants()) {
                BlockState surfaceState = level.getBlockState(targetPos);

                if (!isUnderwater && (surfaceState.is(Blocks.GRASS_BLOCK) || surfaceState.is(Blocks.SAND) ||
                    surfaceState.is(Blocks.PODZOL) || surfaceState.is(Blocks.MYCELIUM) || surfaceState.is(Blocks.MUD))) {
                    NaturalizationConfig.VegetationDensity density = playerSettings != null ?
                        playerSettings.vegetationDensity : NaturalizationConfig.getVegetationDensity();
                    if (ThreadLocalRandom.current().nextDouble() < density.getChance()) {
                        BlockPos abovePos = targetPos.above();
                        if (level.getBlockState(abovePos).isAir()) {
                            Block plantBlock = BiomePalette.getVegetationBlock(biome);
                            BlockState plantState = plantBlock.defaultBlockState();
                            if (shouldPlaceVegetation(level, abovePos, plantState)) {
                                level.setBlock(abovePos, plantState, BLOCK_UPDATE_FLAG);
                            }
                        }
                    }
                } else if (isUnderwater && (surfaceState.is(Blocks.SAND) || surfaceState.is(Blocks.GRAVEL) ||
                    surfaceState.is(Blocks.MUD) || surfaceState.is(Blocks.CLAY))) {
                    NaturalizationConfig.VegetationDensity density = playerSettings != null ?
                        playerSettings.vegetationDensity : NaturalizationConfig.getVegetationDensity();
                    double baseChance = density.getChance();
                    double vegetationChance = mode.allowsVariation() ?
                        baseChance * MESSY_UNDERWATER_VEGETATION_MULTIPLIER : baseChance;
                    if (ThreadLocalRandom.current().nextDouble() < vegetationChance) {
                        BlockPos abovePos = targetPos.above();
                        BlockState aboveState = level.getBlockState(abovePos);
                        if (aboveState.is(Blocks.WATER)) {
                            Block plantBlock = ThreadLocalRandom.current().nextDouble() < UNDERWATER_SEAGRASS_RATIO ?
                                Blocks.SEAGRASS : Blocks.KELP;
                            level.setBlock(abovePos, plantBlock.defaultBlockState(), BLOCK_UPDATE_FLAG);
                        }
                    }
                }
            }
        }

        return changed;
    }
}
