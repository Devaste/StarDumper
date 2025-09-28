package com.devaste.stardumper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RecipeWriter {

    private RecipeWriter() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void writeRecipesToFile(Map<String, Map<String, Map<String, Object>>> groupedRecipes) {
        File outputDir = new File("config/star_recipe_dumps");
        if (!outputDir.exists()) outputDir.mkdirs();

        File outputFile = new File(outputDir, "gtceu_new_recipes.json");

        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(groupedRecipes, writer);
            LOGGER.info("Saved recipes to {}", outputFile.getPath());
        } catch (IOException e) {
            LOGGER.error("Failed to save GTCEu recipes", e);
        }
    }
}
