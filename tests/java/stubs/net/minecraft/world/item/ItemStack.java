package net.minecraft.world.item;

/** Algorithm fixture only. This does not emulate Minecraft serialization or its runtime. */
public final class ItemStack {
    private final String item, components;
    private final int maximum;
    private int count;
    public ItemStack(String item, String components, int count, int maximum) {
        this.item = item; this.components = components; this.count = count; this.maximum = maximum;
    }
    public ItemStack copy() { return copyWithCount(count); }
    public ItemStack copyWithCount(int amount) { return new ItemStack(item, components, amount, maximum); }
    public boolean isEmpty() { return count <= 0; }
    public int getCount() { return count; }
    public int getMaxStackSize() { return maximum; }
    public void grow(int amount) { count += amount; }
    public void shrink(int amount) { count -= amount; }
    public static boolean isSameItemSameComponents(ItemStack left, ItemStack right) {
        return left.item.equals(right.item) && left.components.equals(right.components);
    }
}
