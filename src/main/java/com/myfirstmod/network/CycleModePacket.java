package com.myfirstmod.network;

import com.myfirstmod.item.NaturalizationMode;
import com.myfirstmod.item.NaturalizationStaff;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CycleModePacket {
    private final int newModeOrdinal;

    public CycleModePacket(int newModeOrdinal) {
        this.newModeOrdinal = newModeOrdinal;
    }

    public static void encode(CycleModePacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.newModeOrdinal);
    }

    public static CycleModePacket decode(FriendlyByteBuf buffer) {
        return new CycleModePacket(buffer.readInt());
    }

    public static void handle(CycleModePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Server-side handling
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack heldItem = player.getMainHandItem();
                if (heldItem.getItem() instanceof NaturalizationStaff) {
                    // Validate mode ordinal
                    NaturalizationMode[] modes = NaturalizationMode.values();
                    if (packet.newModeOrdinal >= 0 && packet.newModeOrdinal < modes.length) {
                        NaturalizationMode newMode = modes[packet.newModeOrdinal];
                        NaturalizationStaff.setMode(heldItem, newMode);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
