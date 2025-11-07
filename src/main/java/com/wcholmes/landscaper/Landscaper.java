package com.wcholmes.landscaper;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.config.ConfigScreen;
import com.wcholmes.landscaper.config.NaturalizationConfig;
import com.wcholmes.landscaper.item.NaturalizationStaff;
import com.wcholmes.landscaper.network.ModPackets;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
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
        LOGGER.info("=== Landscaper constructor starting ===");
        LOGGER.info("NATURALIZATION_STAFF defined: {}", NATURALIZATION_STAFF != null);
        LOGGER.info("NATURALIZATION_STAFF.isPresent() in constructor: {}", NATURALIZATION_STAFF.isPresent());

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup method
        modEventBus.addListener(this::commonSetup);

        // Register items - MUST happen before creative tab listener
        LOGGER.info("Calling ITEMS.register(modEventBus)...");
        ITEMS.register(modEventBus);
        LOGGER.info("ITEMS.register() completed");
        LOGGER.info("NATURALIZATION_STAFF.isPresent() after ITEMS.register(): {}", NATURALIZATION_STAFF.isPresent());

        // Register creative tab listener - happens after item registration
        modEventBus.addListener(this::addCreativeTab);
        LOGGER.info("Creative tab listener registered");

        // Register config screen
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                (mc, screen) -> new ConfigScreen(screen)
            )
        );
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

    /**
     * Adds mod items to creative mode tabs.
     * Registered in constructor after ITEMS.register() to ensure proper initialization order.
     */
    private void addCreativeTab(BuildCreativeModeTabContentsEvent event)
    {
        LOGGER.info("buildCreativeTabs called for tab: {}", event.getTabKey().location());

        // Add the Naturalization Staff to the tools tab
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            LOGGER.info("Processing TOOLS_AND_UTILITIES tab");
            LOGGER.info("NATURALIZATION_STAFF object: {}", NATURALIZATION_STAFF);
            LOGGER.info("NATURALIZATION_STAFF.getId(): {}", NATURALIZATION_STAFF.getId());
            LOGGER.info("NATURALIZATION_STAFF.isPresent() = {}", NATURALIZATION_STAFF.isPresent());

            if (NATURALIZATION_STAFF.isPresent()) {
                LOGGER.info("Adding Naturalization Staff to creative tab as ItemStack");
                event.accept(new ItemStack(NATURALIZATION_STAFF.get(), 1));
            } else {
                LOGGER.warn("NATURALIZATION_STAFF is not present during creative tab building!");
                LOGGER.warn("RegistryObject ID: {}", NATURALIZATION_STAFF.getId());
                LOGGER.warn("Trying to query registry directly...");
                try {
                    Item directLookup = ForgeRegistries.ITEMS.getValue(NATURALIZATION_STAFF.getId());
                    LOGGER.warn("Direct registry lookup result: {}", directLookup);
                    if (directLookup != null) {
                        LOGGER.warn("Item exists in registry! Adding as ItemStack with count=1...");
                        event.accept(new ItemStack(directLookup, 1));
                    }
                } catch (Exception e) {
                    LOGGER.error("Error during direct registry lookup", e);
                }
            }
        }
    }
}
