package com.devaste.stardumper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IRangedIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Exports every field of a {@link GTRecipe} as stored in the original implementation:
 * all four content maps (with raw chance / maxChance / tierChanceBoost), all four chance-logic maps,
 * conditions, the recipe data tag, category, voltage and amperage separately.
 * Contents keep the exact JSON the GT serializer produces and add resolved ids for tags.
 */
public class FullRecipeExtractor {

    private FullRecipeExtractor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void addFullData(GTRecipe recipe, Map<String, Object> out) {
        out.put("recipe_type", recipe.recipeType.registryName.toString());
        if (recipe.recipeCategory != null)
            out.put("category", recipe.recipeCategory.registryKey.toString());

        out.put("energy", energy(recipe));
        out.put("voltage_tier", RecipeHelper.getRecipeEUtTier(recipe));

        Map<String, Object> contents = new LinkedHashMap<>();
        putContents(contents, "inputs", recipe.inputs, IO.IN);
        putContents(contents, "outputs", recipe.outputs, IO.OUT);
        putContents(contents, "tick_inputs", recipe.tickInputs, IO.IN);
        putContents(contents, "tick_outputs", recipe.tickOutputs, IO.OUT);
        out.put("contents", contents);

        Map<String, Object> logics = new LinkedHashMap<>();
        putLogics(logics, "inputs", recipe, recipe.inputs, IO.IN, false);
        putLogics(logics, "outputs", recipe, recipe.outputs, IO.OUT, false);
        putLogics(logics, "tick_inputs", recipe, recipe.tickInputs, IO.IN, true);
        putLogics(logics, "tick_outputs", recipe, recipe.tickOutputs, IO.OUT, true);
        out.put("chance_logics", logics);

        Integer circuit = findCircuit(recipe);
        if (circuit != null)
            out.put("circuit", circuit);

        List<Object> conditions = new ArrayList<>();
        for (RecipeCondition<?> condition : recipe.conditions) {
            conditions.add(condition(condition));
        }
        if (!conditions.isEmpty())
            out.put("conditions", conditions);

        if (recipe.data != null && !recipe.data.isEmpty()) {
            out.put("data", nbtToJson(recipe.data));
            out.put("data_snbt", recipe.data.toString());
        }

        if (recipe.ingredientActions != null && !recipe.ingredientActions.isEmpty())
            out.put("ingredient_actions", recipe.ingredientActions.size());

        out.put("parallels", recipe.parallels);
        out.put("ocLevel", recipe.ocLevel);
        out.put("baseOcLevel", recipe.baseOcLevel);
    }

    private static Map<String, Object> energy(GTRecipe recipe) {
        Map<String, Object> energy = new LinkedHashMap<>();
        energy.put("input", energyStack(recipe.getInputEUt()));
        energy.put("output", energyStack(recipe.getOutputEUt()));
        return energy;
    }

