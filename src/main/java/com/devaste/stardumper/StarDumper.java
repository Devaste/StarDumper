package com.devaste.stardumper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

@Mod(StarDumper.MODID)
public class StarDumper {
    public static final String MODID = "stardumper";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public StarDumper(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Star Dumper Initialized");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Starting to gather recipes");
        gatherRecipes(event);
    }

    private void gatherRecipes(ServerStartingEvent event) {
        RecipeManager recipeManager = event.getServer().getRecipeManager();
        RegistryAccess registryAccess = event.getServer().registryAccess();
        Collection<Recipe<?>> recipes = recipeManager.getRecipes();

        List<Map<String, Object>> gtRecipes = new ArrayList<>();

        for (Recipe<?> recipe : recipes) {
            if (recipe.getClass().getName().equals("com.gregtechceu.gtceu.api.recipe.GTRecipe")) {
                try {
                    // Use reflection to get the GTRecipe class
                    Class<?> gtRecipeClass = Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipe");
                    Object gtRecipeObj = gtRecipeClass.cast(recipe);

                    Map<String, Object> recipeData = new HashMap<>();
                    recipeData.put("id", recipe.getId().toString());
                    recipeData.put("type", recipe.getType().toString());

                    // Extracting inputs using reflection
                    Field inputsField = gtRecipeClass.getField("inputs");
                    Map<?, ?> inputsMap = (Map<?, ?>) inputsField.get(gtRecipeObj);
                    List<String> inputList = new ArrayList<>();
                    for (Map.Entry<?, ?> entry : inputsMap.entrySet()) {
                        List<?> contentList = (List<?>) entry.getValue();
                        for (Object content : contentList) {
                            inputList.add(content.toString());
                        }
                    }
                    recipeData.put("inputs", inputList);

                    // Extracting outputs using reflection
                    Field outputsField = gtRecipeClass.getField("outputs");
                    Map<?, ?> outputsMap = (Map<?, ?>) outputsField.get(gtRecipeObj);
                    List<String> outputList = new ArrayList<>();
                    for (Map.Entry<?, ?> entry : outputsMap.entrySet()) {
                        List<?> contentList = (List<?>) entry.getValue();
                        for (Object content : contentList) {
                            outputList.add(content.toString());
                        }
                    }
                    recipeData.put("outputs", outputList);

                    // Extracting duration using reflection
                    Field durationField = gtRecipeClass.getField("duration");
                    int duration = durationField.getInt(gtRecipeObj);
                    recipeData.put("duration", duration);

                    // Extracting energy consumption using reflection
                    Class<?> recipeHelperClass = Class.forName("com.gregtechceu.gtceu.api.recipe.RecipeHelper");
                    Method getInputEUtMethod = recipeHelperClass.getMethod("getInputEUt", gtRecipeClass);
                    Method getOutputEUtMethod = recipeHelperClass.getMethod("getOutputEUt", gtRecipeClass);
                    long inputEUt = (long) getInputEUtMethod.invoke(null, gtRecipeObj);
                    long outputEUt = (long) getOutputEUtMethod.invoke(null, gtRecipeObj);
                    recipeData.put("input_EUt", inputEUt);
                    recipeData.put("output_EUt", outputEUt);

                    gtRecipes.add(recipeData);

                } catch (Exception e) {
                    LOGGER.error("Failed to process GTCEu recipe: " + recipe.getId(), e);
                }
            }
        }

        if (gtRecipes.isEmpty()) {
            LOGGER.info("No GTCEu recipes found.");
            return;
        }

        File outputDir = new File("config/star_recipe_dumps");
        if (!outputDir.exists()) outputDir.mkdirs();

        File outputFile = new File(outputDir, "gtceu_recipes.json");

        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(gtRecipes, writer);
            LOGGER.info("Saved GTCEu recipes to " + outputFile.getPath());
        } catch (IOException e) {
            LOGGER.error("Failed to write GTCEu recipes", e);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup() {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
