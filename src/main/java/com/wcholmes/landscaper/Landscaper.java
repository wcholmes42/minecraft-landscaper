package com.wcholmes.landscaper;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.config.ConfigScreen;
import com.wcholmes.landscaper.config.NaturalizationConfig;
import com.wcholmes.landscaper.item.NaturalizationStaff;
import com.wcholmes.landscaper.network.ModPackets;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(Landscaper.MODID)
public class Landscaper
{
    public static final String MODID = "landscaper";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Registry for items
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    // Create the Naturalization Staff
    public static final RegistryObject<Item> NATURALIZATION_STAFF = ITEMS.register("naturalization_staff",
        () -> new NaturalizationStaff(new Item.Properties().stacksTo(1)));

    public Landscaper()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup method
        modEventBus.addListener(this::commonSetup);

        // Register items
        ITEMS.register(modEventBus);

        // Register config screen
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                (mc, screen) -> new ConfigScreen(screen)
            )
        );
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("Terrain Naturalization Tools is loading!");

        // Register network packets
        event.enqueueWork(ModPackets::register);

        // Load configuration file
        NaturalizationConfig.load();

        LOGGER.info("Naturalization Staff ready for terraforming!");
    }
}
