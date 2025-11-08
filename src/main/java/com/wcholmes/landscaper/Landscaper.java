package com.wcholmes.landscaper;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.config.NaturalizationConfig;
import com.wcholmes.landscaper.item.NaturalizationStaff;
import com.wcholmes.landscaper.network.ModPackets;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
        LOGGER.info("=== Landscaper constructor starting ===");
        LOGGER.info("NATURALIZATION_STAFF defined: {}", NATURALIZATION_STAFF != null);
        LOGGER.info("NATURALIZATION_STAFF.isPresent() in constructor: {}", NATURALIZATION_STAFF.isPresent());

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup method
        modEventBus.addListener(this::commonSetup);

        // Register items
        LOGGER.info("Calling ITEMS.register(modEventBus)...");
        ITEMS.register(modEventBus);
        LOGGER.info("ITEMS.register() completed");
        LOGGER.info("NATURALIZATION_STAFF.isPresent() after ITEMS.register(): {}", NATURALIZATION_STAFF.isPresent());

        // Add items to creative tabs
        modEventBus.addListener(this::addCreative);

        // Register config screen (client-side only)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerConfigScreen();
        }
        LOGGER.info("=== Landscaper constructor complete ===");
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("Terrain Naturalization Tools is loading!");
        LOGGER.info("NATURALIZATION_STAFF.isPresent() in commonSetup: {}", NATURALIZATION_STAFF.isPresent());

        // Register network packets
        event.enqueueWork(ModPackets::register);

        // Load configuration file
        NaturalizationConfig.load();

        LOGGER.info("Naturalization Staff ready for terraforming!");
        LOGGER.info("NATURALIZATION_STAFF.isPresent() at end of commonSetup: {}", NATURALIZATION_STAFF.isPresent());
    }

    private void addCreative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event)
    {
        // Add the Naturalization Staff to the tools tab
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) {
            // Look up item directly from registry to avoid RegistryObject invalidation after server sync
            net.minecraft.world.item.Item staff = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation(MODID, "naturalization_staff"));
            if (staff != null) {
                event.accept(staff);
            }
        }
    }

    // Client-side only method to register config screen
    private void registerConfigScreen()
    {
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                (mc, screen) -> new com.wcholmes.landscaper.config.ConfigScreen(screen)
            )
        );
    }
}
