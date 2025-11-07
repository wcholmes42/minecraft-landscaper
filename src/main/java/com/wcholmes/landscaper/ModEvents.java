package com.wcholmes.landscaper;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles mod bus events (initialization, registration, creative tabs).
 * Automatically registered via @Mod.EventBusSubscriber annotation.
 */
@Mod.EventBusSubscriber(modid = Landscaper.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {

    /**
     * Adds mod items to creative mode tabs.
     * This event fires during creative tab building and allows mods to add their items.
     */
    @SubscribeEvent
    public static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        // Add the Naturalization Staff to the tools tab
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            // Check if registry object is present to avoid timing issues
            if (Landscaper.NATURALIZATION_STAFF.isPresent()) {
                event.accept(Landscaper.NATURALIZATION_STAFF.get());
            }
        }
    }
}
