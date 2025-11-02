package com.myfirstmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY = "key.categories.landscaper";
    public static final String KEY_CYCLE_MODE = "key.landscaper.cycle_mode";
    public static final String KEY_OPEN_SETTINGS = "key.landscaper.open_settings";

    public static KeyMapping cycleMode;
    public static KeyMapping openSettings;

    public static void register() {
        cycleMode = new KeyMapping(
            KEY_CYCLE_MODE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, // Default: V key
            KEY_CATEGORY
        );

        openSettings = new KeyMapping(
            KEY_OPEN_SETTINGS,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, // Default: K key
            KEY_CATEGORY
        );
    }
}
