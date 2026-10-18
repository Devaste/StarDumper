package com.devaste.stardumper;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.*;

/**
 * Exports recipe type limits and machine definitions (tier, recipe types, multiblock / generator flags),
 * which decide how recipes are run (parallels, overclocks, I/O limits).
 */
public class MachineExtractor {

    private MachineExtractor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Map<String, Object> extract() {
        Map<String, Object> out = new LinkedHashMap<>();

        Map<String, Object> types = new TreeMap<>();
        for (GTRecipeType type : GTRegistries.RECIPE_TYPES.values()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("group", type.group);
            data.put("max_inputs", capMap(type.maxInputs));
            data.put("max_outputs", capMap(type.maxOutputs));
            data.put("layered", type.isLayered());
            types.put(type.registryName.toString(), data);
        }
        out.put("recipe_types", types);

        Map<String, Object> machines = new TreeMap<>();
        for (MachineDefinition def : GTRegistries.MACHINES.values()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tier", def.getTier());
            List<String> recipeTypes = new ArrayList<>();
            for (GTRecipeType type : def.getRecipeTypes()) {
                if (type != null) recipeTypes.add(type.registryName.toString());
            }
            data.put("recipe_types", recipeTypes);
            boolean multiblock = def instanceof MultiblockMachineDefinition;
            data.put("multiblock", multiblock);
            if (multiblock)
                data.put("generator", ((MultiblockMachineDefinition) def).isGenerator());
            data.put("always_try_modify_recipe", def.isAlwaysTryModifyRecipe());
            machines.put(def.getId().toString(), data);
        }
        out.put("machines", machines);
        return out;
    }

    private static Map<String, Integer> capMap(Map<RecipeCapability<?>, Integer> map) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            result.put(entry.getKey().name, entry.getValue());
        }
        return result;
    }
}
