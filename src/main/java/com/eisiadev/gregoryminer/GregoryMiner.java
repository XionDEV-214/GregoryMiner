package com.eisiadev.gregoryminer;

import com.eisiadev.gregoryminer.common.GMMachines;
import com.eisiadev.gregoryminer.config.GMConfig;
import com.eisiadev.gregoryminer.machine.BlockRules;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(GregoryMiner.MOD_ID)
public class GregoryMiner {

    public static final String MOD_ID = "gregoryminer";
    public static final String NAME = "Gregory Miner";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public GregoryMiner() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GMConfig.SPEC);

        modBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        modBus.addListener(this::onConfigLoad);
        modBus.addListener(this::onConfigReload);

        REGISTRATE.registerRegistrate();

        MinecraftForge.EVENT_BUS.addListener(this::onTagsUpdated);
    }

    private void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        GMMachines.init();
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == GMConfig.SPEC) BlockRules.invalidate();
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == GMConfig.SPEC) BlockRules.invalidate();
    }

    private void onTagsUpdated(TagsUpdatedEvent event) {
        BlockRules.invalidate();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
