package com.devaste.stardumper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public class FormatUtils {
    private FormatUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static String formatItemStack(ItemStack stack) {
        ResourceLocation reg = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String regName = reg != null ? reg.toString() : "minecraft:air";
        return String.format("Item{amount=%d, items=[%s]}", stack.getCount(), regName);
    }

    public static String formatFluidStack(FluidStack stack) {
        ResourceLocation reg = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
        String regName = reg != null ? reg.toString() : "minecraft:empty";
        return String.format("Fluid{amount=%d, fluids=[%s]}", stack.getAmount(), regName);
    }

}
