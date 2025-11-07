package com.wcholmes.landscaper;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Landscaper.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModEventHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onCreativeModeTabBuildContents(BuildCreativeModeTabContentsEvent event) {
        LOGGER.info("Creative tab event fired for tab: {}", event.getTabKey());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            LOGGER.info("Tools & Utilities tab - checking NATURALIZATION_STAFF");
            LOGGER.info("NATURALIZATION_STAFF.isPresent(): {}", Landscaper.NATURALIZATION_STAFF.isPresent());
            if (Landscaper.NATURALIZATION_STAFF.isPresent()) {
                LOGGER.info("Adding NATURALIZATION_STAFF to creative tab");
                event.accept(Landscaper.NATURALIZATION_STAFF.get());
            } else {
                LOGGER.error("NATURALIZATION_STAFF is NOT present - cannot add to creative tab!");
            }
        }
    }
}
