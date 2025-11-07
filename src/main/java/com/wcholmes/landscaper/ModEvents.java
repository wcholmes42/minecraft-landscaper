package com.wcholmes.landscaper;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Handles mod bus events (initialization, registration, creative tabs).
 * Automatically registered via @Mod.EventBusSubscriber annotation.
 */
@Mod.EventBusSubscriber(modid = Landscaper.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Adds mod items to creative mode tabs.
     * This event fires during creative tab building and allows mods to add their items.
     */
    @SubscribeEvent
    public static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        LOGGER.info("buildCreativeTabs called for tab: {}", event.getTabKey().location());

        // Add the Naturalization Staff to the tools tab
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            LOGGER.info("Processing TOOLS_AND_UTILITIES tab");
            LOGGER.info("NATURALIZATION_STAFF.isPresent() = {}", Landscaper.NATURALIZATION_STAFF.isPresent());

            // Check if registry object is present to avoid timing issues
            if (Landscaper.NATURALIZATION_STAFF.isPresent()) {
                LOGGER.info("Adding Naturalization Staff to creative tab");
                event.accept(Landscaper.NATURALIZATION_STAFF.get());
            } else {
                LOGGER.warn("NATURALIZATION_STAFF is not present during creative tab building!");
            }
        }
    }
}
