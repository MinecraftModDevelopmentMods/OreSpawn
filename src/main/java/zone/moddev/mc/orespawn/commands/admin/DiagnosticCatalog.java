package zone.moddev.mc.orespawn.commands.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.orespawn.worldgen.NaturalFormations;

/** Conservative identities: an ore/deposit match always wins over terrain. */
public final class DiagnosticCatalog {
    private final Set<IBlockState> ores = new HashSet<>();
    private final Set<Block> deposits = new HashSet<>();
    private final Set<IBlockState> rocks = new HashSet<>();
    private final Set<Block> terrain = new HashSet<>();
    private final Set<Block> backgroundFluids = new HashSet<>();

    public DiagnosticCatalog(JsonObject profile) {
        for (Block block : new Block[] {Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE,
                Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE,
                Blocks.REDSTONE_ORE, Blocks.NETHER_QUARTZ_ORE}) ores.addAll(block.getStateContainer().getValidStates());
        for (Block block : new Block[] {Blocks.STONE, Blocks.GRANITE, Blocks.POLISHED_GRANITE, Blocks.DIORITE,
                Blocks.POLISHED_DIORITE, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.SAND,
                Blocks.GRAVEL, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.NETHERRACK,
                Blocks.END_STONE, Blocks.CLAY, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA,
                Blocks.MYCELIUM, Blocks.SNOW_BLOCK, Blocks.SNOW}) terrain.add(block);
        backgroundFluids.add(Blocks.WATER); backgroundFluids.add(Blocks.WATER);
        backgroundFluids.add(Blocks.LAVA); backgroundFluids.add(Blocks.LAVA);
        object(profile, "ores").entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject rule = definition(entry.getValue().getAsJsonObject(), entry.getKey());
            addState(rule, "block", "metadata", ores);
            addState(rule, "deep_output", "deep_output_metadata", ores);
            if (rule.has("outputs") && rule.get("outputs").isJsonArray())
                for (JsonElement output : rule.getAsJsonArray("outputs"))
                    if (output.isJsonObject()) addState(output.getAsJsonObject(), "block", "metadata", ores);
        });
        object(profile, "rocks").entrySet().forEach(entry -> {
            if (entry.getValue().isJsonObject()) addState(definition(entry.getValue().getAsJsonObject(), entry.getKey()), "block", "metadata", rocks);
        });
        object(profile, "ore_source_policies").entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject policy = entry.getValue().getAsJsonObject();
            if (!policy.has("candidates") || !policy.get("candidates").isJsonArray()) return;
            for (JsonElement candidate : policy.getAsJsonArray("candidates"))
                if (candidate.isJsonObject()) addState(candidate.getAsJsonObject(), "registry_id", "metadata", ores);
        });
        NaturalFormations.materials().forEach(material -> rocks.add(material.state()));
        object(profile, "fluid_deposits").entrySet().forEach(entry -> {
            if (entry.getValue().isJsonObject()) {
                Block block = block(entry.getValue().getAsJsonObject(), "block");
                if (block != null) deposits.add(block);
            }
        });
        object(profile, "dimension_materials").entrySet().forEach(entry -> {
            if (entry.getValue().isJsonObject()) {
                for (String key : new String[]{"default_fluid", "deep_aquifer_fluid"}) {
                    Block block = block(entry.getValue().getAsJsonObject(), key);
                    if (block != null && block.getDefaultState().getMaterial().isLiquid()) backgroundFluids.add(block);
                }
            }
        });
        if (deposits.contains(Blocks.WATER) || deposits.contains(Blocks.WATER)) {
            deposits.add(Blocks.WATER); deposits.add(Blocks.WATER);
        }
        if (deposits.contains(Blocks.LAVA) || deposits.contains(Blocks.LAVA)) {
            deposits.add(Blocks.LAVA); deposits.add(Blocks.LAVA);
        }
        // Exact ore tags are the flattened equivalent of legacy Ore Dictionary registrations.
        net.minecraft.tags.BlockTags.getCollection().getTagMap().forEach((id, tag) -> {
            if (id.getPath().equals("ores") || id.getPath().startsWith("ores/"))
                for (Block block : tag.getAllElements()) ores.addAll(block.getStateContainer().getValidStates());
        });
    }

    public String category(IBlockState state) {
        if (ores.contains(state)) return "ore";
        if (deposits.contains(state.getBlock())) return "fluid_deposit";
        if (state.isAir()) return "air";
        if (terrain.contains(state.getBlock()) || rocks.contains(state) || backgroundFluids.contains(state.getBlock())) return "terrain";
        return "unclassified";
    }

    public boolean removable(IBlockState state, boolean tileEntity) {
        return !tileEntity && state.getBlock() != Blocks.BEDROCK && "terrain".equals(category(state));
    }

    public String fingerprint() {
        java.util.TreeSet<String> identities = new java.util.TreeSet<>();
        ores.forEach(state -> identities.add("ore:" + DiagnosticStates.key(state)));
        rocks.forEach(state -> identities.add("rock:" + DiagnosticStates.key(state)));
        deposits.forEach(block -> identities.add("deposit:" + ForgeRegistries.BLOCKS.getKey(block)));
        terrain.forEach(block -> identities.add("terrain:" + ForgeRegistries.BLOCKS.getKey(block)));
        backgroundFluids.forEach(block -> identities.add("fluid:" + ForgeRegistries.BLOCKS.getKey(block)));
        return DiagnosticFiles.hash(String.join("\n", identities).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void addState(JsonObject object, String key, String metadata, Set<IBlockState> target) {
        Block block = block(object, key);
        if (block != null) try {
            int value = object.has(metadata) ? object.get(metadata).getAsInt() : 0;
            JsonObject state = new JsonObject(); state.addProperty("block", ForgeRegistries.BLOCKS.getKey(block).toString());
            if (object.has("properties")) state.add("properties", object.get("properties"));
            target.add(zone.moddev.mc.orespawn.util.ConfiguredBlockState.read(state));
        } catch (RuntimeException ignored) { target.add(block.getDefaultState()); }
    }

    private static JsonObject definition(JsonObject source, String fallback) {
        JsonObject copy = zone.moddev.mc.orespawn.util.JsonCopies.copy(source);
        if (!copy.has("block")) copy.addProperty("block", fallback);
        return copy;
    }

    private static Block block(JsonObject object, String key) {
        try {
            if (!object.has(key) || object.get(key).isJsonNull()) return null;
            ResourceLocation id = new ResourceLocation(object.get(key).getAsString());
            if (!ForgeRegistries.BLOCKS.containsKey(id)) return null;
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            return block == Blocks.AIR ? null : block;
        } catch (RuntimeException ignored) { return null; }
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }
}
