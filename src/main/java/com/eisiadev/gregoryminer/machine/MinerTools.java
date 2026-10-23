package com.eisiadev.gregoryminer.machine;

import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class MinerTools {

    private static ItemStack fallbackTool;

    private MinerTools() {}

    public static boolean isValidTool(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IGTTool tool)) return false;
        GTToolType type = tool.getToolType();
        if (type == GTToolType.PICKAXE) return true;
        if (tool.getToolClasses(stack).contains(GTToolType.PICKAXE)) return true;
        return tool.getToolClassNames(stack).contains("drill");
    }

    public static int fortune(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, stack);
    }

    public static int silkTouch(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, stack);
    }

    public static ItemStack fallbackTool() {
        if (fallbackTool == null) fallbackTool = new ItemStack(Items.NETHERITE_PICKAXE);
        return fallbackTool;
    }
}
