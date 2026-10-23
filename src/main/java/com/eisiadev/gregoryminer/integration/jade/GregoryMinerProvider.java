package com.eisiadev.gregoryminer.integration.jade;

import com.eisiadev.gregoryminer.GregoryMiner;
import com.eisiadev.gregoryminer.machine.GregoryMinerMachine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;

import java.util.ArrayList;
import java.util.List;

public final class GregoryMinerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final GregoryMinerProvider INSTANCE = new GregoryMinerProvider();
    private static final ResourceLocation UID = new ResourceLocation(GregoryMiner.MOD_ID, "miner_info");
    private static final String KEY = "GregoryMiner";

    private GregoryMinerProvider() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity be) ||
                !(be.getMetaMachine() instanceof GregoryMinerMachine miner)) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("Done", miner.getColumnsDone());
        tag.putInt("Total", miner.getTotalColumns());
        tag.putBoolean("Finished", miner.isDone());

        List<Component> lines = new ArrayList<>();
        miner.addDisplayText(lines, accessor.showDetails());
        ListTag list = new ListTag();
        for (Component c : lines) list.add(StringTag.valueOf(Component.Serializer.toJson(c)));
        tag.put("Lines", list);
        data.put(KEY, tag);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag tag = data.getCompound(KEY);

        int done = tag.getInt("Done");
        int total = Math.max(1, tag.getInt("Total"));
        float progress = tag.getBoolean("Finished") ? 1f : Math.min(1f, done / (float) total);
        BoxStyle box = new BoxStyle();
        box.borderWidth = 1;
        box.borderColor = 0xFF555555;
        tooltip.add(tooltip.getElementHelper().progress(
                progress,
                Component.translatable("gregoryminer.jade.progress", done, total),
                tooltip.getElementHelper().progressStyle().color(0xFF4CBB17).textColor(-1),
                box,
                true));

        ListTag list = tag.getList("Lines", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (i == 1) continue;
            Component c = Component.Serializer.fromJson(list.getString(i));
            if (c != null) tooltip.add(c);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
