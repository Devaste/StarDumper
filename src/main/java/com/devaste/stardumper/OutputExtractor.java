package com.devaste.stardumper;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.util.*;

public class OutputExtractor {

    private OutputExtractor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static List<String> extractOutputs(GTRecipe recipe) {
        List<String> outputs = new ArrayList<>();

        // Item outputs
        outputs.addAll(recipe.getOutputContents(ItemRecipeCapability.CAP).stream()
            .map(content -> {
                var cap = ItemRecipeCapability.CAP.of(content.getContent());
                if (cap.getItems().length == 0) return null;

                String itemStr = FormatUtils.formatItemStack(cap.getItems()[0]);

                float chancePercent = 100f * content.chance / content.maxChance;
                if (chancePercent < 100.0f) {
                    itemStr += String.format(", chance=[%.2f]", chancePercent);
                }

                if (content.tierChanceBoost > 0) {
                    float boostPercent = content.tierChanceBoost / 100f;
                    itemStr += String.format(", tierBoost=[%.2f]", boostPercent);
                }

                return itemStr;
            })
            .filter(Objects::nonNull)
            .toList()
        );

        // Fluid outputs
        outputs.addAll(recipe.getOutputContents(FluidRecipeCapability.CAP).stream()
            .map(content -> FluidRecipeCapability.CAP.of(content.getContent()))
            .filter(ingredient -> ingredient.getStacks().length > 0)
            .map(ingredient -> FormatUtils.formatFluidStack(ingredient.getStacks()[0]))
            .toList()
        );

        return outputs;
    }
}
