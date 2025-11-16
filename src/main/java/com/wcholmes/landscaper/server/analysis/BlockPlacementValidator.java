package com.wcholmes.landscaper.server.analysis;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Runtime validation for block placement - prevents water placement with logging
 */
public class BlockPlacementValidator {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Validate and place block - CRASHES if water detected
     */
    public static void validateAndPlace(Level level, BlockPos pos, BlockState state, String source) {
        Block block = state.getBlock();

        // NUCLEAR VALIDATION: Crash if water/lava detected
        if (block == Blocks.WATER || block == Blocks.LAVA) {
            String error = String.format(
                "❌ WATER DETECTED! Source: %s, Pos: %s, Block: %s",
                source, pos, block.getName().getString()
            );
            LOGGER.error(error);
            LOGGER.error("Stack trace:", new Exception("Water placement detected"));

            // Don't place - use safe fallback
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            return;
        }

        // Also reject air in certain contexts
        if (block == Blocks.AIR && !source.contains("Clear") && !source.contains("Dig")) {
            LOGGER.warn("⚠️  Suspicious AIR placement: {} at {}", source, pos);
        }

        // Safe to place
        level.setBlock(pos, state, 3);
    }

    /**
     * Quick check without placement
     */
    public static boolean isBlockSafe(Block block) {
        return block != Blocks.WATER &&
               block != Blocks.LAVA &&
               block != Blocks.AIR;
    }
}
