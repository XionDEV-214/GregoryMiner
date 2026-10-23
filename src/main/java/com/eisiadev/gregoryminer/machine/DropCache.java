package com.eisiadev.gregoryminer.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

final class DropCache {

    private static final int RECHECK_EVERY = 64;
    private static final Entry UNCACHEABLE = new Entry(List.of());

    private static final class Entry {

        final List<ItemStack> drops;
        int uses;

        Entry(List<ItemStack> drops) {
            this.drops = drops;
        }
    }

    private final Reference2ObjectOpenHashMap<BlockState, Entry> entries = new Reference2ObjectOpenHashMap<>();

    void clear() {
        entries.clear();
    }

    List<ItemStack> getDrops(ServerLevel level, BlockState state, BlockPos pos, ItemStack tool, boolean enabled) {
        if (!enabled) return compute(level, state, pos, tool);
        Entry entry = entries.get(state);
        if (entry == UNCACHEABLE) return compute(level, state, pos, tool);
        if (entry == null) {
            List<ItemStack> real = compute(level, state, pos, tool);
            entries.put(state, LootDeterminism.isDeterministic(level, state) ? new Entry(copy(real)) : UNCACHEABLE);
            return real;
        }
        if (++entry.uses % RECHECK_EVERY == 0) {
            List<ItemStack> real = compute(level, state, pos, tool);
            if (!same(real, entry.drops)) entries.put(state, UNCACHEABLE);
            return real;
        }
        return entry.drops;
    }

    private static List<ItemStack> compute(ServerLevel level, BlockState state, BlockPos pos, ItemStack tool) {
        LootParams.Builder loot = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, tool);
        return state.getDrops(loot);
    }

    private static List<ItemStack> copy(List<ItemStack> list) {
        List<ItemStack> out = new ArrayList<>(list.size());
        for (ItemStack s : list) out.add(s.copy());
        return List.copyOf(out);
    }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        }
        return true;
    }
}
