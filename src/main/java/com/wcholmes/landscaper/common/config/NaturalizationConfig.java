package com.wcholmes.landscaper.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NaturalizationConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "landscaper-naturalization.json";

    public enum VegetationDensity {
        NONE(0.0, "None"),
        LOW(0.025, "Low"),         // 2.5%
        MEDIUM(0.075, "Medium"),    // 7.5% (current default)
        HIGH(0.15, "High"),         // 15%
        VERY_HIGH(0.30, "Very High"); // 30%

        private final double chance;
        private final String displayName;

        VegetationDensity(double chance, String displayName) {
            this.chance = chance;
            this.displayName = displayName;
        }

        public double getChance() {
            return chance;
        }

        public String getDisplayName() {
            return displayName;
        }

        public VegetationDensity next() {
            VegetationDensity[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public VegetationDensity previous() {
            VegetationDensity[] values = values();
            return values[(ordinal() - 1 + values.length) % values.length];
        }
    }

    private static volatile Set<Block> safeBlocks = null;
    private static volatile int radius = 5;
    private static volatile boolean consumeResources = false;
    private static volatile boolean overworldOnly = true;
    private static volatile boolean showHighlight = true;
    private static volatile int messyEdgeExtension = 2; // 0-3 blocks extension
    private static volatile boolean circleShape = true; // true = circle, false = square
    private static volatile VegetationDensity vegetationDensity = VegetationDensity.MEDIUM;

    public static class ConfigData {
        public List<String> safe_blocks_to_replace;
        public int radius = 5;
        public boolean consume_resources = false;
        public boolean overworld_only = true;
        public boolean show_highlight = true;
        public int messy_edge_extension = 2;
        public boolean circle_shape = true;
        public String vegetation_density = "MEDIUM";
        public String description = "Configuration for Naturalization Staff behavior";
        public String safe_blocks_description = "List of blocks that can be safely replaced. Use minecraft:block_name format.";
        public String radius_description = "Horizontal radius of effect (1-50 blocks)";
        public String consume_resources_description = "If true, requires dirt/stone/sand in inventory to naturalize terrain";
        public String overworld_only_description = "If true, staff only works in the Overworld dimension";
        public String show_highlight_description = "If true, shows visual outline of affected area when holding staff";
        public String messy_edge_extension_description = "Blocks beyond radius for messy edge (0-3, 0=disabled)";
        public String circle_shape_description = "If true, uses circular area. If false, uses square area";
        public String vegetation_density_description = "Vegetation density: NONE, LOW (2.5%), MEDIUM (7.5%), HIGH (15%), VERY_HIGH (30%)";

        public ConfigData() {}

        public ConfigData(List<String> blocks, int radius, boolean consumeResources, boolean overworldOnly, boolean showHighlight, int messyEdgeExtension, boolean circleShape) {
            this.safe_blocks_to_replace = blocks;
            this.radius = radius;
            this.consume_resources = consumeResources;
            this.overworld_only = overworldOnly;
            this.show_highlight = showHighlight;
            this.messy_edge_extension = messyEdgeExtension;
            this.circle_shape = circleShape;
        }
    }

    private static List<String> getDefaultBlockList() {
        return List.of(
            // Stone variants - common terrain
            "minecraft:stone",
            "minecraft:cobblestone",
            "minecraft:mossy_cobblestone",
            "minecraft:andesite",
            "minecraft:diorite",
            "minecraft:granite",
            "minecraft:deepslate",
            "minecraft:cobbled_deepslate",
            "minecraft:tuff",
            "minecraft:calcite",

            // Dirt variants
            "minecraft:dirt",
            "minecraft:coarse_dirt",
            "minecraft:rooted_dirt",
            "minecraft:podzol",
            "minecraft:mycelium",
            "minecraft:grass_block",
            "minecraft:dirt_path",
            "minecraft:farmland",
            "minecraft:mud",
            "minecraft:muddy_mangrove_roots",

            // Sand and gravel
            "minecraft:sand",
            "minecraft:red_sand",
            "minecraft:gravel",
            "minecraft:sandstone",
            "minecraft:red_sandstone",
            "minecraft:clay",

            // Nether terrain
            "minecraft:netherrack",
            "minecraft:blackstone",
            "minecraft:basalt",
            "minecraft:smooth_basalt",

            // End terrain
            "minecraft:end_stone"
        );
    }

    public static void load() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);

        try {
            if (!Files.exists(configPath)) {
                // Create default config
                LOGGER.info("Config file not found, creating default configuration at: {}", configPath);
                createDefaultConfig(configPath);
            }

            // Load config
            String json = Files.readString(configPath);
            ConfigData config = GSON.fromJson(json, ConfigData.class);

            // Load settings
            radius = Math.max(1, Math.min(50, config.radius)); // Clamp to 1-50
            consumeResources = config.consume_resources;
            overworldOnly = config.overworld_only;
            showHighlight = config.show_highlight;
            messyEdgeExtension = Math.max(0, Math.min(3, config.messy_edge_extension)); // Clamp to 0-3
            circleShape = config.circle_shape;

            // Load vegetation density with fallback to MEDIUM if invalid
            try {
                vegetationDensity = VegetationDensity.valueOf(config.vegetation_density);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid vegetation density '{}', using MEDIUM", config.vegetation_density);
                vegetationDensity = VegetationDensity.MEDIUM;
            }

            // Convert string IDs to blocks
            safeBlocks = new HashSet<>();
            for (String blockId : config.safe_blocks_to_replace) {
                ResourceLocation resourceLocation = new ResourceLocation(blockId);
                Block block = BuiltInRegistries.BLOCK.get(resourceLocation);

                if (block != Blocks.AIR) {
                    safeBlocks.add(block);
                } else {
                    LOGGER.warn("Unknown block in config: {}", blockId);
                }
            }

            LOGGER.info("Loaded config: {} safe blocks, radius={}, consume_resources={}, overworld_only={}",
                safeBlocks.size(), radius, consumeResources, overworldOnly);

        } catch (IOException e) {
            LOGGER.error("Failed to load config, using defaults", e);
            safeBlocks = getDefaultBlocks();
            radius = 5;
            consumeResources = false;
            overworldOnly = true;
        }
    }

    private static void createDefaultConfig(Path configPath) throws IOException {
        ConfigData defaultConfig = new ConfigData(getDefaultBlockList(), 5, false, true, true, 2, true);
        String json = GSON.toJson(defaultConfig);
        Files.writeString(configPath, json);
        LOGGER.info("Created default configuration file");
    }

    private static Set<Block> getDefaultBlocks() {
        return getDefaultBlockList().stream()
            .map(ResourceLocation::new)
            .map(BuiltInRegistries.BLOCK::get)
            .filter(block -> block != Blocks.AIR)
            .collect(Collectors.toSet());
    }

    public static Set<Block> getSafeBlocks() {
        return safeBlocks;
    }

    public static int getRadius() {
        return radius;
    }

    public static boolean shouldConsumeResources() {
        return consumeResources;
    }

    public static boolean isOverworldOnly() {
        return overworldOnly;
    }

    // Synchronized to prevent concurrent modification and file I/O conflicts
    public static synchronized void saveConfig(int newRadius, boolean newConsumeResources, boolean newOverworldOnly) {
        radius = Math.max(1, Math.min(50, newRadius));
        consumeResources = newConsumeResources;
        overworldOnly = newOverworldOnly;

        Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);

        try {
            // Read current config to preserve safe blocks list
            String json = Files.readString(configPath);
            ConfigData config = GSON.fromJson(json, ConfigData.class);

            // Update only the changed values
            config.radius = radius;
            config.consume_resources = consumeResources;
            config.overworld_only = overworldOnly;
            config.show_highlight = showHighlight;
            config.messy_edge_extension = messyEdgeExtension;
            config.circle_shape = circleShape;
            config.vegetation_density = vegetationDensity.name();

            // Write back to file
            String updatedJson = GSON.toJson(config);
            Files.writeString(configPath, updatedJson);

            LOGGER.info("Config saved: radius={}, consume_resources={}, overworld_only={}, show_highlight={}, messy_edge_extension={}, circle_shape={}, vegetation_density={}",
                radius, consumeResources, overworldOnly, showHighlight, messyEdgeExtension, circleShape, vegetationDensity);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    // Getters for new config values
    public static boolean showHighlight() {
        return showHighlight;
    }

    public static boolean isMessyEdge() {
        return messyEdgeExtension > 0;
    }

    public static int getMessyEdgeExtension() {
        return messyEdgeExtension;
    }

    public static boolean isCircleShape() {
        return circleShape;
    }

    public static VegetationDensity getVegetationDensity() {
        return vegetationDensity;
    }

    public static synchronized void setVegetationDensity(VegetationDensity newDensity) {
        vegetationDensity = newDensity;
    }

    public static synchronized void setMessyEdgeExtension(int extension) {
        messyEdgeExtension = Math.max(0, Math.min(3, extension));
    }

    // Convenience methods for toggling/cycling config values
    public static synchronized void cycleVegetationDensity() {
        vegetationDensity = vegetationDensity.next();
        saveConfig(radius, consumeResources, overworldOnly);
    }

    public static synchronized void toggleHighlight() {
        showHighlight = !showHighlight;
        saveConfig(radius, consumeResources, overworldOnly);
    }

    public static synchronized void toggleMessyEdge() {
        // Cycle: 0 -> 1 -> 2 -> 3 -> 0
        messyEdgeExtension = (messyEdgeExtension + 1) % 4;
        saveConfig(radius, consumeResources, overworldOnly);
    }

    public static synchronized void toggleShape() {
        circleShape = !circleShape;
        saveConfig(radius, consumeResources, overworldOnly);
    }
}
