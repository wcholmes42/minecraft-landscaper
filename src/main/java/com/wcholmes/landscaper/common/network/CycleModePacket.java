package com.wcholmes.landscaper.common.network;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.common.item.NaturalizationMode;
import com.wcholmes.landscaper.common.item.NaturalizationStaff;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class CycleModePacket {
    private static final Logger LOGGER = LogUtils.getLogger();
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
            // Server-side handling with validation
            ServerPlayer player = context.getSender();
            if (player == null) {
                LOGGER.warn("Received CycleModePacket with null sender");
                return;
            }

            // Rate limiting: max 10 packets per second per player
            if (!PacketRateLimiter.allowPacket(player.getUUID(), "cycle_mode", 10, 1000)) {
                LOGGER.warn("Player {} is spamming CycleModePacket", player.getName().getString());
                return;
            }

            // Validate player is holding the staff
            ItemStack heldItem = player.getMainHandItem();
            if (!(heldItem.getItem() instanceof NaturalizationStaff)) {
                LOGGER.warn("Player {} sent CycleModePacket without holding staff", player.getName().getString());
                return;
            }

            // Validate mode ordinal
            NaturalizationMode[] modes = NaturalizationMode.values();
            if (packet.newModeOrdinal < 0 || packet.newModeOrdinal >= modes.length) {
                LOGGER.error("Player {} sent invalid mode ordinal: {}",
                    player.getName().getString(), packet.newModeOrdinal);
                return;
            }

            // All validation passed - apply mode change
            NaturalizationMode newMode = modes[packet.newModeOrdinal];
            NaturalizationStaff.setMode(heldItem, newMode);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Player {} changed staff mode to {}",
                    player.getName().getString(), newMode.getDisplayName());
            }
        });
        context.setPacketHandled(true);
    }
}
