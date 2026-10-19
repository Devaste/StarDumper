package com.devaste.stardumper;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

import java.util.*;

public class RecipeExportManager {

    private RecipeExportManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void gatherRecipes(ServerStartingEvent event) {
        RecipeManager recipeManager = event.getServer().getRecipeManager();
        Collection<Recipe<?>> recipes = recipeManager.getRecipes();

        Map<String, Map<String, Map<String, Object>>> groupedRecipes = new HashMap<>();

        for (Recipe<?> recipe : recipes) {
            if (recipe instanceof GTRecipe gtRecipe) {
                try {
                    String type = gtRecipe.getType().toString();
                    String id = gtRecipe.getId().toString();
                    Map<String, Object> recipeData = RecipeProcessor.processRecipe(gtRecipe);
                    groupedRecipes.computeIfAbsent(type, k -> new HashMap<>()).put(id, recipeData);
                } catch (Exception e) {
                    LOGGER.error("Failed to process GTCEu recipe: " + recipe.getId(), e);
                }
            }
        }

        if (groupedRecipes.isEmpty()) {
            LOGGER.info("No GTCEu recipes found.");
            return;
        }

        RecipeWriter.writeRecipesToFile(groupedRecipes);

        try {
            RecipeWriter.writeJson("gtceu_machines.json", MachineExtractor.extract());
        } catch (Exception e) {
            LOGGER.error("Failed to export GTCEu machines", e);
        }

        try {
            RecipeWriter.writeJson("gtceu_layouts.json", LayoutExtractor.extract());
        } catch (Exception e) {
            LOGGER.error("Failed to export layouts", e);
        }

        try {
            RecipeWriter.writeJson("gtceu_names.json", NameExtractor.extract());
        } catch (Exception e) {
            LOGGER.error("Failed to export names", e);
        }
    }
}
