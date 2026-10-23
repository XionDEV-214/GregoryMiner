package com.eisiadev.gregoryminer;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

@GTAddon
public class GregoryMinerAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GregoryMiner.REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return GregoryMiner.MOD_ID;
    }
}
