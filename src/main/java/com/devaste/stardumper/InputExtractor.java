package com.devaste.stardumper;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.util.*;

public class InputExtractor {

    private InputExtractor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static List<String> extractInputs(GTRecipe recipe) {
        List<String> inputs = new ArrayList<>();

        inputs.addAll(recipe.getInputContents(ItemRecipeCapability.CAP).stream()
            .map(content -> {
                var cap = ItemRecipeCapability.CAP.of(content.getContent());
                if (cap.getItems().length == 0) return null;
                String itemStr = FormatUtils.formatItemStack(cap.getItems()[0]);
                float chancePercent = 100f * content.chance / content.maxChance;
                if (chancePercent < 100.0f)
                    itemStr += String.format(", chance=[%.2f]", chancePercent);
                return itemStr;
            })
            .filter(Objects::nonNull)
            .toList()
        );

        inputs.addAll(recipe.getInputContents(FluidRecipeCapability.CAP).stream()
            .map(content -> FluidRecipeCapability.CAP.of(content.getContent()))
            .filter(ingredient -> ingredient.getStacks().length > 0)
            .map(ingredient -> FormatUtils.formatFluidStack(ingredient.getStacks()[0]))
            .toList()
        );

        return inputs;
    }
}
