package com.myfirstmod;

import com.mojang.logging.LogUtils;
import com.myfirstmod.config.NaturalizationConfig;
import com.myfirstmod.item.NaturalizationStaff;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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

    // Registry for blocks
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Registry for items
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    // Create a custom block - this will appear in the game!
    public static final RegistryObject<Block> RUBY_BLOCK = BLOCKS.register("ruby_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .strength(3.0f, 3.0f)
            .requiresCorrectToolForDrops()));

    // Create a block item so the block can be held in inventory
    public static final RegistryObject<Item> RUBY_BLOCK_ITEM = ITEMS.register("ruby_block",
        () -> new BlockItem(RUBY_BLOCK.get(), new Item.Properties()));

    // Create a custom item - a ruby gem!
    public static final RegistryObject<Item> RUBY = ITEMS.register("ruby",
        () -> new Item(new Item.Properties()));

    // Create the Naturalization Staff - the real mod!
    public static final RegistryObject<Item> NATURALIZATION_STAFF = ITEMS.register("naturalization_staff",
        () -> new NaturalizationStaff(new Item.Properties().stacksTo(1)));

    public MyFirstMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup method
        modEventBus.addListener(this::commonSetup);

        // Register our blocks and items
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        // Register for game events
        MinecraftForge.EVENT_BUS.register(this);

        // Add items to creative tabs
        modEventBus.addListener(this::addCreative);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("Terrain Naturalization Tools is loading!");

        // Load configuration file
        NaturalizationConfig.load();

        LOGGER.info("Naturalization Staff ready for terraforming!");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        // Add our ruby block to the building blocks creative tab
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
            event.accept(RUBY_BLOCK_ITEM);

        // Add our ruby item to the ingredients creative tab
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS)
            event.accept(RUBY);

        // Add the Naturalization Staff to the tools tab
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
            event.accept(NATURALIZATION_STAFF);
    }
}
