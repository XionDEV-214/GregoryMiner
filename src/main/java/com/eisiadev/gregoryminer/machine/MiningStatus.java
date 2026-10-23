package com.eisiadev.gregoryminer.machine;

import net.minecraft.ChatFormatting;

public enum MiningStatus {

    IDLE("idle", ChatFormatting.GRAY),
    PAUSED("paused", ChatFormatting.YELLOW),
    WORKING("working", ChatFormatting.GOLD),
    NO_POWER("no_power", ChatFormatting.RED),
    CHUNK_NOT_LOADED("chunk_not_loaded", ChatFormatting.RED),
    BUFFER_FULL("buffer_full", ChatFormatting.RED),
    DONE("done", ChatFormatting.GREEN);

    public final String key;
    public final ChatFormatting color;

    MiningStatus(String key, ChatFormatting color) {
        this.key = key;
        this.color = color;
    }

    public String langKey() {
        return "gregoryminer.status." + key;
    }
}
