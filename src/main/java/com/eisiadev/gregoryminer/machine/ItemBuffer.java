package com.eisiadev.gregoryminer.machine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Objects;

public final class ItemBuffer {

    private static final Hash.Strategy<ItemStack> STRATEGY = new Hash.Strategy<>() {

        @Override
        public int hashCode(ItemStack o) {
            if (o == null) return 0;
            return 31 * System.identityHashCode(o.getItem()) + Objects.hashCode(o.getTag());
        }

        @Override
        public boolean equals(ItemStack a, ItemStack b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            return ItemStack.isSameItemSameTags(a, b);
        }
    };

    private final Object2LongLinkedOpenCustomHashMap<ItemStack> entries = new Object2LongLinkedOpenCustomHashMap<>(
            STRATEGY);
    private long usedStacks = 0;

    private static long stacksFor(long count, int maxStack) {
        return count <= 0 ? 0 : (count + maxStack - 1) / maxStack;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int kinds() {
        return entries.size();
    }

    public long usedStacks() {
        return usedStacks;
    }

    public long totalItems() {
        long total = 0;
        for (long v : entries.values()) total += v;
        return total;
    }

    public void add(ItemStack stack, long count) {
        if (stack.isEmpty() || count <= 0) return;
        int max = Math.max(1, stack.getMaxStackSize());
        long before = entries.getLong(stack);
        long after = before + count;
        if (after < 0) after = Long.MAX_VALUE;
        if (before == 0) {
            entries.put(stack.copyWithCount(1), after);
        } else {
            entries.put(stack, after);
        }
        usedStacks += stacksFor(after, max) - stacksFor(before, max);
    }

    public void add(ItemStack stack) {
        add(stack, stack.getCount());
    }

    public long flushInto(IItemHandler target, int maxStacks) {
        long moved = 0;
        int calls = 0;
        ObjectIterator<Object2LongMap.Entry<ItemStack>> it = entries.object2LongEntrySet().fastIterator();
        while (it.hasNext() && calls < maxStacks) {
            Object2LongMap.Entry<ItemStack> e = it.next();
            ItemStack key = e.getKey();
            int max = Math.max(1, key.getMaxStackSize());
            long count = e.getLongValue();
            long before = count;
            while (count > 0 && calls < maxStacks) {
                int n = (int) Math.min(count, max);
                ItemStack rest = ItemHandlerHelper.insertItemStacked(target, key.copyWithCount(n), false);
                calls++;
                int inserted = n - rest.getCount();
                count -= inserted;
                if (!rest.isEmpty()) break;
            }
            if (count != before) {
                moved += before - count;
                usedStacks += stacksFor(count, max) - stacksFor(before, max);
                if (count <= 0) it.remove();
                else e.setValue(count);
            }
        }
        return moved;
    }

    public void clear() {
        entries.clear();
        usedStacks = 0;
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Object2LongMap.Entry<ItemStack> e : entries.object2LongEntrySet()) {
            CompoundTag tag = e.getKey().save(new CompoundTag());
            tag.putLong("GMCount", e.getLongValue());
            list.add(tag);
        }
        return list;
    }

    public void load(ListTag list) {
        clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            long count = tag.getLong("GMCount");
            ItemStack stack = ItemStack.of(tag);
            if (!stack.isEmpty() && count > 0) add(stack, count);
        }
    }
}
