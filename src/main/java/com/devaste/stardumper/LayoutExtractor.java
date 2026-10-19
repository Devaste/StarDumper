package com.devaste.stardumper;

import com.gregtechceu.gtceu.api.block.MaterialPipeBlock;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ItemPipeProperties;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Exports what a factory layout planner needs: the physical shape of every multiblock
 * (from its JEI/preview shapes) and the transport limits of every GT cable and pipe block.
 * Single-block machines are always 1x1x1 and are not listed.
 */
public class LayoutExtractor {

    private static final Logger LOGGER = LogUtils.getLogger();

    private LayoutExtractor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Map<String, Object> extract() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("axes", "size = [x, y, z]: x = position within an aisle row, y = row (0 = bottom), z = aisle index; parts/controller pos use the same order; aisles lists each aisle bottom row first, aisles separated by an empty string");
        out.put("multiblocks", multiblocks());
        out.put("pipes", pipes());
        return out;
    }

    private static Map<String, Object> multiblocks() {
        Map<String, Object> result = new TreeMap<>();
        for (MachineDefinition def : GTRegistries.MACHINES.values()) {
            if (!(def instanceof MultiblockMachineDefinition multi)) continue;
            Map<String, Object> data = new LinkedHashMap<>();
            try {
                BlockPattern pattern = multi.getPatternFactory().get();
                data.put("pattern_dimensions", pattern.getDimensions());
                List<int[]> reps = new ArrayList<>();
                boolean variable = false;
                for (int[] r : pattern.aisleRepetitions) {
                    reps.add(r);
                    if (r[0] != r[1]) variable = true;
                }
                data.put("variable_length", variable);
                if (variable) data.put("aisle_repetitions", reps);
                data.put("allowed_parts", allowedParts(pattern));
            } catch (Throwable e) {
                data.put("pattern_error", e.toString());
            }
            List<Object> shapes = new ArrayList<>();
            try {
                for (MultiblockShapeInfo info : multi.getMatchingShapes()) {
                    shapes.add(shape(info.getBlocks(), def.getBlock()));
                }
            } catch (Throwable e) {
                data.put("shape_error", e.toString());
                LOGGER.warn("No shape for {}", def.getId(), e);
            }
            data.put("shapes", shapes);
            result.put(def.getId().toString(), data);
        }
        return result;
    }

    /**
     * For each machine part (hatch, bus, ME part...) that the pattern accepts, the number of pattern
     * positions where it may be placed and the tightest global limit on it, read from the predicates.
     */
    private static Map<String, Object> allowedParts(BlockPattern pattern) throws ReflectiveOperationException {
        Field field = BlockPattern.class.getDeclaredField("blockMatches");
        field.setAccessible(true);
        TraceabilityPredicate[][][] matches = (TraceabilityPredicate[][][]) field.get(pattern);
        Map<String, Integer> positions = new TreeMap<>();
        Map<String, Integer> maxCount = new TreeMap<>();
        Map<String, Integer> minCount = new TreeMap<>();
        for (TraceabilityPredicate[][] aisle : matches) {
            for (TraceabilityPredicate[] row : aisle) {
                for (TraceabilityPredicate predicate : row) {
                    if (predicate == null) continue;
                    Set<String> here = new HashSet<>();
                    List<SimplePredicate> all = new ArrayList<>(predicate.common);
                    all.addAll(predicate.limited);
                    for (SimplePredicate simple : all) {
                        if (simple.candidates == null) continue;
                        BlockInfo[] candidates;
                        try {
                            candidates = simple.candidates.get();
                        } catch (Throwable e) {
                            continue;
                        }
                        if (candidates == null) continue;
                        for (BlockInfo info : candidates) {
                            if (info == null) continue;
                            Block block = info.getBlockState().getBlock();
                            if (!(block instanceof MetaMachineBlock)) continue;
                            String id = String.valueOf(ForgeRegistries.BLOCKS.getKey(block));
                            here.add(id);
                            if (simple.maxCount > 0) maxCount.merge(id, simple.maxCount, Math::min);
                            if (simple.minCount > 0) minCount.merge(id, simple.minCount, Math::max);
                        }
                    }
                    for (String id : here) positions.merge(id, 1, Integer::sum);
                }
            }
        }
        Map<String, Object> out = new TreeMap<>();
        positions.forEach((id, count) -> {
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("positions", count);
            if (maxCount.containsKey(id)) part.put("max", maxCount.get(id));
            if (minCount.containsKey(id)) part.put("min", minCount.get(id));
            out.put(id, part);
        });
        return out;
    }

    private static Map<String, Object> shape(BlockInfo[][][] blocks, Block controller) {
        int depth = blocks.length;
        int height = 0, width = 0;
        for (BlockInfo[][] aisle : blocks) {
            height = Math.max(height, aisle.length);
            for (BlockInfo[] row : aisle) width = Math.max(width, row.length);
        }
        Map<String, Integer> counts = new TreeMap<>();
        List<Object> parts = new ArrayList<>();
        int[] controllerPos = null;
        List<String> rows = new ArrayList<>();
        Map<String, Character> legend = new LinkedHashMap<>();
        for (int d = 0; d < depth; d++) {
            for (int h = 0; h < blocks[d].length; h++) {
                StringBuilder sb = new StringBuilder();
                for (int w = 0; w < blocks[d][h].length; w++) {
                    BlockInfo info = blocks[d][h][w];
                    Block block = info == null ? Blocks.AIR : info.getBlockState().getBlock();
                    if (block == Blocks.AIR) {
                        sb.append(' ');
                        continue;
                    }
                    String id = String.valueOf(ForgeRegistries.BLOCKS.getKey(block));
                    counts.merge(id, 1, Integer::sum);
                    Character c = legend.get(id);
                    if (c == null) {
                        c = symbol(legend.size());
                        legend.put(id, c);
                    }
                    sb.append(c);
                    int[] pos = { w, h, d };
                    if (block == controller) controllerPos = pos;
                    else if (block instanceof MetaMachineBlock) {
                        Map<String, Object> part = new LinkedHashMap<>();
                        part.put("id", id);
                        part.put("pos", pos);
                        parts.add(part);
                    }
                }
                rows.add(sb.toString());
            }
            rows.add("");
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("size", new int[] { width, height, depth });
        s.put("controller", controllerPos);
        s.put("block_count", counts.values().stream().mapToInt(Integer::intValue).sum());
        s.put("blocks", counts);
        s.put("parts", parts);
        Map<String, String> legendOut = new LinkedHashMap<>();
        legend.forEach((k, v) -> legendOut.put(String.valueOf(v), k));
        s.put("legend", legendOut);
        s.put("aisles", rows);
        return s;
    }

    private static char symbol(int i) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#$%&*+=?@^~";
        return i < chars.length() ? chars.charAt(i) : (char) (0x100 + i);
    }

    /** Applies the pipe size (tiny..huge, single..hex) to the material's base properties, as a placed pipe does. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object modified(MaterialPipeBlock<?, ?, ?> block, Object base) {
        return ((com.gregtechceu.gtceu.api.pipenet.IPipeType) block.pipeType).modifyProperties(base);
    }

    private static Map<String, Object> pipes() {
        Map<String, Object> cables = new TreeMap<>();
        Map<String, Object> fluid = new TreeMap<>();
        Map<String, Object> item = new TreeMap<>();
        Map<String, Object> other = new TreeMap<>();
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            if (!(block instanceof PipeBlock<?, ?, ?> pipe)) continue;
            String id = String.valueOf(ForgeRegistries.BLOCKS.getKey(block));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", pipe.pipeType.name().toLowerCase(Locale.ROOT));
            if (block instanceof MaterialPipeBlock<?, ?, ?> mat) {
                data.put("material", mat.material.getName());
                Object props;
                try {
                    props = modified(mat, mat.createRawData(block.defaultBlockState(), ItemStack.EMPTY));
                } catch (Throwable e) {
                    data.put("error", e.toString());
                    other.put(id, data);
                    continue;
                }
                if (props instanceof WireProperties w) {
                    data.put("voltage", w.getVoltage());
                    data.put("amperage", w.getAmperage());
                    data.put("loss_per_block", w.getLossPerBlock());
                    data.put("superconductor", w.isSuperconductor());
                    cables.put(id, data);
                } else if (props instanceof FluidPipeProperties f) {
                    data.put("throughput", f.getThroughput());
                    data.put("channels", f.getChannels());
                    data.put("max_temperature", f.getMaxFluidTemperature());
                    data.put("gas_proof", f.isGasProof());
                    data.put("acid_proof", f.isAcidProof());
                    data.put("cryo_proof", f.isCryoProof());
                    data.put("plasma_proof", f.isPlasmaProof());
                    fluid.put(id, data);
                } else if (props instanceof ItemPipeProperties p) {
                    data.put("transfer_rate", p.getTransferRate());
                    data.put("priority", p.getPriority());
                    item.put(id, data);
                } else {
                    other.put(id, data);
                }
            } else {
                other.put(id, data);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cables", cables);
        out.put("fluid_pipes", fluid);
        out.put("item_pipes", item);
        out.put("other", other);
        return out;
    }
}
