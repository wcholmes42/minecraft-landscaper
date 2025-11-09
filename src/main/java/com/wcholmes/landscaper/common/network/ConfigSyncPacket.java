package com.wcholmes.landscaper.common.network;

import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet to sync config changes from client to server.
 * Sent when player saves config in the config screen.
 */
public class ConfigSyncPacket {
    private final int radius;
    private final boolean consumeResources;
    private final boolean overworldOnly;
    private final String vegetationDensity;
    private final int messyEdgeExtension;

    public ConfigSyncPacket(int radius, boolean consumeResources, boolean overworldOnly,
                           NaturalizationConfig.VegetationDensity vegetationDensity,
                           int messyEdgeExtension) {
        this.radius = radius;
        this.consumeResources = consumeResources;
        this.overworldOnly = overworldOnly;
        this.vegetationDensity = vegetationDensity.name();
        this.messyEdgeExtension = messyEdgeExtension;
    }

    public ConfigSyncPacket(FriendlyByteBuf buf) {
        this.radius = buf.readInt();
        this.consumeResources = buf.readBoolean();
        this.overworldOnly = buf.readBoolean();
        this.vegetationDensity = buf.readUtf();
        this.messyEdgeExtension = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(radius);
        buf.writeBoolean(consumeResources);
        buf.writeBoolean(overworldOnly);
        buf.writeUtf(vegetationDensity);
        buf.writeInt(messyEdgeExtension);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Server-side only - update THIS PLAYER's config
            if (context.getSender() != null) {
                try {
                    NaturalizationConfig.VegetationDensity density =
                        NaturalizationConfig.VegetationDensity.valueOf(vegetationDensity);

                    // Store per-player settings on server
                    com.wcholmes.landscaper.common.config.PlayerConfig.updatePlayerSettings(
                        context.getSender().getUUID(),
                        radius,
                        density,
                        messyEdgeExtension,
                        consumeResources,
                        overworldOnly
                    );

                    // Log the sync
                    com.mojang.logging.LogUtils.getLogger().info(
                        "Updated personal config for {}: radius={}, vegetation={}, messyEdge={}",
                        context.getSender().getName().getString(), radius, density, messyEdgeExtension);
                } catch (IllegalArgumentException e) {
                    com.mojang.logging.LogUtils.getLogger().error(
                        "Invalid vegetation density from client: {}", vegetationDensity);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
