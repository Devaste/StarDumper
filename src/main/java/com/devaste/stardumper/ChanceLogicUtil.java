package com.devaste.stardumper;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;



public final class ChanceLogicUtil {

    // Private constructor to prevent instantiation
    private ChanceLogicUtil() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Returns a map of capability name to chance logic string for chanced entries in the given list.
     * Only adds entries where at least one content has chancePercent < 100.0f.
     */
    public static Map<String, String> extractChanceLogics(
            GTRecipe gtRecipe,
            Map<RecipeCapability<?>, List<Content>> contentMap,
            IO io,
            boolean isTick
    ) {
        Map<String, String> result = new LinkedHashMap<>();

        for (var entry : contentMap.entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            List<Content> contents = entry.getValue();

            boolean isChanced = contents.stream().anyMatch(content -> {
                float chancePercent = (content.maxChance == 0)
                        ? 100.0f
                        : (100.0f * content.chance / content.maxChance);
                return chancePercent < 100.0f;
            });

            if (isChanced) {
                ChanceLogic logic = gtRecipe.getChanceLogicForCapability(cap, io, isTick);
                String logicName = GTRegistries.CHANCE_LOGICS.getKey(logic);
                result.put(cap.getClass().getSimpleName(), logicName != null ? logicName : logic.toString());
            }
        }

        return result;
    }
}
