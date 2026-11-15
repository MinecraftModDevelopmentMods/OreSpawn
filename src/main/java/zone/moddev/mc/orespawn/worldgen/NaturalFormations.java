package zone.moddev.mc.orespawn.worldgen;

import java.util.*;
import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.MapGenStructureData;
import zone.moddev.mc.orespawn.api.*;
import zone.moddev.mc.orespawn.util.JsonCopies;

/** One baked natural-substrate path shared by terrain and the public sampler. */
public final class NaturalFormations {
    public static final String SECTION = "rock_formations";
    private static final String[] TYPES = {"alkaline_complex", "granitic_contact", "ultramafic_alteration",
            "barren_pipe", "quartz_vein", "weathering_profile"};
    private static final Map<WorldServer, NaturalTerrainSurface> SURFACES = new WeakHashMap<>();
    private static List<Rule> rules = Collections.emptyList();
    private static Map<IBlockState, FormationMaterial> materials = Collections.emptyMap();
    private static String fingerprint = "";
    static {
        for (String type : TYPES) RockFormationRegistry.register(new ResourceLocation("orespawn", type),
                json -> new BuiltinRockFormation(type, json));
    }
    private NaturalFormations() { }
    static synchronized void install(JsonObject profile) {
        JsonObject section = profile.has(SECTION) ? profile.getAsJsonObject(SECTION) : new JsonObject();
        String json = section.toString();
        if (fingerprint.equals(json)) return;
        List<Rule> baked = new ArrayList<>();
        Map<IBlockState, FormationMaterial> all = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            JsonObject def = entry.getValue().getAsJsonObject();
            if (def.has("enabled") && !def.get("enabled").getAsBoolean()) continue;
            CompiledRockFormation compiler = RockFormationRegistry.compile(new ResourceLocation(def.get("type").getAsString()),
                    JsonCopies.copy(def));
            baked.add(new Rule(new ResourceLocation(entry.getKey()), BuiltinRockFormation.integer(def, "priority", 0),
                    BuiltinRockFormation.integer(def, "algorithm_revision", 1), compiler));
            for (Map.Entry<String, JsonElement> value : def.getAsJsonObject("materials").entrySet()) {
                JsonObject m = value.getValue().getAsJsonObject();
                Block block = Block.REGISTRY.getObject(new ResourceLocation(m.get("block").getAsString()));
                FormationMaterial material = new FormationMaterial(block.getStateFromMeta(BuiltinRockFormation.integer(m, "metadata", 0)),
                        m.has("family") ? GeologyFamily.valueOf(m.get("family").getAsString().toUpperCase(Locale.ROOT)) : null);
                FormationMaterial prior = all.put(material.state(), material);
                if (prior != null && !prior.family().equals(material.family()))
                    throw new IllegalArgumentException("Conflicting formation material families");
            }
        }
        baked.sort(Comparator.comparingInt((Rule r) -> r.priority).thenComparing(r -> r.id.toString()));
        rules = Collections.unmodifiableList(baked);
        materials = Collections.unmodifiableMap(all);
        fingerprint = json;
        SURFACES.clear();
    }
    public static Collection<FormationMaterial> materials() { return materials.values(); }
    static boolean enabledForPlacement(net.minecraft.world.World world) {
        return !rules.isEmpty() && world instanceof WorldServer
                && NaturalTerrainSurface.nativeGenerator((WorldServer) world) != null
                && zone.moddev.mc.orespawn.OreSpawnConfig.placeOreSpawnRock()
                && WorldGeologyProfileManager.activeProfile().geologyMode()
                == zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode.GEOME;
    }
    static FormationMaterial material(IBlockState state) { return materials.get(state); }
    public static GeologyColumn wrap(WorldServer world, GeologyColumn base, Biome biome) {
        BakedTerrainDimension terrain = GeomeConfig.terrainDimension(base.dimension());
        if (!enabledForPlacement(world) || terrain == null || !terrain.acceptsBiome(base.biome())) return base;
        NaturalTerrainSurface surface;
        synchronized (SURFACES) { surface = SURFACES.computeIfAbsent(world, NaturalTerrainSurface::new); }
        final NaturalTerrainSurface heights = surface;
        GeologyColumn current = base;
        for (Rule rule : rules) {
            RockFormationContext context = new RockFormationContext(world.getSeed(), current,
                    biome.getTemperature(), biome.getRainfall(), () -> heights.at(base.blockX(), base.blockZ()));
            RockFormationColumn column = rule.compiled.prepare(context);
            if (column != null) current = new Column(current, rule, column);
        }
        return current;
    }
    static void apply(WorldServer world, Chunk chunk, BakedTerrainDimension terrain) {
        if (!enabledForPlacement(world)) return;
        GeologySampler sampler = OreSpawnApi.createSampler(world).get();
        List<int[]> boxes = structures(world, chunk.xPosition, chunk.zPosition);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = chunk.xPosition * 16 + dx, z = chunk.zPosition * 16 + dz;
            GeologyColumn column = sampler.sampleColumn(x, z, chunk.getHeightValue(dx, dz) - 1);
            if (!(column instanceof Column)) continue;
            GeologyColumn country = column;
            while (country instanceof Column) country = ((Column) country).base;
            for (int y = 5; y < chunk.getHeightValue(dx, dz); y++) {
                IBlockState target = column.rockAt(y), parent = country.rockAt(y);
                if (target.equals(parent) || protectedAt(boxes, x, y, z)) continue;
                pos.setPos(x, y, z);
                IBlockState current = chunk.getBlockState(pos);
                // Native stone patches may have spilled across this boundary
                // before this chunk's early pass. They are country rock, not a
                // protected metal ore; the same planned substrate wins either way.
                if ((!current.equals(parent) && !nativeStoneVariant(current))
                        || chunk.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK) != null) continue;
                chunk.setBlockState(pos, target);
                changed = true;
            }
        }
        if (changed) ChunkAccessCompat.markChanged(chunk);
    }
    static boolean nativeStoneVariant(IBlockState state) {
        if (state.getBlock() != net.minecraft.init.Blocks.STONE) return false;
        int metadata = net.minecraft.init.Blocks.STONE.getMetaFromState(state);
        return metadata == 0 || metadata == 1 || metadata == 3 || metadata == 5;
    }
    private static List<int[]> structures(WorldServer world, int cx, int cz) {
        List<int[]> result = new ArrayList<>();
        for (String name : new String[]{"Mineshaft", "Village", "Stronghold", "Temple", "Monument"}) {
            MapGenStructureData data = (MapGenStructureData) world.getMapStorage().getOrLoadData(MapGenStructureData.class, name);
            if (data == null) continue;
            NBTTagCompound root = data.getTagCompound();
            for (String key : root.getKeySet()) {
                int[] b = root.getCompoundTag(key).getIntArray("BB");
                if (b.length == 6 && b[0] <= cx * 16 + 15 && b[3] >= cx * 16 && b[2] <= cz * 16 + 15 && b[5] >= cz * 16) result.add(b);
            }
        }
        return result;
    }
    private static boolean protectedAt(List<int[]> boxes, int x, int y, int z) {
        for (int[] b : boxes) if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) return true;
        return false;
    }
    private static final class Rule {
        final ResourceLocation id; final int priority, revision; final CompiledRockFormation compiled;
        Rule(ResourceLocation id, int priority, int revision, CompiledRockFormation compiled) {
            this.id = id; this.priority = priority; this.revision = revision; this.compiled = compiled;
        }
    }
    private static final class Column implements GeologyColumn {
        final GeologyColumn base; final Rule rule; final RockFormationColumn column;
        Column(GeologyColumn base, Rule rule, RockFormationColumn column) { this.base = base; this.rule = rule; this.column = column; }
        public ResourceLocation dimension() { return base.dimension(); }
        public ResourceLocation biome() { return base.biome(); }
        public ResourceLocation geome() { return base.geome(); }
        public int blockX() { return base.blockX(); }
        public int blockZ() { return base.blockZ(); }
        public int surfaceY() { return base.surfaceY(); }
        public IBlockState rockAt(int y) { FormationMaterial m = column.at(y); return m == null ? base.rockAt(y) : m.state(); }
        public Optional<GeologyFamily> familyAt(int y) { FormationMaterial m = column.at(y); return m == null ? base.familyAt(y) : m.family(); }
        public Optional<FormationSample> formationAt(int y) {
            FormationMaterial m = column.at(y);
            if (m == null) return base.formationAt(y);
            IBlockState parent = column.parentAt(y);
            return Optional.of(new FormationSample(rule.id, rule.revision, parent == null ? base.rockAt(y) : parent, m));
        }
    }
}
