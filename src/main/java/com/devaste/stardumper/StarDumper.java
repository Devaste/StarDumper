package com.devaste.stardumper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
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
        LOGGER.info("HELLO FROM COMMON SETUP");
        // gatherRecipes больше здесь не вызываем — делаем это на старте сервера
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
        gatherRecipes(event);
    }

    private void gatherRecipes(ServerStartingEvent event) {
        RecipeManager recipeManager = event.getServer().getRecipeManager();
        RegistryAccess registryAccess = event.getServer().registryAccess();
        Collection<Recipe<?>> recipes = recipeManager.getRecipes();

        Map<String, List<Map<String, Object>>> recipesByType = new HashMap<>();

        for (Recipe<?> recipe : recipes) {
            String recipeType = recipe.getType().toString();

            if (recipeType.startsWith("gtceu:")) {
                String machineType = recipeType.split(":")[1];
                Map<String, Object> recipeData = extractRecipeData(recipe, registryAccess);

                recipesByType
                        .computeIfAbsent(machineType, k -> new ArrayList<>())
                        .add(recipeData);
            }
        }

        File outputDir = new File("config/star_recipe_dumps");
        if (!outputDir.exists()) outputDir.mkdirs();

        for (Map.Entry<String, List<Map<String, Object>>> entry : recipesByType.entrySet()) {
            String machineType = entry.getKey();
            List<Map<String, Object>> machineRecipes = entry.getValue();

            File outputFile = new File(outputDir, machineType + ".json");

            try (FileWriter writer = new FileWriter(outputFile)) {
                GSON.toJson(machineRecipes, writer);
                LOGGER.info("Saved recipes for " + machineType + " to " + outputFile.getPath());
            } catch (IOException e) {
                LOGGER.error("Failed to write recipes for " + machineType, e);
            }
        }
    }

    private Map<String, Object> extractRecipeData(Recipe<?> recipe, RegistryAccess registryAccess) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", recipe.getId().toString());
        data.put("inputs", recipe.getIngredients().toString());
        data.put("outputs", recipe.getResultItem(registryAccess).toString());
        data.put("duration", 360);
        data.put("EUt", 120);
        return data;
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
