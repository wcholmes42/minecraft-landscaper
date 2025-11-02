package com.myfirstmod.config;

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

    public ConfigScreen(Screen parent) {
        super(Component.literal("Naturalization Staff Configuration"));
        this.parent = parent;
        this.tempRadius = NaturalizationConfig.getRadius();
        this.tempConsumeResources = NaturalizationConfig.shouldConsumeResources();
        this.tempOverworldOnly = NaturalizationConfig.isOverworldOnly();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Radius slider
        this.addRenderableWidget(new AbstractSliderButton(centerX - 150, startY, 300, 20,
            Component.literal("Radius: " + tempRadius), (tempRadius - 1) / 9.0) {
            @Override
            protected void updateMessage() {
                tempRadius = (int) (this.value * 9) + 1;
                setMessage(Component.literal("Radius: " + tempRadius + " blocks"));
            }

            @Override
            protected void applyValue() {
                tempRadius = (int) (this.value * 9) + 1;
            }
        });

        // Consume resources toggle
        this.addRenderableWidget(CycleButton.onOffBuilder(tempConsumeResources)
            .create(centerX - 150, startY + 30, 300, 20,
                Component.literal("Consume Resources"),
                (button, value) -> tempConsumeResources = value));

        // Overworld only toggle
        this.addRenderableWidget(CycleButton.onOffBuilder(tempOverworldOnly)
            .create(centerX - 150, startY + 60, 300, 20,
                Component.literal("Overworld Only"),
                (button, value) -> tempOverworldOnly = value));

        // Save button
        this.addRenderableWidget(Button.builder(
            Component.literal("Save"),
            button -> {
                NaturalizationConfig.saveConfig(tempRadius, tempConsumeResources, tempOverworldOnly);
                this.minecraft.setScreen(parent);
            })
            .bounds(centerX - 100, startY + 100, 95, 20)
            .build());

        // Cancel button
        this.addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            button -> this.minecraft.setScreen(parent))
            .bounds(centerX + 5, startY + 100, 95, 20)
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
