package com.wcholmes.landscaper.client.config;

import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.network.ConfigSyncPacket;
import com.wcholmes.landscaper.common.network.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
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

    public ConfigScreen(Screen parent) {
        super(Component.literal("Naturalization Staff Configuration"));
        this.parent = parent;
        this.tempRadius = NaturalizationConfig.getRadius();
        this.tempConsumeResources = NaturalizationConfig.shouldConsumeResources();
        this.tempOverworldOnly = NaturalizationConfig.isOverworldOnly();
        this.tempVegetationDensity = NaturalizationConfig.getVegetationDensity();
        this.tempMessyEdgeExtension = NaturalizationConfig.getMessyEdgeExtension();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Radius slider
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY, 300, 20,
            Component.literal("Radius: " + tempRadius), (tempRadius - 1) / 49.0) {
            @Override
            protected void updateMessage() {
                tempRadius = (int) (this.value * 49) + 1;
                setMessage(Component.literal("Radius: " + tempRadius + " blocks"));
            }

            @Override
            protected void applyValue() {
                tempRadius = (int) (this.value * 49) + 1;
            }
        });

        // Vegetation density cycle button
        this.addRenderableWidget(CycleButton.<NaturalizationConfig.VegetationDensity>builder(density ->
                Component.literal(density.getDisplayName()))
            .withValues(NaturalizationConfig.VegetationDensity.values())
            .withInitialValue(tempVegetationDensity)
            .displayOnlyValue()
            .create(centerX - 150, startY + 30, 300, 20,
                Component.literal("Vegetation Density"),
                (button, value) -> tempVegetationDensity = value));

        // Messy edge extension slider
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY + 60, 300, 20,
            Component.literal("Messy Edge: " + tempMessyEdgeExtension + " blocks"), tempMessyEdgeExtension / 3.0) {
            @Override
            protected void updateMessage() {
                tempMessyEdgeExtension = (int)(this.value * 3);
                setMessage(Component.literal("Messy Edge: " + tempMessyEdgeExtension + " blocks"));
            }

            @Override
            protected void applyValue() {
                tempMessyEdgeExtension = (int)(this.value * 3);
            }
        });

        // Consume resources toggle
        this.addRenderableWidget(CycleButton.onOffBuilder(tempConsumeResources)
            .create(centerX - 150, startY + 90, 300, 20,
                Component.literal("Consume Resources"),
                (button, value) -> tempConsumeResources = value));

        // Overworld only toggle
        this.addRenderableWidget(CycleButton.onOffBuilder(tempOverworldOnly)
            .create(centerX - 150, startY + 120, 300, 20,
                Component.literal("Overworld Only"),
                (button, value) -> tempOverworldOnly = value));

        // Save button
        this.addRenderableWidget(Button.builder(
            Component.literal("Save"),
            button -> {
                // Update local config
                NaturalizationConfig.setVegetationDensity(tempVegetationDensity);
                NaturalizationConfig.setMessyEdgeExtension(tempMessyEdgeExtension);
                NaturalizationConfig.saveConfig(tempRadius, tempConsumeResources, tempOverworldOnly);

                // Send to server if in multiplayer
                if (this.minecraft.player != null && this.minecraft.getCurrentServer() != null) {
                    ModPackets.CHANNEL.sendToServer(new ConfigSyncPacket(
                        tempRadius,
                        tempConsumeResources,
                        tempOverworldOnly,
                        tempVegetationDensity,
                        tempMessyEdgeExtension
                    ));
                }

                this.minecraft.setScreen(parent);
            })
            .bounds(centerX - 100, startY + 160, 95, 20)
            .build());

        // Cancel button
        this.addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            button -> this.minecraft.setScreen(parent))
            .bounds(centerX + 5, startY + 160, 95, 20)
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
