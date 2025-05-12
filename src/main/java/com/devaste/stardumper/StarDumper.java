package com.devaste.stardumper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
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
            if (recipe.getClass().getName().equals("com.gregtechceu.gtceu.api.recipe.GTRecipe")) {
                try {
                    Class<?> gtRecipeClass = Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipe");
                    Object gtRecipeObj = gtRecipeClass.cast(recipe);

                    Map<String, Object> recipeData = new HashMap<>();
                    recipeData.put("id", recipe.getId().toString());

                    // Extracting inputs
                    Field inputsField = gtRecipeClass.getField("inputs");
                    Map<?, ?> inputsMap = (Map<?, ?>) inputsField.get(gtRecipeObj);
                    List<String> inputList = new ArrayList<>();
                    for (Map.Entry<?, ?> entry : inputsMap.entrySet()) {
                        List<?> contentList = (List<?>) entry.getValue();
                        for (Object content : contentList) {
                            inputList.add(getContentDetails(content));
                        }
                    }
                    recipeData.put("inputs", inputList);

                    // Extracting outputs
                    Field outputsField = gtRecipeClass.getField("outputs");
                    Map<?, ?> outputsMap = (Map<?, ?>) outputsField.get(gtRecipeObj);
                    List<String> outputList = new ArrayList<>();
                    for (Map.Entry<?, ?> entry : outputsMap.entrySet()) {
                        List<?> contentList = (List<?>) entry.getValue();
                        for (Object content : contentList) {
                            outputList.add(getContentDetails(content));
                        }
                    }
                    recipeData.put("outputs", outputList);

                    // Extracting duration
                    Field durationField = gtRecipeClass.getField("duration");
                    int duration = durationField.getInt(gtRecipeObj);
                    recipeData.put("duration", duration);

                    // Extracting energy consumption
                    Class<?> recipeHelperClass = Class.forName("com.gregtechceu.gtceu.api.recipe.RecipeHelper");
                    Method getInputEUtMethod = recipeHelperClass.getMethod("getInputEUt", gtRecipeClass);
                    Method getOutputEUtMethod = recipeHelperClass.getMethod("getOutputEUt", gtRecipeClass);
                    long inputEUt = (long) getInputEUtMethod.invoke(null, gtRecipeObj);
                    long outputEUt = (long) getOutputEUtMethod.invoke(null, gtRecipeObj);
                    recipeData.put("input_EUt", inputEUt);
                    recipeData.put("output_EUt", outputEUt);

                    // Extracting recipe type
                    Field recipeTypeField = gtRecipeClass.getField("recipeType");
                    Object recipeType = recipeTypeField.get(gtRecipeObj);
                    recipeData.put("recipeType", recipeType.toString()); // or process it further if needed

                    // Extracting overclocking level
                    Field ocLevelField = gtRecipeClass.getField("ocLevel");
                    int ocLevel = ocLevelField.getInt(gtRecipeObj);
                    recipeData.put("ocLevel", ocLevel);

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

    // Helper method to extract content details
    private String getContentDetails(Object content) {
        StringBuilder sb = new StringBuilder();
        try {
            Field contentField = content.getClass().getDeclaredField("content");
            contentField.setAccessible(true);
            Object actualContent = contentField.get(content);

            if (actualContent == null) return "Content not loaded";

            String className = actualContent.getClass().getName();

            if (className.equals("com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient")) {
                int amount = (int) actualContent.getClass().getMethod("getAmount").invoke(actualContent);
                Object[] values = (Object[]) actualContent.getClass().getDeclaredField("values").get(actualContent);

                sb.append("FluidIngredient{amount=").append(amount).append(", fluids=[");
                for (Object value : values) {
                    Collection<?> fluids = (Collection<?>) value.getClass().getMethod("getFluids").invoke(value);
                    for (Object fluid : fluids) {
                        String fluidKey = "unknown";
                        boolean found = false;
                        String[] registryClasses = {
                                "net.minecraftforge.registries.ForgeRegistries",
                                "net.minecraft.core.registries.BuiltInRegistries"
                        };
                        String[] fieldNames = {"FLUIDS", "FLUID"};
                        for (String registryClass : registryClasses) {
                            for (String fieldName : fieldNames) {
                                try {
                                    Class<?> regClazz = Class.forName(registryClass);
                                    Field field = regClazz.getField(fieldName);
                                    Object registry = field.get(null);
                                    try {
                                        fluidKey = regClazz.getMethod("getKey", Object.class).invoke(registry, fluid).toString();
                                        found = true;
                                        break;
                                    } catch (NoSuchMethodException nsme) {
                                        fluidKey = registry.getClass().getMethod("getKey", Object.class).invoke(registry, fluid).toString();
                                        found = true;
                                        break;
                                    }
                                } catch (Throwable ignored) {}
                            }
                            if (found) break;
                        }
                        if (!found) {
                            try {
                                fluidKey = fluid.getClass().getMethod("toString").invoke(fluid).toString();
                            } catch (Throwable t) {
                                fluidKey = "unknown";
                            }
                        }
                        sb.append(fluidKey).append(", ");
                    }
                }
                sb.append("]}");
            } else if (className.equals("com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient")) {
                int amount = (int) actualContent.getClass().getMethod("getAmount").invoke(actualContent);
                sb.append("SizedIngredient{amount=").append(amount).append(", items=[");
                List<String> itemKeys = new ArrayList<>();
                extractItemKeysRecursive(actualContent, itemKeys, 0);
                for (int i = 0; i < itemKeys.size(); i++) {
                    sb.append(itemKeys.get(i));
                    if (i < itemKeys.size() - 1) sb.append(", ");
                }
                sb.append("]}");
            } else {
                try {
                    java.lang.reflect.Method getItemsMethod = actualContent.getClass().getDeclaredMethod("getItems");
                    getItemsMethod.setAccessible(true);
                    Object[] itemStacks = (Object[]) getItemsMethod.invoke(actualContent);
                    sb.append("Ingredient{items=[");
                    for (Object itemStack : itemStacks) {
                        Object item = itemStack.getClass().getMethod("getItem").invoke(itemStack);
                        int count = (int) itemStack.getClass().getMethod("getCount").invoke(itemStack);
                        String itemKey = resolveItemKey(item);
                        sb.append(itemKey).append(" x").append(count).append(", ");
                    }
                    sb.append("]}");
                } catch (Throwable t) {
                    try {
                        Object json = actualContent.getClass().getMethod("toJson").invoke(actualContent);
                        sb.append("json:").append(json.toString());
                    } catch (Throwable t2) {
                        sb.append("error:").append(t2.getClass().getName());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error while processing content: ", e);
            return content.toString();
        }
        return sb.toString();
    }

    // Recursively extract item registry keys from SizedIngredient or Ingredient
    private void extractItemKeysRecursive(Object ingredient, List<String> itemKeys, int depth) {
        if (depth > 10 || ingredient == null) return;
        try {
            // Try getItems() via public or declared method on this class or superclasses
            Class<?> clazz = ingredient.getClass();
            java.lang.reflect.Method getItemsMethod = null;
            while (clazz != null) {
                try {
                    getItemsMethod = clazz.getMethod("getItems");
                    break;
                } catch (NoSuchMethodException e) {
                    try {
                        getItemsMethod = clazz.getDeclaredMethod("getItems");
                        getItemsMethod.setAccessible(true);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
                clazz = clazz.getSuperclass();
            }
            if (getItemsMethod != null) {
                Object[] itemStacks = (Object[]) getItemsMethod.invoke(ingredient);
                if (itemStacks.length > 0) {
                    for (Object itemStack : itemStacks) {
                        Object item = itemStack.getClass().getMethod("getItem").invoke(itemStack);
                        String itemKey = resolveItemKey(item);
                        if (!itemKeys.contains(itemKey)) itemKeys.add(itemKey);
                    }
                    return;
                }
            }
        } catch (Throwable t) {
            // ignore, try fallback below
        }
        // Try to unwrap inner if present
        try {
            Field innerField = ingredient.getClass().getDeclaredField("inner");
            innerField.setAccessible(true);
            Object inner = innerField.get(ingredient);
            extractItemKeysRecursive(inner, itemKeys, depth + 1);
        } catch (Throwable t2) {
            // ignore
        }
        // Try all declared fields that are arrays (especially obfuscated ones)
        try {
            java.lang.reflect.Field[] fields = ingredient.getClass().getDeclaredFields();
            for (java.lang.reflect.Field f : fields) {
                f.setAccessible(true);
                Object value = f.get(ingredient);
                if (value instanceof Object[]) {
                    Object[] arr = (Object[]) value;
                    for (Object obj : arr) {
                        if (obj != null) {
                            try {
                                Object item = obj.getClass().getMethod("getItem").invoke(obj);
                                String itemKey = resolveItemKey(item);
                                if (!itemKeys.contains(itemKey)) itemKeys.add(itemKey);
                            } catch (Throwable t) {
                                // not an ItemStack, skip
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        // Try getValues() for vanilla Ingredient
        try {
            java.lang.reflect.Method getValuesMethod = ingredient.getClass().getDeclaredMethod("getValues");
            getValuesMethod.setAccessible(true);
            Object[] values = (Object[]) getValuesMethod.invoke(ingredient);
            for (Object value : values) {
                java.lang.reflect.Method createItemStackMethod = value.getClass().getDeclaredMethod("createItemStack");
                createItemStackMethod.setAccessible(true);
                Object itemStack = createItemStackMethod.invoke(value);
                Object item = itemStack.getClass().getMethod("getItem").invoke(itemStack);
                String itemKey = resolveItemKey(item);
                if (!itemKeys.contains(itemKey)) itemKeys.add(itemKey);
            }
        } catch (Throwable t) {
            // ignore
        }
        // Try CraftTweaker getItemStacks/getValues methods
        try {
            for (String methodName : new String[]{"crafttweaker$getItemStacks", "crafttweaker$getValues"}) {
                try {
                    java.lang.reflect.Method m = ingredient.getClass().getDeclaredMethod(methodName);
                    m.setAccessible(true);
                    Object result = m.invoke(ingredient);
                    if (result instanceof Object[]) {
                        Object[] itemStacks = (Object[]) result;
                        for (Object itemStack : itemStacks) {
                            Object item = itemStack.getClass().getMethod("getItem").invoke(itemStack);
                            String itemKey = resolveItemKey(item);
                            if (!itemKeys.contains(itemKey)) itemKeys.add(itemKey);
                        }
                    }
                } catch (Throwable t) {
                    // ignore
                }
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    // Helper to resolve item registry key
    private String resolveItemKey(Object item) {
        try {
            Class<?> forgeRegistries = Class.forName("net.minecraftforge.registries.ForgeRegistries");
            Object itemRegistry = forgeRegistries.getField("ITEMS").get(null);
            Object key = forgeRegistries.getMethod("getKey", Object.class).invoke(itemRegistry, item);
            if (key != null) {
                return key.toString();
            } else {
                return item.getClass().getMethod("toString").invoke(item).toString();
            }
        } catch (Throwable t) {
            try {
                return item.getClass().getMethod("toString").invoke(item).toString();
            } catch (Throwable t2) {
                return "unknown";
            }
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
