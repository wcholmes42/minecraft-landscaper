package com.wcholmes.landscaper.network;

import com.wcholmes.landscaper.Landscaper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModPackets {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(Landscaper.MODID, "main"),
        () -> PROTOCOL_VERSION,
        clientVersion -> true,  // Accept any client version - never block connections
        serverVersion -> true   // Accept any server version - never block connections
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(
            packetId++,
            CycleModePacket.class,
            CycleModePacket::encode,
            CycleModePacket::decode,
            CycleModePacket::handle
        );
    }
}
