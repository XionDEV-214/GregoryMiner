package com.eisiadev.gregoryminer.machine;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.ChatFormatting;

public enum MiningStatus {

    IDLE("idle", ChatFormatting.GRAY, RecipeLogic.Status.IDLE),
    PAUSED("paused", ChatFormatting.YELLOW, RecipeLogic.Status.SUSPEND),
    WORKING("working", ChatFormatting.GOLD, RecipeLogic.Status.WORKING),
    NO_POWER("no_power", ChatFormatting.RED, RecipeLogic.Status.WORKING),
    CHUNK_NOT_LOADED("chunk_not_loaded", ChatFormatting.RED, RecipeLogic.Status.WORKING),
    BUFFER_FULL("buffer_full", ChatFormatting.RED, RecipeLogic.Status.WAITING),
    DONE("done", ChatFormatting.GREEN, RecipeLogic.Status.IDLE);

    public final String key;
    public final ChatFormatting color;
    public final RecipeLogic.Status renderStatus;

    MiningStatus(String key, ChatFormatting color, RecipeLogic.Status renderStatus) {
        this.key = key;
        this.color = color;
        this.renderStatus = renderStatus;
    }

    public String langKey() {
        return "gregoryminer.status." + key;
    }
}
