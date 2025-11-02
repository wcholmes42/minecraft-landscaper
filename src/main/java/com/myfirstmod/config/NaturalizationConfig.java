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

    public static class ConfigData {
        public List<String> safe_blocks_to_replace;
        public String description = "List of blocks that the Naturalization Staff can safely replace. Use minecraft:block_name format.";

        public ConfigData() {}

        public ConfigData(List<String> blocks) {
            this.safe_blocks_to_replace = blocks;
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

            LOGGER.info("Loaded {} safe blocks from configuration", safeBlocks.size());

        } catch (IOException e) {
            LOGGER.error("Failed to load config, using defaults", e);
            safeBlocks = getDefaultBlocks();
        }
    }

    private static void createDefaultConfig(Path configPath) throws IOException {
        ConfigData defaultConfig = new ConfigData(getDefaultBlockList());
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
}
