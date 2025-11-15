package com.wcholmes.landscaper.client.config;

import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.network.ConfigSyncPacket;
import com.wcholmes.landscaper.common.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private int tempRadius;
    private boolean tempConsumeResources;
    private boolean tempOverworldOnly;
    private NaturalizationConfig.VegetationDensity tempVegetationDensity;
    private int tempMessyEdgeExtension;

    // New advanced settings
    private int tempMaxFlattenHeight;
    private int tempErosionStrength;
    private double tempRoughnessAmount;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Naturalization Staff Configuration"));
        this.parent = parent;
        this.tempRadius = NaturalizationConfig.getRadius();
        this.tempConsumeResources = NaturalizationConfig.shouldConsumeResources();
        this.tempOverworldOnly = NaturalizationConfig.isOverworldOnly();
        this.tempVegetationDensity = NaturalizationConfig.getVegetationDensity();
        this.tempMessyEdgeExtension = NaturalizationConfig.getMessyEdgeExtension();
        this.tempMaxFlattenHeight = NaturalizationConfig.getMaxFlattenHeight();
        this.tempErosionStrength = NaturalizationConfig.getErosionStrength();
        this.tempRoughnessAmount = NaturalizationConfig.getRoughnessAmount();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Radius slider (snaps to integers 1-50)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY, 300, 20,
            Component.literal("Radius: " + tempRadius), (tempRadius - 1) / 49.0) {
            @Override
            protected void updateMessage() {
                tempRadius = (int) Math.round(this.value * 49) + 1;
                setMessage(Component.literal("Radius: " + tempRadius + " blocks"));
            }

            @Override
            protected void applyValue() {
                tempRadius = (int) Math.round(this.value * 49) + 1;
                // Snap slider to discrete value
                this.value = (tempRadius - 1) / 49.0;
            }
        });

        // Vegetation density slider (snaps to 5 levels: 0-4)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY + 30, 300, 20,
            Component.literal("Vegetation: " + tempVegetationDensity.getDisplayName()),
            tempVegetationDensity.ordinal() / 4.0) {
            @Override
            protected void updateMessage() {
                int level = (int) Math.round(this.value * 4);
                tempVegetationDensity = NaturalizationConfig.VegetationDensity.values()[level];
                setMessage(Component.literal("Vegetation: " + tempVegetationDensity.getDisplayName()));
            }

            @Override
            protected void applyValue() {
                int level = (int) Math.round(this.value * 4);
                tempVegetationDensity = NaturalizationConfig.VegetationDensity.values()[level];
                // Snap slider to discrete value
                this.value = level / 4.0;
            }
        });

        // Messy edge extension slider (snaps to 0-3)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY + 60, 300, 20,
            Component.literal("Messy Edge: " + tempMessyEdgeExtension + " blocks"), tempMessyEdgeExtension / 3.0) {
            @Override
            protected void updateMessage() {
                tempMessyEdgeExtension = (int) Math.round(this.value * 3);
                setMessage(Component.literal("Messy Edge: " + tempMessyEdgeExtension + " blocks"));
            }

            @Override
            protected void applyValue() {
                tempMessyEdgeExtension = (int) Math.round(this.value * 3);
                // Snap slider to discrete value
                this.value = tempMessyEdgeExtension / 3.0;
            }
        });

        // Consume resources slider (snaps to on/off)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY + 90, 300, 20,
            Component.literal("Consume Resources: " + (tempConsumeResources ? "ON" : "OFF")),
            tempConsumeResources ? 1.0 : 0.0) {
            @Override
            protected void updateMessage() {
                tempConsumeResources = this.value > 0.5;
                setMessage(Component.literal("Consume Resources: " + (tempConsumeResources ? "ON" : "OFF")));
            }

            @Override
            protected void applyValue() {
                tempConsumeResources = this.value > 0.5;
                // Snap slider to discrete value
                this.value = tempConsumeResources ? 1.0 : 0.0;
            }
        });

        // Overworld only slider (snaps to on/off)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY + 120, 300, 20,
            Component.literal("Overworld Only: " + (tempOverworldOnly ? "ON" : "OFF")),
            tempOverworldOnly ? 1.0 : 0.0) {
            @Override
            protected void updateMessage() {
                tempOverworldOnly = this.value > 0.5;
                setMessage(Component.literal("Overworld Only: " + (tempOverworldOnly ? "ON" : "OFF")));
            }

            @Override
            protected void applyValue() {
                tempOverworldOnly = this.value > 0.5;
                // Snap slider to discrete value
                this.value = tempOverworldOnly ? 1.0 : 0.0;
            }
        });

        // Max Flatten Height slider (1-320 blocks, for FLATTEN mode)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY + 150, 300, 20,
            Component.literal("Max Flatten Height: " + tempMaxFlattenHeight),
            (tempMaxFlattenHeight - 1) / 319.0) {
            @Override
            protected void updateMessage() {
                tempMaxFlattenHeight = (int) Math.round(this.value * 319) + 1;
                setMessage(Component.literal("Max Flatten Height: " + tempMaxFlattenHeight + " blocks"));
            }

            @Override
            protected void applyValue() {
                tempMaxFlattenHeight = (int) Math.round(this.value * 319) + 1;
                this.value = (tempMaxFlattenHeight - 1) / 319.0;
            }
        });

        // Erosion Strength slider (1-10 blocks, for NATURALIZE mode)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY + 180, 300, 20,
            Component.literal("Erosion Strength: " + tempErosionStrength),
            (tempErosionStrength - 1) / 9.0) {
            @Override
            protected void updateMessage() {
                tempErosionStrength = (int) Math.round(this.value * 9) + 1;
                setMessage(Component.literal("Erosion Strength: " + tempErosionStrength + " blocks"));
            }

            @Override
            protected void applyValue() {
                tempErosionStrength = (int) Math.round(this.value * 9) + 1;
                this.value = (tempErosionStrength - 1) / 9.0;
            }
        });

        // Roughness Amount slider (0.0-5.0, for NATURALIZE mode)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY + 210, 300, 20,
            Component.literal("Roughness: " + String.format("%.1f", tempRoughnessAmount)),
            tempRoughnessAmount / 5.0) {
            @Override
            protected void updateMessage() {
                tempRoughnessAmount = Math.round(this.value * 50) / 10.0; // Round to nearest 0.1
                setMessage(Component.literal("Roughness: " + String.format("%.1f", tempRoughnessAmount)));
            }

            @Override
            protected void applyValue() {
                tempRoughnessAmount = Math.round(this.value * 50) / 10.0;
                this.value = tempRoughnessAmount / 5.0;
            }
        });

        // Save button
        this.addRenderableWidget(Button.builder(
            Component.literal("Save"),
            button -> {
                // Update local config
                NaturalizationConfig.setVegetationDensity(tempVegetationDensity);
                NaturalizationConfig.setMessyEdgeExtension(tempMessyEdgeExtension);
                NaturalizationConfig.setMaxFlattenHeight(tempMaxFlattenHeight);
                NaturalizationConfig.setErosionStrength(tempErosionStrength);
                NaturalizationConfig.setRoughnessAmount(tempRoughnessAmount);
                NaturalizationConfig.saveConfig(tempRadius, tempConsumeResources, tempOverworldOnly);

                // Send to server if in multiplayer
                if (this.minecraft.player != null && this.minecraft.getCurrentServer() != null) {
                    ModPackets.CHANNEL.sendToServer(new ConfigSyncPacket(
                        tempRadius,
                        tempConsumeResources,
                        tempOverworldOnly,
                        tempVegetationDensity,
                        tempMessyEdgeExtension,
                        tempMaxFlattenHeight,
                        tempErosionStrength,
                        tempRoughnessAmount
                    ));
                }

                this.minecraft.setScreen(parent);
            })
            .bounds(centerX - 100, startY + 250, 95, 20)
            .build());

        // Cancel button
        this.addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            button -> this.minecraft.setScreen(parent))
            .bounds(centerX + 5, startY + 250, 95, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        // Draw title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
