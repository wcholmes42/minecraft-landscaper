package com.myfirstmod.config;

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

    public static class ConfigData {
        public List<String> safe_blocks_to_replace;
        public int radius = 5;
        public boolean consume_resources = false;
        public boolean overworld_only = true;
        public String description = "Configuration for Naturalization Staff behavior";
        public String safe_blocks_description = "List of blocks that can be safely replaced. Use minecraft:block_name format.";
        public String radius_description = "Horizontal radius of effect (1-10 blocks)";
        public String consume_resources_description = "If true, requires dirt/stone/sand in inventory to naturalize terrain";
        public String overworld_only_description = "If true, staff only works in the Overworld dimension";

        public ConfigData() {}

        public ConfigData(List<String> blocks, int radius, boolean consumeResources, boolean overworldOnly) {
            this.safe_blocks_to_replace = blocks;
            this.radius = radius;
            this.consume_resources = consumeResources;
            this.overworld_only = overworldOnly;
        }
    }

    private static List<String> getDefaultBlockList() {
        return List.of(
            // Stone variants - common terrain
            "minecraft:stone",
            "minecraft:cobblestone",
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
            radius = Math.max(1, Math.min(10, config.radius)); // Clamp to 1-10
            consumeResources = config.consume_resources;
            overworldOnly = config.overworld_only;

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
        ConfigData defaultConfig = new ConfigData(getDefaultBlockList(), 5, false, true);
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

    public static void saveConfig(int newRadius, boolean newConsumeResources, boolean newOverworldOnly) {
        radius = Math.max(1, Math.min(10, newRadius));
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

            // Write back to file
            String updatedJson = GSON.toJson(config);
            Files.writeString(configPath, updatedJson);

            LOGGER.info("Config saved: radius={}, consume_resources={}, overworld_only={}",
                radius, consumeResources, overworldOnly);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}
