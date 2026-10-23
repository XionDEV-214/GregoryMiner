package com.eisiadev.gregoryminer.common;

import com.eisiadev.gregoryminer.GregoryMiner;
import com.eisiadev.gregoryminer.config.GMConfig;
import com.eisiadev.gregoryminer.machine.GregoryMinerMachine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

import com.tterrag.registrate.util.entry.RegistryEntry;

import static com.gregtechceu.gtceu.api.GTValues.EV;
import static com.gregtechceu.gtceu.api.GTValues.HV;
import static com.gregtechceu.gtceu.api.GTValues.IV;
import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.MV;

public final class GMMachines {

    public static RegistryEntry<CreativeModeTab> TAB;
    public static MachineDefinition[] GREGORY_MINER;

    private GMMachines() {}

    public static void init() {
        if (GREGORY_MINER != null) return;

        TAB = GregoryMiner.REGISTRATE.defaultCreativeTab(GregoryMiner.MOD_ID,
                builder -> builder
                        .displayItems(new GTCreativeModeTabs.RegistrateDisplayItemsGenerator(GregoryMiner.MOD_ID,
                                GregoryMiner.REGISTRATE))
                        .icon(() -> GREGORY_MINER[IV].asStack())
                        .title(Component.translatable("itemGroup.gregoryminer"))
                        .build())
                .register();
        GregoryMiner.REGISTRATE.creativeModeTab(() -> TAB);

        GREGORY_MINER = GTMachineUtils.registerTieredMachines(GregoryMiner.REGISTRATE, "gregory_miner",
                GregoryMinerMachine::new,
                (tier, builder) -> builder
                        .rotationState(RotationState.NON_Y_AXIS)
                        .langValue("%s Gregory Miner".formatted(GTValues.VN[tier]))
                        .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                        .workableTieredHullModel(GTCEu.id("block/machines/miner"))
                        .tooltipBuilder((stack, tooltip) -> {
                            int side = GregoryMinerMachine.filterGridSize(tier);
                            int maxChunks = GMConfig.maxAreaChunks();
                            tooltip.add(Component.translatable("gregoryminer.tooltip.desc")
                                    .withStyle(ChatFormatting.GRAY));
                            tooltip.add(Component.translatable("gregoryminer.tooltip.speed",
                                    GMConfig.interval(tier)));
                            tooltip.add(Component.translatable("gregoryminer.tooltip.energy",
                                    FormattingUtil.formatNumbers(GMConfig.euPerColumn(tier))));
                            tooltip.add(Component.translatable("gregoryminer.tooltip.area", maxChunks, maxChunks));
                            tooltip.add(Component.translatable("gregoryminer.tooltip.tool"));
                            tooltip.add(Component.translatable("gregoryminer.tooltip.filter", side, side));
                            tooltip.add(Component.translatable("gtceu.universal.tooltip.voltage_in",
                                    FormattingUtil.formatNumbers(GTValues.V[tier]), GTValues.VNF[tier]));
                            tooltip.add(Component.translatable("gtceu.universal.tooltip.amperage_in",
                                    GMConfig.amperage()));
                            tooltip.add(Component.translatable("gtceu.universal.tooltip.energy_storage_capacity",
                                    FormattingUtil.formatNumbers(GTValues.V[tier] * 64L)));
                        })
                        .register(),
                LV, MV, HV, EV, IV);
    }
}
