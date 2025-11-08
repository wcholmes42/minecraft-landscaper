package com.wcholmes.landscaper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY = "key.categories.landscaper";
    public static final String KEY_CYCLE_MODE = "key.landscaper.cycle_mode";
    public static final String KEY_OPEN_SETTINGS = "key.landscaper.open_settings";
    public static final String KEY_TOGGLE_HIGHLIGHT = "key.landscaper.toggle_highlight";
    public static final String KEY_TOGGLE_MESSY_EDGE = "key.landscaper.toggle_messy_edge";
    public static final String KEY_TOGGLE_SHAPE = "key.landscaper.toggle_shape";

    public static KeyMapping cycleMode;
    public static KeyMapping openSettings;
    public static KeyMapping toggleHighlight;
    public static KeyMapping toggleMessyEdge;
    public static KeyMapping toggleShape;

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

        toggleHighlight = new KeyMapping(
            KEY_TOGGLE_HIGHLIGHT,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H, // Default: H key
            KEY_CATEGORY
        );

        toggleMessyEdge = new KeyMapping(
            KEY_TOGGLE_MESSY_EDGE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M, // Default: M key
            KEY_CATEGORY
        );

        toggleShape = new KeyMapping(
            KEY_TOGGLE_SHAPE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C, // Default: C key (Circle/sQuare)
            KEY_CATEGORY
        );
    }
}
