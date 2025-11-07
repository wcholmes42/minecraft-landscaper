package com.myfirstmod;

import com.mojang.logging.LogUtils;
import com.myfirstmod.config.ConfigScreen;
import com.myfirstmod.config.NaturalizationConfig;
import com.myfirstmod.item.NaturalizationStaff;
import com.myfirstmod.network.ModPackets;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(MyFirstMod.MODID)
public class MyFirstMod
{
    public static final String MODID = "landscaper";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Registry for items
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    // Create the Naturalization Staff
    public static final RegistryObject<Item> NATURALIZATION_STAFF = ITEMS.register("naturalization_staff",
        () -> new NaturalizationStaff(new Item.Properties().stacksTo(1)));

    public MyFirstMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup method
        modEventBus.addListener(this::commonSetup);

        // Register items
        ITEMS.register(modEventBus);

        // Register for game events
        MinecraftForge.EVENT_BUS.register(this);

        // Add items to creative tabs
        modEventBus.addListener(this::addCreative);

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

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        // Add the Naturalization Staff to the tools tab
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
            event.accept(NATURALIZATION_STAFF);
    }
}
