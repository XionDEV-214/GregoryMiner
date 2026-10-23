package com.eisiadev.gregoryminer.client;

import com.eisiadev.gregoryminer.machine.GregoryMinerMachine;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class AreaRenderRegistry {

    private static final Set<GregoryMinerMachine> MACHINES = Collections.newSetFromMap(new WeakHashMap<>());

    private AreaRenderRegistry() {}

    public static void add(GregoryMinerMachine machine) {
        MACHINES.add(machine);
    }

    public static void remove(GregoryMinerMachine machine) {
        MACHINES.remove(machine);
    }

    public static Set<GregoryMinerMachine> machines() {
        return MACHINES;
    }

    public static void clear() {
        MACHINES.clear();
    }
}
