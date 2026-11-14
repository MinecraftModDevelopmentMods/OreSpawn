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
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.gen.feature.structure.StructureStart;
import zone.moddev.mc.orespawn.api.*;
import zone.moddev.mc.orespawn.util.JsonCopies;

/** One baked natural-substrate path shared by terrain and the public sampler. */
public final class NaturalFormations {
    public static final String SECTION = "rock_formations";
    private static final String[] TYPES = {"alkaline_complex", "granitic_contact", "ultramafic_alteration",
            "barren_pipe", "quartz_vein", "weathering_profile"};
    private static final Map<WorldServer, NaturalTerrainSurface> SURFACES = new WeakHashMap<>();
    private static volatile List<Rule> rules = Collections.emptyList();
    private static volatile Map<IBlockState, FormationMaterial> materials = Collections.emptyMap();
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
            ResourceLocation type = new ResourceLocation(def.get("type").getAsString());
            if (!RockFormationRegistry.contains(type)) {
                org.apache.logging.log4j.LogManager.getLogger().warn(
                        "Formation '{}' needs unavailable type '{}'; keeping its settings without placing it", entry.getKey(), type);
                continue;
            }
            boolean missing = false;
            for (Map.Entry<String, JsonElement> material : def.getAsJsonObject("materials").entrySet()) {
                ResourceLocation id = new ResourceLocation(material.getValue().getAsJsonObject().get("block").getAsString());
                if (!net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(id)) missing = true;
            }
            if (missing) {
                org.apache.logging.log4j.LogManager.getLogger().warn(
                        "Formation '{}' has unavailable material blocks; keeping its settings without placing it", entry.getKey());
                continue;
            }
            CompiledRockFormation compiler = RockFormationRegistry.compile(type, JsonCopies.copy(def));
            baked.add(new Rule(new ResourceLocation(entry.getKey()), BuiltinRockFormation.integer(def, "priority", 0),
                    BuiltinRockFormation.integer(def, "algorithm_revision", 1), compiler));
            for (Map.Entry<String, JsonElement> value : def.getAsJsonObject("materials").entrySet()) {
                JsonObject m = value.getValue().getAsJsonObject();
                Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation(m.get("block").getAsString()));
                FormationMaterial material = new FormationMaterial(zone.moddev.mc.orespawn.util.ConfiguredBlockState.read(m),
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
        synchronized (SURFACES) { SURFACES.clear(); }
    }
    public static Collection<FormationMaterial> materials() { return materials.values(); }
    static boolean hasConfiguredFormations() { return !rules.isEmpty(); }
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
                    biome.getDefaultTemperature(), biome.getDownfall(), () -> heights.at(base.blockX(), base.blockZ()));
            RockFormationColumn column = rule.compiled.prepare(context);
            if (column != null) current = new Column(current, rule, column);
        }
        return current;
    }
    static void apply(IWorld region, IChunk chunk, BakedTerrainDimension terrain) {
        if (!(region.getWorld() instanceof WorldServer)) return;
        WorldServer world = (WorldServer) region.getWorld();
        if (!enabledForPlacement(world)) return;
        GeologySampler sampler = OreSpawnApi.createSampler(world).get();
        List<net.minecraft.util.math.MutableBoundingBox> boxes = structures(region, chunk);
        if (boxes == null) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = chunk.getPos().x * 16 + dx, z = chunk.getPos().z * 16 + dz;
            GeologyColumn column = sampler.sampleColumn(x, z, (chunk.getTopBlockY(Heightmap.Type.WORLD_SURFACE_WG, dx, dz) + 1) - 1);
            if (!(column instanceof Column)) continue;
            GeologyColumn country = column;
            while (country instanceof Column) country = ((Column) country).base;
            for (int y = 5; y < (chunk.getTopBlockY(Heightmap.Type.WORLD_SURFACE_WG, dx, dz) + 1); y++) {
                IBlockState target = column.rockAt(y), parent = country.rockAt(y);
                if (target.equals(parent) || protectedAt(boxes, x, y, z)) continue;
                pos.setPos(x, y, z);
                IBlockState current = chunk.getBlockState(pos);
                // Native stone patches can spill over a chunk boundary before this pass.
                // They are country rock, so the planned formation still takes precedence.
                if ((!current.equals(parent) && !nativeStoneVariant(current))
                        || chunk.getDeferredTileEntity(pos) != null
                        || (chunk instanceof Chunk && ((Chunk) chunk).getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK) != null)) continue;
                chunk.setBlockState(pos, target, false);
                changed = true;
            }
        }
        if (changed && chunk instanceof Chunk) ((Chunk) chunk).setModified(true);
        else if (changed && chunk instanceof net.minecraft.world.chunk.ChunkPrimer)
            ((net.minecraft.world.chunk.ChunkPrimer) chunk).setModified(true);
    }
    static boolean nativeStoneVariant(IBlockState state) {
        Block block = state.getBlock();
        return block == net.minecraft.init.Blocks.STONE || block == net.minecraft.init.Blocks.GRANITE
                || block == net.minecraft.init.Blocks.DIORITE || block == net.minecraft.init.Blocks.ANDESITE;
    }

    private static List<net.minecraft.util.math.MutableBoundingBox> structures(IWorld world, IChunk chunk) {
        List<net.minecraft.util.math.MutableBoundingBox> boxes = new ArrayList<>();
        for (StructureStart start : chunk.getStructureStarts().values())
            if (start != null && start.isSizeableStructure()) boxes.add(start.getBoundingBox());
        for (Map.Entry<String, it.unimi.dsi.fastutil.longs.LongSet> entry : chunk.getStructureReferences().entrySet()) {
            for (long packed : entry.getValue()) {
                int x = (int) packed, z = (int) (packed >>> 32);
                if (world instanceof WorldGenRegion && !((WorldGenRegion) world).isChunkInBounds(x, z))
                    return null; // An unavailable structure start makes this chunk unsafe to change.
                if (!(world instanceof WorldGenRegion)
                        && !((WorldServer) world.getWorld()).getChunkProvider().chunkExists(x, z)) return null;
                StructureStart start = world.getChunk(x, z).getStructureStart(entry.getKey());
                if (start == null) return null;
                if (start.isSizeableStructure()) boxes.add(start.getBoundingBox());
            }
        }
        return boxes;
    }

    private static boolean protectedAt(List<net.minecraft.util.math.MutableBoundingBox> boxes, int x, int y, int z) {
        for (net.minecraft.util.math.MutableBoundingBox box : boxes)
            if (x >= box.minX && x <= box.maxX && y >= box.minY && y <= box.maxY
                    && z >= box.minZ && z <= box.maxZ) return true;
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
