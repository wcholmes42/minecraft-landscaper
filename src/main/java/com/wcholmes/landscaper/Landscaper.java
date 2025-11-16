package com.wcholmes.landscaper;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.server.commands.LandscaperCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Landscaper.MODID)
public class Landscaper
{
    public static final String MODID = "landscaper";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Landscaper()
    {
        LOGGER.info("Landscaper mod initializing (server-side only)");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup method
        modEventBus.addListener(this::commonSetup);

        // Register server event handler
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Landscaper registered successfully");
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("Landscaper loading...");

        event.enqueueWork(() -> {
            NaturalizationConfig.load();
        });

        LOGGER.info("Landscaper ready! Use /landscaper commands for terrain modification");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LandscaperCommand.register(event.getDispatcher());
        LOGGER.info("Landscaper commands registered");
    }
}
