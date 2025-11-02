package com.myfirstmod;

import com.myfirstmod.item.NaturalizationMode;
import com.myfirstmod.item.NaturalizationStaff;
import com.myfirstmod.network.CycleModePacket;
import com.myfirstmod.network.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MyFirstMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;

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
                        Component.literal("§6Naturalization Mode: §e" + nextMode.getDisplayName()),
                        true // Action bar
                    );
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MyFirstMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEventHandler {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            KeyBindings.register();
            event.register(KeyBindings.cycleMode);
        }
    }
}
