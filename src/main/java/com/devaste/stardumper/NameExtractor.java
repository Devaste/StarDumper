package com.devaste.stardumper;

import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.*;

/**
 * Exports display names of items, fluids, machines and recipe types.
 * A dedicated server only knows en_us strings from mod jars, and GT composes material names
 * ("%s Dust" + material key), so every entry carries the component JSON (translation key + args)
 * next to the server-resolved English string. Other languages are resolved later from lang files.
 */
public class NameExtractor {

    private static final Logger LOGGER = LogUtils.getLogger();

    private NameExtractor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Map<String, Object> extract() {
        Map<String, Object> out = new LinkedHashMap<>();

        Map<String, Object> items = new TreeMap<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> e : ForgeRegistries.ITEMS.getEntries()) {
            String id = e.getKey().location().toString();
            try {
                items.put(id, entry(new ItemStack(e.getValue()).getHoverName()));
            } catch (Exception ex) {
                LOGGER.warn("Failed to name item {}", id, ex);
            }
        }
        out.put("items", items);

        Map<String, Object> fluids = new TreeMap<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Fluid>, Fluid> e : ForgeRegistries.FLUIDS.getEntries()) {
            String id = e.getKey().location().toString();
            if (e.getValue() == Fluids.EMPTY) continue;
            try {
                fluids.put(id, entry(new FluidStack(e.getValue(), 1000).getDisplayName()));
            } catch (Exception ex) {
                LOGGER.warn("Failed to name fluid {}", id, ex);
            }
        }
        out.put("fluids", fluids);

        Map<String, Object> machines = new TreeMap<>();
        for (MachineDefinition def : GTRegistries.MACHINES.values()) {
            try {
                machines.put(def.getId().toString(), entry(Component.translatable(def.getDescriptionId())));
            } catch (Exception ex) {
                LOGGER.warn("Failed to name machine {}", def.getId(), ex);
            }
        }
        out.put("machines", machines);

        Map<String, Object> types = new TreeMap<>();
        for (GTRecipeType type : GTRegistries.RECIPE_TYPES.values()) {
            types.put(type.registryName.toString(), entry(Component.translatable(type.registryName.toLanguageKey())));
        }
        out.put("recipe_types", types);
        return out;
    }

    private static Map<String, Object> entry(Component component) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("en", component.getString());
        data.put("component", JsonParser.parseString(Component.Serializer.toJson(component)));
        return data;
    }
}
