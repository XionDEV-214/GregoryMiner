package com.eisiadev.gregoryminer.config;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class GMConfig {

    public static final int[] DEFAULT_INTERVAL = { 20, 15, 10, 5, 1 };
    public static final int[] DEFAULT_EU_PER_BLOCK = { 4, 12, 32, 64, 64 };

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue[] INTERVAL = new ForgeConfigSpec.IntValue[5];
    private static final ForgeConfigSpec.LongValue[] EU_PER_BLOCK = new ForgeConfigSpec.LongValue[5];
    private static final ForgeConfigSpec.IntValue AMPERAGE;
    private static final ForgeConfigSpec.IntValue MAX_AREA_CHUNKS;
    private static final ForgeConfigSpec.IntValue BUFFER_SLOTS;
    private static final ForgeConfigSpec.IntValue OUTPUT_STACKS_PER_TICK;
    private static final ForgeConfigSpec.EnumValue<ProtectionMode> PROTECTION_MODE;
    private static final ForgeConfigSpec.BooleanValue SKIP_NEGATIVE_HARDNESS;
    private static final ForgeConfigSpec.BooleanValue LOAD_UNLOADED_CHUNKS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_UNMINABLE;

    public enum ProtectionMode {
        NONE,
        COLUMN,
        BLOCK
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Per-tier speed and energy. One operation = clearing one full 1x1 column",
                "from (machine Y - 1) down to the bottom of the world.").push("tiers");
        for (int i = 0; i < 5; i++) {
            String name = GTValues.VN[GTValues.LV + i].toLowerCase(java.util.Locale.ROOT);
            b.push(name);
            INTERVAL[i] = b.comment("Ticks between two column operations.")
                    .defineInRange("ticksPerColumn", DEFAULT_INTERVAL[i], 1, 1200);
            EU_PER_BLOCK[i] = b.comment("EU consumed for every block (or fluid) removed in a column.")
                    .defineInRange("euPerBlock", (long) DEFAULT_EU_PER_BLOCK[i], 0L, Long.MAX_VALUE / 4096);
            b.pop();
        }
        b.pop();

        b.push("general");
        AMPERAGE = b.comment("Max amperage accepted by every Gregory Miner (at its tier voltage).")
                .defineInRange("inputAmperage", 2, 1, 16);
        MAX_AREA_CHUNKS = b.comment("Maximum width/depth of the mining area, in chunks (same for every tier).")
                .defineInRange("maxAreaChunks", 9, 1, 32);
        BUFFER_SLOTS = b.comment("Size of the internal safety buffer, counted in full stacks.",
                "When it is full the miner pauses until the output inventory accepts items again.")
                .defineInRange("bufferStacks", 512, 1, 1 << 20);
        OUTPUT_STACKS_PER_TICK = b.comment("Max number of stacks pushed into the output inventory per tick.")
                .defineInRange("outputStacksPerTick", 64, 1, 4096);
        PROTECTION_MODE = b.comment("How claim / protection mods are consulted (via a fake player owned by the machine owner).",
                "NONE = never, COLUMN = once per column (default), BLOCK = once per block.")
                .defineEnum("protectionMode", ProtectionMode.COLUMN);
        SKIP_NEGATIVE_HARDNESS = b.comment("Also skip any (modded) block whose hardness is negative.",
                "Bedrock & other vanilla unbreakables are always skipped regardless of this setting.")
                .define("skipNegativeHardness", true);
        LOAD_UNLOADED_CHUNKS = b.comment("If false (default) the miner waits while the target chunk is not loaded",
                "(use FTB Chunks or similar to keep the area loaded). If true it loads the chunk synchronously.")
                .define("loadUnloadedChunks", false);
        EXTRA_UNMINABLE = b.comment("Extra block ids that are never mined (in addition to the #gregoryminer:unminable tag).")
                .defineListAllowEmpty(List.of("extraUnminableBlocks"), () -> List.<String>of(),
                        o -> o instanceof String);
        b.pop();

        SPEC = b.build();
    }

    private GMConfig() {}

    private static int tierIndex(int tier) {
        return Math.max(0, Math.min(4, tier - GTValues.LV));
    }

    public static int interval(int tier) {
        try {
            return INTERVAL[tierIndex(tier)].get();
        } catch (IllegalStateException e) {
            return DEFAULT_INTERVAL[tierIndex(tier)];
        }
    }

    public static long euPerBlock(int tier) {
        try {
            return EU_PER_BLOCK[tierIndex(tier)].get();
        } catch (IllegalStateException e) {
            return DEFAULT_EU_PER_BLOCK[tierIndex(tier)];
        }
    }

    public static int amperage() {
        try {
            return AMPERAGE.get();
        } catch (IllegalStateException e) {
            return 2;
        }
    }

    public static int maxAreaChunks() {
        try {
            return MAX_AREA_CHUNKS.get();
        } catch (IllegalStateException e) {
            return 9;
        }
    }

    public static int bufferStacks() {
        try {
            return BUFFER_SLOTS.get();
        } catch (IllegalStateException e) {
            return 512;
        }
    }

    public static int outputStacksPerTick() {
        try {
            return OUTPUT_STACKS_PER_TICK.get();
        } catch (IllegalStateException e) {
            return 64;
        }
    }

    public static ProtectionMode protectionMode() {
        try {
            return PROTECTION_MODE.get();
        } catch (IllegalStateException e) {
            return ProtectionMode.COLUMN;
        }
    }

    public static boolean skipNegativeHardness() {
        try {
            return SKIP_NEGATIVE_HARDNESS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean loadUnloadedChunks() {
        try {
            return LOAD_UNLOADED_CHUNKS.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static List<? extends String> extraUnminable() {
        try {
            return EXTRA_UNMINABLE.get();
        } catch (IllegalStateException e) {
            return List.of();
        }
    }
}
