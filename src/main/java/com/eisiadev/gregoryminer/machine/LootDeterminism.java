package com.eisiadev.gregoryminer.machine;

import com.eisiadev.gregoryminer.GregoryMiner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyExplosionDecay;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LootDeterminism {

    private static final Map<ResourceLocation, Boolean> TABLES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> OVERRIDES_GET_DROPS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();
    private static final Set<String> COMPOSITE_CONDITIONS = Set.of("AllOfCondition", "AnyOfCondition",
            "AlternativeLootItemCondition");
    private static volatile Method baseGetDrops;
    private static volatile boolean warned = false;

    private LootDeterminism() {}

    public static void invalidate() {
        TABLES.clear();
    }

    public static boolean isDeterministic(ServerLevel level, BlockState state) {
        Block block = state.getBlock();
        if (overridesGetDrops(block)) return false;
        ResourceLocation id = block.getLootTable();
        if (id == null || BuiltInLootTables.EMPTY.equals(id)) return true;
        return TABLES.computeIfAbsent(id, key -> analyze(level.getServer().getLootData().getLootTable(key), key));
    }

    private static boolean overridesGetDrops(Block block) {
        return OVERRIDES_GET_DROPS.computeIfAbsent(block.getClass(), cls -> {
            try {
                Method base = baseGetDrops();
                if (base == null) return true;
                Method actual = cls.getMethod(base.getName(), base.getParameterTypes());
                return actual.getDeclaringClass() != BlockBehaviour.class;
            } catch (Throwable t) {
                return true;
            }
        });
    }

    private static Method baseGetDrops() {
        Method m = baseGetDrops;
        if (m != null) return m;
        for (Method candidate : BlockBehaviour.class.getDeclaredMethods()) {
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length == 2 && params[0] == BlockState.class && params[1] == LootParams.Builder.class &&
                    List.class.isAssignableFrom(candidate.getReturnType()) &&
                    Modifier.isPublic(candidate.getModifiers())) {
                baseGetDrops = candidate;
                return candidate;
            }
        }
        return null;
    }

    private static boolean analyze(LootTable table, ResourceLocation id) {
        try {
            for (LootItemFunction function : collect(table, LootItemFunction.class)) {
                if (!function(function)) return false;
            }
            for (LootPool pool : collect(table, LootPool.class)) {
                if (!pool(pool)) return false;
            }
            return true;
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                GregoryMiner.LOGGER.warn("Could not inspect loot table {}, drop caching disabled for it", id, t);
            }
            return false;
        }
    }

    private static boolean pool(LootPool pool) throws IllegalAccessException {
        for (NumberProvider rolls : collect(pool, NumberProvider.class)) {
            if (!(rolls instanceof ConstantValue)) return false;
        }
        for (LootItemCondition condition : collect(pool, LootItemCondition.class)) {
            if (!condition(condition)) return false;
        }
        for (LootItemFunction function : collect(pool, LootItemFunction.class)) {
            if (!function(function)) return false;
        }
        List<LootPoolEntryContainer> entries = collect(pool, LootPoolEntryContainer.class);
        return entries.size() == 1 && entry(entries.get(0));
    }

    private static boolean entry(LootPoolEntryContainer entry) throws IllegalAccessException {
        if (!(entry instanceof LootItem || entry instanceof EmptyLootItem || entry instanceof AlternativesEntry)) {
            return false;
        }
        for (LootItemCondition condition : collect(entry, LootItemCondition.class)) {
            if (!condition(condition)) return false;
        }
        for (LootItemFunction function : collect(entry, LootItemFunction.class)) {
            if (!function(function)) return false;
        }
        if (entry instanceof AlternativesEntry) {
            for (LootPoolEntryContainer child : collect(entry, LootPoolEntryContainer.class)) {
                if (!entry(child)) return false;
            }
        }
        return true;
    }

    private static boolean condition(LootItemCondition condition) throws IllegalAccessException {
        if (condition instanceof ExplosionCondition || condition instanceof MatchTool ||
                condition instanceof LootItemBlockStatePropertyCondition) {
            return true;
        }
        if (condition instanceof InvertedLootItemCondition ||
                COMPOSITE_CONDITIONS.contains(condition.getClass().getSimpleName())) {
            for (LootItemCondition nested : collect(condition, LootItemCondition.class)) {
                if (!condition(nested)) return false;
            }
            return true;
        }
        return false;
    }

    private static boolean function(LootItemFunction function) throws IllegalAccessException {
        if (!(function instanceof ApplyExplosionDecay || function instanceof SetItemCountFunction)) return false;
        for (LootItemCondition condition : collect(function, LootItemCondition.class)) {
            if (!condition(condition)) return false;
        }
        for (NumberProvider value : collect(function, NumberProvider.class)) {
            if (!(value instanceof ConstantValue)) return false;
        }
        return true;
    }

    private static <T> List<T> collect(Object owner, Class<T> type) throws IllegalAccessException {
        List<T> out = new ArrayList<>();
        for (Field field : fields(owner.getClass())) {
            Object value = field.get(owner);
            if (value == null) continue;
            Class<?> ft = field.getType();
            if (ft.isArray() && type.isAssignableFrom(ft.getComponentType())) {
                for (Object o : (Object[]) value) {
                    if (type.isInstance(o)) out.add(type.cast(o));
                }
            } else if (List.class.isAssignableFrom(ft) && listOf(field, type)) {
                for (Object o : (List<?>) value) {
                    if (type.isInstance(o)) out.add(type.cast(o));
                }
            } else if (type.isAssignableFrom(ft) && type.isInstance(value)) {
                out.add(type.cast(value));
            }
        }
        return out;
    }

    private static boolean listOf(Field field, Class<?> type) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1 &&
                pt.getActualTypeArguments()[0] instanceof Class<?> arg) {
            return type.isAssignableFrom(arg);
        }
        return false;
    }

    private static List<Field> fields(Class<?> cls) {
        return FIELDS.computeIfAbsent(cls, c -> {
            List<Field> list = new ArrayList<>();
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    list.add(f);
                }
            }
            return list;
        });
    }
}