    private static Map<String, Object> energyStack(EnergyStack stack) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("voltage", stack.voltage());
        map.put("amperage", stack.amperage());
        map.put("total", stack.getTotalEU());
        return map;
    }

    private static void putContents(Map<String, Object> out, String key,
                                    Map<RecipeCapability<?>, List<Content>> map, IO io) {
        Map<String, Object> byCap = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            List<Object> list = new ArrayList<>();
            for (Content content : entry.getValue()) {
                list.add(content(cap, content, io));
            }
            if (!list.isEmpty())
                byCap.put(cap.name, list);
        }
        if (!byCap.isEmpty())
            out.put(key, byCap);
    }

    private static Map<String, Object> content(RecipeCapability<?> cap, Content content, IO io) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", rawContent(cap, content));
        map.put("chance", content.chance);
        map.put("maxChance", content.maxChance);
        map.put("tierChanceBoost", content.tierChanceBoost);
        if (content.maxChance > 0) {
            map.put("chance_percent", 100.0 * content.chance / content.maxChance);
            if (content.tierChanceBoost != 0)
                map.put("boost_percent_per_tier", 100.0 * content.tierChanceBoost / content.maxChance);
        }
        if (io == IO.IN)
            map.put("consumed", content.chance > 0);

        Object inner = content.getContent();
        if (inner instanceof Ingredient ingredient) {
            describeItem(ingredient, map);
        } else if (inner instanceof FluidIngredient fluid) {
            describeFluid(fluid, map);
        }
        return map;
    }

    private static JsonElement rawContent(RecipeCapability<?> cap, Content content) {
        try {
            return cap.serializer.toJsonContent(content).getAsJsonObject().get("content");
        } catch (Exception e) {
            return new JsonPrimitive(String.valueOf(content.getContent()));
        }
    }

    private static void describeItem(Ingredient ingredient, Map<String, Object> map) {
        IntProvider range = null;
        int amount = 1;
        Ingredient inner = ingredient;
        // unwrap Sized / IntProvider layers without rolling ranged amounts
        while (true) {
            if (inner instanceof SizedIngredient sized) {
                amount = sized.getAmount();
                inner = sized.getInner();
            } else if (inner instanceof IntProviderIngredient provider) {
                range = provider.getCountProvider();
                inner = provider.getInner();
            } else {
                break;
            }
        }
        if (range != null) {
            map.put("count_min", range.getMinValue());
            map.put("count_max", range.getMaxValue());
        } else {
            map.put("count", amount);
        }
        if (inner instanceof IntCircuitIngredient circuit) {
            ItemStack[] stacks = circuit.getItems();
            if (stacks.length > 0)
                map.put("circuit", IntCircuitBehaviour.getCircuitConfiguration(stacks[0]));
        }
        Set<String> ids = new LinkedHashSet<>();
        for (ItemStack stack : inner.getItems()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null) ids.add(id.toString());
        }
        map.put("items", new ArrayList<>(ids));
    }

    private static void describeFluid(FluidIngredient fluid, Map<String, Object> map) {
        if (fluid instanceof IRangedIngredient ranged) {
            map.put("amount_min", ranged.getCountProvider().getMinValue());
            map.put("amount_max", ranged.getCountProvider().getMaxValue());
        } else {
            map.put("amount", fluid.getAmount());
        }
        FluidIngredient base = fluid instanceof IntProviderFluidIngredient provider ? provider.getInner() : fluid;
        Set<String> ids = new LinkedHashSet<>();
        for (FluidIngredient.Value value : base.values) {
            for (Fluid f : value.getFluids()) {
                ResourceLocation id = ForgeRegistries.FLUIDS.getKey(f);
                if (id != null) ids.add(id.toString());
            }
        }
        map.put("fluids", new ArrayList<>(ids));
        if (base.getNbt() != null)
            map.put("nbt", base.getNbt().toString());
    }

    private static void putLogics(Map<String, Object> out, String key, GTRecipe recipe,
                                  Map<RecipeCapability<?>, List<Content>> map, IO io, boolean tick) {
        Map<String, String> byCap = new LinkedHashMap<>();
        for (RecipeCapability<?> cap : map.keySet()) {
            ChanceLogic logic = recipe.getChanceLogicForCapability(cap, io, tick);
            String name = GTRegistries.CHANCE_LOGICS.getKey(logic);
            byCap.put(cap.name, name != null ? name : logic.toString());
        }
        if (!byCap.isEmpty())
            out.put(key, byCap);
    }

    private static Integer findCircuit(GTRecipe recipe) {
        for (List<Content> list : recipe.inputs.values()) {
            for (Content content : list) {
                Object inner = content.getContent();
                while (inner instanceof SizedIngredient sized) inner = sized.getInner();
                if (inner instanceof IntCircuitIngredient circuit) {
                    ItemStack[] stacks = circuit.getItems();
                    if (stacks.length > 0) return IntCircuitBehaviour.getCircuitConfiguration(stacks[0]);
                }
            }
        }
        return null;
    }

    private static Object condition(RecipeCondition<?> condition) {
        JsonObject json;
        try {
            json = condition.serialize();
        } catch (Exception e) {
            json = new JsonObject();
            json.addProperty("type", String.valueOf(condition.getType()));
        }
        try {
            json.addProperty("tooltip", condition.getTooltips().getString());
        } catch (Exception ignored) {
            // some tooltips need client-side state
        }
        json.addProperty("reverse", condition.isReverse());
        return json;
    }

    private static JsonElement nbtToJson(CompoundTag tag) {
        return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
    }
}
