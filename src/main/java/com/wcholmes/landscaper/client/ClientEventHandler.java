package com.wcholmes.landscaper.client;

import com.wcholmes.landscaper.Landscaper;
import com.wcholmes.landscaper.config.ConfigScreen;
import com.wcholmes.landscaper.config.NaturalizationConfig;
import com.wcholmes.landscaper.item.NaturalizationMode;
import com.wcholmes.landscaper.item.NaturalizationStaff;
import com.wcholmes.landscaper.network.CycleModePacket;
import com.wcholmes.landscaper.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Landscaper.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {

    // Minecraft color codes for chat messages
    private static final String COLOR_LABEL = "§6";  // Gold
    private static final String COLOR_VALUE = "§e";  // Yellow

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;

            // Handle mode cycling
            if (player != null && KeyBindings.cycleMode.consumeClick()) {
                ItemStack heldItem = player.getMainHandItem();

                if (heldItem.getItem() instanceof NaturalizationStaff) {
                    NaturalizationMode currentMode = NaturalizationStaff.getMode(heldItem);

                    // Check if shift is held - cycle backward if yes, forward if no
                    boolean shiftHeld = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                    NaturalizationMode nextMode = shiftHeld ? currentMode.previous() : currentMode.next();

                    // Update client-side immediately for tooltip
                    NaturalizationStaff.setMode(heldItem, nextMode);

                    // Send packet to server to sync the change
                    ModPackets.CHANNEL.sendToServer(new CycleModePacket(nextMode.ordinal()));

                    // Show mode change message
                    player.displayClientMessage(
                        Component.literal(COLOR_LABEL + "Naturalization Mode: " + COLOR_VALUE + nextMode.getDisplayName()),
                        true // Action bar
                    );
                }
            }

            // Handle opening settings screen
            if (player != null && KeyBindings.openSettings.consumeClick()) {
                mc.setScreen(new ConfigScreen(mc.screen));
            }

            // Handle toggle highlight
            if (player != null && KeyBindings.toggleHighlight.consumeClick()) {
                NaturalizationConfig.toggleHighlight();
                boolean enabled = NaturalizationConfig.showHighlight();
                player.displayClientMessage(
                    Component.literal(COLOR_LABEL + "Highlight: " + COLOR_VALUE + (enabled ? "ON" : "OFF")),
                    true // Action bar
                );
            }

            // Handle toggle messy edge
            if (player != null && KeyBindings.toggleMessyEdge.consumeClick()) {
                NaturalizationConfig.toggleMessyEdge();
                boolean enabled = NaturalizationConfig.isMessyEdge();
                player.displayClientMessage(
                    Component.literal(COLOR_LABEL + "Messy Edge: " + COLOR_VALUE + (enabled ? "ON" : "OFF")),
                    true // Action bar
                );
            }

            // Handle toggle shape
            if (player != null && KeyBindings.toggleShape.consumeClick()) {
                NaturalizationConfig.toggleShape();
                boolean isCircle = NaturalizationConfig.isCircleShape();
                player.displayClientMessage(
                    Component.literal(COLOR_LABEL + "Shape: " + COLOR_VALUE + (isCircle ? "Circle" : "Square")),
                    true // Action bar
                );
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Landscaper.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEventHandler {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            KeyBindings.register();
            event.register(KeyBindings.cycleMode);
            event.register(KeyBindings.openSettings);
            event.register(KeyBindings.toggleHighlight);
            event.register(KeyBindings.toggleMessyEdge);
            event.register(KeyBindings.toggleShape);
        }
    }

    // Register config screen (client-side only)
    static {
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                (mc, screen) -> new ConfigScreen(screen)
            )
        );
    }
}
