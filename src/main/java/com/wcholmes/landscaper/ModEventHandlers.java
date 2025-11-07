package com.wcholmes.landscaper;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Landscaper.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModEventHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onCreativeModeTabBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            // In multiplayer, RegistryObject.isPresent() can return false after server sync
            // Use direct registry lookup instead, which is multiplayer-safe
            Item staff = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Landscaper.MODID, "naturalization_staff"));
            if (staff != null) {
                event.accept(staff);
            }
        }
    }
}
