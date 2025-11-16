package com.wcholmes.landscaper.common.undo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Manages undo history for terrain operations.
 * Stores block changes per player with configurable depth.
 */
public class UndoManager {
    private static final int DEFAULT_UNDO_DEPTH = 10;

    // Player UUID -> Stack of operations
    private static final Map<UUID, Deque<UndoOperation>> playerUndoStacks = new HashMap<>();

    /**
     * Record a new operation for potential undo.
     */
    public static void recordOperation(Player player, List<BlockChange> changes) {
        if (player == null || changes.isEmpty()) return;

        UUID playerUUID = player.getUUID();
        Deque<UndoOperation> stack = playerUndoStacks.computeIfAbsent(playerUUID, k -> new ArrayDeque<>());

        // Add new operation
        stack.push(new UndoOperation(changes));

        // Limit stack size
        while (stack.size() > DEFAULT_UNDO_DEPTH) {
            stack.removeLast();
        }
    }

    /**
     * Undo the last operation for a player.
     * Returns the number of blocks restored, or -1 if nothing to undo.
     */
    public static int undoLastOperation(Player player, Level level) {
        if (player == null) return -1;

        UUID playerUUID = player.getUUID();
        Deque<UndoOperation> stack = playerUndoStacks.get(playerUUID);

        if (stack == null || stack.isEmpty()) {
            return -1; // Nothing to undo
        }

        UndoOperation operation = stack.pop();
        return operation.restore(level);
    }

    /**
     * Check if player has any undo history.
     */
    public static boolean hasUndoHistory(Player player) {
        if (player == null) return false;
        Deque<UndoOperation> stack = playerUndoStacks.get(player.getUUID());
        return stack != null && !stack.isEmpty();
    }

    /**
     * Get number of undo operations available for player.
     */
    public static int getUndoDepth(Player player) {
        if (player == null) return 0;
        Deque<UndoOperation> stack = playerUndoStacks.get(player.getUUID());
        return stack == null ? 0 : stack.size();
    }

    /**
     * Clear undo history for a player.
     */
    public static void clearHistory(Player player) {
        if (player != null) {
            playerUndoStacks.remove(player.getUUID());
        }
    }

    /**
     * Represents a single block change for undo.
     */
    public static class BlockChange {
        public final BlockPos pos;
        public final BlockState previousState;

        public BlockChange(BlockPos pos, BlockState previousState) {
            this.pos = pos.immutable();
            this.previousState = previousState;
        }
    }

    /**
     * Represents an undoable operation (collection of block changes).
     */
    private static class UndoOperation {
        private final List<BlockChange> changes;

        public UndoOperation(List<BlockChange> changes) {
            this.changes = new ArrayList<>(changes);
        }

        public int restore(Level level) {
            int restored = 0;
            for (BlockChange change : changes) {
                level.setBlock(change.pos, change.previousState, 3); // Update and notify clients
                restored++;
            }
            return restored;
        }
    }
}
