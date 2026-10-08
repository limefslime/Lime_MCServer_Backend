package com.namanseul.farmingmod.server.shop;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Plans complete insertion on copies; a failed plan never modifies live inventory or escrow. */
public final class TradeInventoryPlan {
    private TradeInventoryPlan() {}
    public static List<ItemStack> insert(List<ItemStack> inventory, List<ItemStack> returning) {
        List<ItemStack> planned = new ArrayList<>();
        for (ItemStack stack : inventory) planned.add(stack.copy());
        for (ItemStack original : returning) {
            if (original.isEmpty()) throw new IllegalArgumentException("empty escrow item");
            ItemStack remaining = original.copy();
            for (ItemStack target : planned) {
                if (!target.isEmpty() && ItemStack.isSameItemSameComponents(target, remaining)) {
                    int count = Math.min(remaining.getCount(), Math.max(0,
                            Math.min(64, target.getMaxStackSize()) - target.getCount()));
                    target.grow(count);
                    remaining.shrink(count);
                }
            }
            for (int slot = 0; slot < planned.size() && !remaining.isEmpty(); slot++) {
                if (planned.get(slot).isEmpty()) {
                    int count = Math.min(remaining.getCount(), Math.min(64, remaining.getMaxStackSize()));
                    planned.set(slot, remaining.copyWithCount(count));
                    remaining.shrink(count);
                }
            }
            if (!remaining.isEmpty()) return null;
        }
        return planned;
    }
}
