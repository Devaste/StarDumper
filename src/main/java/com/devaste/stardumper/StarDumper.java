package com.devaste.stardumper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Mod(StarDumper.MODID)
public class StarDumper {
    public static final String MODID = "stardumper";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

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
        Collection<Recipe<?>> recipes = recipeManager.getRecipes();

        List<Map<String, Object>> gtRecipes = new ArrayList<>();

        for (Recipe<?> recipe : recipes) {
            if (recipe instanceof GTRecipe gtRecipe) {
                try {
                    Map<String, Object> recipeData = new HashMap<>();
                    recipeData.put("id", gtRecipe.getId().toString());
                    recipeData.put("recipeType", gtRecipe.getType().toString());
                    recipeData.put("input_EUt", RecipeHelper.getInputEUt(gtRecipe));
                    recipeData.put("output_EUt", RecipeHelper.getOutputEUt(gtRecipe));
                    recipeData.put("duration", gtRecipe.duration);
                    recipeData.put("minTier", RecipeHelper.getRecipeEUtTier(gtRecipe));

                    // Defensive extraction for inputs (items + fluids)
                    List<String> inputList = new ArrayList<>();
                    inputList.addAll(
                        gtRecipe.getInputContents(ItemRecipeCapability.CAP).stream()
                            .map(content -> ItemRecipeCapability.CAP.of(content.getContent()))
                            .filter(ingredient -> ingredient.getItems().length > 0)
                            .map(ingredient -> formatItemStack(ingredient.getItems()[0]))
                            .collect(Collectors.toList())
                    );
                    inputList.addAll(
                        gtRecipe.getInputContents(FluidRecipeCapability.CAP).stream()
                            .map(content -> FluidRecipeCapability.CAP.of(content.getContent()))
                            .filter(ingredient -> ingredient.getStacks().length > 0)
                            .map(ingredient -> formatFluidStack(ingredient.getStacks()[0]))
                            .collect(Collectors.toList())
                    );
                    recipeData.put("inputs", inputList);

                    // Defensive extraction for outputs (items + fluids)
                    List<String> outputList = new ArrayList<>();
                    outputList.addAll(
                        gtRecipe.getOutputContents(ItemRecipeCapability.CAP).stream()
                            .map(content -> ItemRecipeCapability.CAP.of(content.getContent()))
                            .filter(ingredient -> ingredient.getItems().length > 0)
                            .map(ingredient -> formatItemStack(ingredient.getItems()[0]))
                            .collect(Collectors.toList())
                    );
                    outputList.addAll(
                        gtRecipe.getOutputContents(FluidRecipeCapability.CAP).stream()
                            .map(content -> FluidRecipeCapability.CAP.of(content.getContent()))
                            .filter(ingredient -> ingredient.getStacks().length > 0)
                            .map(ingredient -> formatFluidStack(ingredient.getStacks()[0]))
                            .collect(Collectors.toList())
                    );
                    recipeData.put("outputs", outputList);

                    gtRecipes.add(recipeData);
                } catch (Exception e) {
                    LOGGER.error("Failed to process GTCEu recipe: " + gtRecipe.getId(), e);
                }
            }
        }

        if (gtRecipes.isEmpty()) {
            LOGGER.info("No GTCEu recipes found.");
            return;
        }

        File outputDir = new File("config/star_recipe_dumps");
        if (!outputDir.exists()) outputDir.mkdirs();

        File outputFile = new File(outputDir, "gtceu_new_recipes.json");

        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(gtRecipes, writer);
            LOGGER.info("Saved GTCEu recipes to " + outputFile.getPath());
        } catch (IOException e) {
            LOGGER.error("Failed to write GTCEu recipes", e);
        }
    }

    // Format ItemStack as SizedIngredient{amount=..., items=[mod:item]}
    private String formatItemStack(ItemStack stack) {
        ResourceLocation reg = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String regName = reg != null ? reg.toString() : "minecraft:air";
        return String.format("SizedIngredient{amount=%d, items=[%s]}", stack.getCount(), regName);
    }

    // Format FluidStack as FluidIngredient{amount=..., fluids=[mod:fluid]}
    private String formatFluidStack(FluidStack stack) {
        ResourceLocation reg = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
        String regName = reg != null ? reg.toString() : "minecraft:empty";
        return String.format("FluidIngredient{amount=%d, fluids=[%s]}", stack.getAmount(), regName);
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
