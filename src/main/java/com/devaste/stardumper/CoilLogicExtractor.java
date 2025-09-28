package com.devaste.stardumper;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.util.*;


public class CoilLogicExtractor {

    private CoilLogicExtractor() {
    throw new UnsupportedOperationException("Utility class");
    }

    public static Map<String, Object> extractCoilLogic(GTRecipe recipe) {
        Map<String, Object> result = new LinkedHashMap<>();

        String machineType = recipe.getType().toString();
        if (List.of(
            "gtceu:electric_blast_furnace",
            "gtceu:mega_blast_furnace",
            "gtceu:alloy_blast_smelter"
        ).contains(machineType)) {
            Integer minTemp = null;
            if (recipe.data.contains("abs_temp")) minTemp = recipe.data.getInt("abs_temp");
            else if (recipe.data.contains("ebf_temp")) minTemp = recipe.data.getInt("ebf_temp");
            else if (recipe.data.contains("blast_temperature")) minTemp = recipe.data.getInt("blast_temperature");
            else if (recipe.data.contains("requiredTemperature")) minTemp = recipe.data.getInt("requiredTemperature");

            if (minTemp != null && minTemp > 0) {
                ICoilType coil = ICoilType.getMinRequiredType(minTemp);
                if (coil != null) {
                    result.put("min tier", coil.getTier());
                    result.put("min temp", minTemp);
                }
            }
        }

        return result;
    }
}
