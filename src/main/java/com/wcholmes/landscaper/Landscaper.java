package com.wcholmes.landscaper;

import com.mojang.logging.LogUtils;
import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.item.NaturalizationStaff;
import com.wcholmes.landscaper.common.network.ModPackets;
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
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Landscaper mod initializing (v2.3.0)");
        }

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup method
        modEventBus.addListener(this::commonSetup);

        // Register items
        ITEMS.register(modEventBus);

        // Add items to creative tabs
        modEventBus.addListener(this::addCreative);

        LOGGER.info("Landscaper mod registered successfully");
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("Landscaper mod loading...");

        // Register network packets
        event.enqueueWork(() -> {
            ModPackets.register();
            NaturalizationConfig.load();
        });

        LOGGER.info("Landscaper ready! Naturalization Staff available for terraforming");
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
}
