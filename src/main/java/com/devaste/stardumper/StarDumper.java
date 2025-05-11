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
            // Проверяем принадлежность класса через getName()
            if (recipe.getClass().getName().equals("com.gregtechceu.gtceu.api.recipe.GTRecipe")) {
                Map<String, Object> recipeData = new HashMap<>();
                recipeData.put("id", recipe.getId().toString());
                recipeData.put("type", recipe.getType().toString());

                gtRecipes.add(recipeData);
            }
        }

        // Если ничего не нашли — логируем и выходим
        if (gtRecipes.isEmpty()) {
            LOGGER.info("No GTCEu recipes found.");
            return;
        }

        // Папка для дампов
        File outputDir = new File("config/star_recipe_dumps");
        if (!outputDir.exists()) outputDir.mkdirs();

        // Выводим всё в один файл gtceu_recipes.json
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
