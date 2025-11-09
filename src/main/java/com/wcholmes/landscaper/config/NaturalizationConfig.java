package com.wcholmes.landscaper.config;

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

    private static Set<Block> safeBlocks = null;
    private static int radius = 5;
    private static boolean consumeResources = false;
    private static boolean overworldOnly = true;
    private static boolean showHighlight = true;
    private static boolean messyEdge = false;
    private static boolean circleShape = true; // true = circle, false = square

    public static class ConfigData {
        public List<String> safe_blocks_to_replace;
        public int radius = 5;
        public boolean consume_resources = false;
        public boolean overworld_only = true;
        public boolean show_highlight = true;
        public boolean messy_edge = false;
        public boolean circle_shape = true;
        public String description = "Configuration for Naturalization Staff behavior";
        public String safe_blocks_description = "List of blocks that can be safely replaced. Use minecraft:block_name format.";
        public String radius_description = "Horizontal radius of effect (1-50 blocks)";
        public String consume_resources_description = "If true, requires dirt/stone/sand in inventory to naturalize terrain";
        public String overworld_only_description = "If true, staff only works in the Overworld dimension";
        public String show_highlight_description = "If true, shows visual outline of affected area when holding staff";
        public String messy_edge_description = "If true, randomly fades effect radius by 1-2 blocks for natural edges";
        public String circle_shape_description = "If true, uses circular area. If false, uses square area";

        public ConfigData() {}

        public ConfigData(List<String> blocks, int radius, boolean consumeResources, boolean overworldOnly, boolean showHighlight, boolean messyEdge, boolean circleShape) {
            this.safe_blocks_to_replace = blocks;
            this.radius = radius;
            this.consume_resources = consumeResources;
            this.overworld_only = overworldOnly;
            this.show_highlight = showHighlight;
            this.messy_edge = messyEdge;
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
            messyEdge = config.messy_edge;
            circleShape = config.circle_shape;

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
        ConfigData defaultConfig = new ConfigData(getDefaultBlockList(), 5, false, true, true, false, true);
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
        if (safeBlocks == null) {
            load();
        }
        return safeBlocks;
    }

    public static int getRadius() {
        if (safeBlocks == null) {
            load();
        }
        return radius;
    }

    public static boolean shouldConsumeResources() {
        if (safeBlocks == null) {
            load();
        }
        return consumeResources;
    }

    public static boolean isOverworldOnly() {
        if (safeBlocks == null) {
            load();
        }
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
            config.messy_edge = messyEdge;
            config.circle_shape = circleShape;

            // Write back to file
            String updatedJson = GSON.toJson(config);
            Files.writeString(configPath, updatedJson);

            LOGGER.info("Config saved: radius={}, consume_resources={}, overworld_only={}, show_highlight={}, messy_edge={}, circle_shape={}",
                radius, consumeResources, overworldOnly, showHighlight, messyEdge, circleShape);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    // Getters for new config values
    public static boolean showHighlight() {
        if (safeBlocks == null) {
            load();
        }
        return showHighlight;
    }

    public static boolean isMessyEdge() {
        if (safeBlocks == null) {
            load();
        }
        return messyEdge;
    }

    public static boolean isCircleShape() {
        if (safeBlocks == null) {
            load();
        }
        return circleShape;
    }

    // Setters for toggleable values (called by keybinds)
    // Synchronized to prevent race conditions when toggling from multiple threads
    public static synchronized void toggleHighlight() {
        showHighlight = !showHighlight;
        saveConfig(radius, consumeResources, overworldOnly);
    }

    public static synchronized void toggleMessyEdge() {
        messyEdge = !messyEdge;
        saveConfig(radius, consumeResources, overworldOnly);
    }

    public static synchronized void toggleShape() {
        circleShape = !circleShape;
        saveConfig(radius, consumeResources, overworldOnly);
    }
}
