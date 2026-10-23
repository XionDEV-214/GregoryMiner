package com.eisiadev.gregoryminer.machine;

import com.eisiadev.gregoryminer.GregoryMiner;
import com.eisiadev.gregoryminer.config.GMConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.Set;

public final class BlockRules {

    public static final TagKey<Block> UNMINABLE = TagKey.create(Registries.BLOCK,
            new ResourceLocation(GregoryMiner.MOD_ID, "unminable"));

    private static final Set<Block> HARDCODED = new ReferenceOpenHashSet<>(new Block[] {
            Blocks.BEDROCK, Blocks.BARRIER, Blocks.LIGHT, Blocks.STRUCTURE_VOID,
            Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.NETHER_PORTAL,
            Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW, Blocks.MOVING_PISTON
    });

    public static final byte SKIP = 0;
    public static final byte MINE = 1;
    public static final byte FLUID = 2;

    private static final Reference2ByteOpenHashMap<Block> CACHE = new Reference2ByteOpenHashMap<>();
    private static volatile Set<Block> extraUnminable = null;

    private BlockRules() {}

    public static void invalidate() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        extraUnminable = null;
    }

    public static byte classify(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.hasBlockEntity()) return SKIP;
        Block block = state.getBlock();
        byte cached;
        synchronized (CACHE) {
            cached = CACHE.getOrDefault(block, (byte) -1);
        }
        if (cached >= 0) return cached;

        byte result = compute(state, level, pos);
        synchronized (CACHE) {
            CACHE.put(block, result);
        }
        return result;
    }

    private static byte compute(BlockState state, BlockGetter level, BlockPos pos) {
        Block block = state.getBlock();
        if (HARDCODED.contains(block)) return SKIP;
        if (state.is(UNMINABLE)) return SKIP;
        if (getExtra().contains(block)) return SKIP;
        if (block instanceof LiquidBlock || block == Blocks.BUBBLE_COLUMN) return FLUID;
        if (GMConfig.skipNegativeHardness() && state.getDestroySpeed(level, pos) < 0) return SKIP;
        return MINE;
    }

    private static Set<Block> getExtra() {
        Set<Block> set = extraUnminable;
        if (set == null) {
            set = new ReferenceOpenHashSet<>();
            for (String id : GMConfig.extraUnminable()) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) continue;
                Block b = ForgeRegistries.BLOCKS.getValue(rl);
                if (b != null && b != Blocks.AIR) set.add(b);
            }
            extraUnminable = set;
        }
        return set;
    }
}
