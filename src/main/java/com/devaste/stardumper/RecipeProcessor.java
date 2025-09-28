package com.devaste.stardumper;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import java.util.*;


public class RecipeProcessor {

    private RecipeProcessor() {
        throw new  UnsupportedOperationException("Utility class");
    }

    public static Map<String, Object> processRecipe(GTRecipe gtRecipe) {
        Map<String, Object> recipeData = new LinkedHashMap<>();

        recipeData.put("inputs", InputExtractor.extractInputs(gtRecipe));
        recipeData.put("outputs", OutputExtractor.extractOutputs(gtRecipe));

        Map<String, String> outputChanceLogics = ChanceLogicUtil.extractChanceLogics(gtRecipe, gtRecipe.outputs, IO.OUT, false);
        if (!outputChanceLogics.isEmpty())
            recipeData.put("output_chance_logics", outputChanceLogics);

        Map<String, String> inputChanceLogics = ChanceLogicUtil.extractChanceLogics(gtRecipe, gtRecipe.inputs, IO.IN, false);
        if (!inputChanceLogics.isEmpty())
            recipeData.put("input_chance_logics", inputChanceLogics);

        recipeData.put("duration", gtRecipe.duration);
        recipeData.put("minTier", RecipeHelper.getRecipeEUtTier(gtRecipe));
        recipeData.put("input_EUt", RecipeHelper.getInputEUt(gtRecipe));
        recipeData.put("output_EUt", RecipeHelper.getOutputEUt(gtRecipe));

        Map<String, Object> coilLogic = CoilLogicExtractor.extractCoilLogic(gtRecipe);
        if (!coilLogic.isEmpty())
            recipeData.put("coils", List.of(coilLogic));

        return recipeData;
    }
}
