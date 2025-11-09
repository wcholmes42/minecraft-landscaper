package com.wcholmes.landscaper.common.transaction;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Transaction-based resource management for atomic inventory operations.
 *
 * <p>Ensures resources consumed exactly match blocks placed, preventing duplication exploits.
 * Based on Applied Energistics 2's transaction pattern.
 *
 * @since 2.3.0
 */
public class ResourceTransaction {
    private final Map<Item, Integer> consumed = new HashMap<>();
    private final Player player;
    private boolean committed = false;

    public ResourceTransaction(Player player) {
        this.player = player;
    }

    /**
     * Records resource consumption (called during terrain modification).
     *
     * @param item the item type to consume
     * @param count number of items to consume
     */
    public void consume(Item item, int count) {
        if (!committed) {
            consumed.merge(item, count, Integer::sum);
        }
    }

    /**
     * Checks if player has all required resources.
     *
     * @return true if player has sufficient resources
     */
    public boolean hasResources() {
        if (player.isCreative()) {
            return true;
        }

        Inventory inventory = player.getInventory();
        for (Map.Entry<Item, Integer> entry : consumed.entrySet()) {
            int have = countItem(inventory, entry.getKey());
            if (have < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Commits the transaction, removing resources from inventory.
     *
     * @return true if commit succeeded, false if insufficient resources
     */
    public boolean commit() {
        if (committed) {
            return true;
        }

        if (player.isCreative()) {
            committed = true;
            return true;
        }

        if (!hasResources()) {
            return false;
        }

        // Remove resources
        Inventory inventory = player.getInventory();
        for (Map.Entry<Item, Integer> entry : consumed.entrySet()) {
            removeItems(inventory, entry.getKey(), entry.getValue());
        }

        inventory.setChanged();
        committed = true;
        return true;
    }

    /**
     * Gets missing resources (for error messages).
     *
     * @return map of missing items and quantities
     */
    public Map<Item, Integer> getMissingResources() {
        Map<Item, Integer> missing = new HashMap<>();

        if (player.isCreative()) {
            return missing;
        }

        Inventory inventory = player.getInventory();
        for (Map.Entry<Item, Integer> entry : consumed.entrySet()) {
            int have = countItem(inventory, entry.getKey());
            int need = entry.getValue();

            if (have < need) {
                missing.put(entry.getKey(), need - have);
            }
        }

        return missing;
    }

    /**
     * Formats missing resources for user-friendly error message.
     *
     * @return formatted string like "5x Dirt, 3x Sand"
     */
    public String formatMissingResources() {
        Map<Item, Integer> missing = getMissingResources();
        if (missing.isEmpty()) {
            return "None";
        }

        return missing.entrySet().stream()
            .map(e -> e.getValue() + "x " + e.getKey().getDescription().getString())
            .reduce((a, b) -> a + ", " + b)
            .orElse("None");
    }

    private int countItem(Inventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeItems(Inventory inventory, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
            }
        }
    }
}
