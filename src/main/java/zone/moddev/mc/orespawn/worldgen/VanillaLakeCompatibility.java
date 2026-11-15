package zone.moddev.mc.orespawn.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderOverworld;
import net.minecraft.world.gen.ChunkProviderSettings;
import net.minecraft.world.gen.feature.WorldGenLakes;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import zone.moddev.mc.orespawn.api.FormationSample;
import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreSpawnApi;

/** Keeps native lava-lake lining from erasing an already placed formation. */
final class VanillaLakeCompatibility {
    private static final Map<WorldServer, ChunkProviderSettings> SETTINGS = new WeakHashMap<>();
    private static final WorldGenLakes LAVA = new WorldGenLakes(Blocks.LAVA);

    private VanillaLakeCompatibility() { }

    static synchronized void configure(WorldServer world) {
        SETTINGS.remove(world);
        ChunkProviderOverworld generator = NaturalTerrainSurface.nativeGenerator(world);
        if (generator == null) return;
        // Resolve the native generator's actual settings once, not in a
        // per-block loop. Keep other mods' edits to that settings object.
        ChunkProviderSettings settings = ObfuscationReflectionHelper.getPrivateValue(
                ChunkProviderOverworld.class, generator,
                "field_186000_s", "settings");
        if (settings != null) SETTINGS.put(world, settings);
    }

    static void replaceLavaLake(PopulateChunkEvent.Populate event) {
        if (event.getType() != PopulateChunkEvent.Populate.EventType.LAVA
                || event.getResult() == Event.Result.DENY
                || !(event.getWorld() instanceof WorldServer)
                || !NaturalFormations.enabledForPlacement(event.getWorld())
                || GeomeConfig.terrainDimension(WorldIds.dimension(event.getWorld())) == null) return;
        ChunkProviderSettings settings;
        synchronized (VanillaLakeCompatibility.class) { settings = SETTINGS.get(event.getWorld()); }
        if (settings == null || settings.lavaLakeChance < 8) return;
        World world = event.getWorld();
        BlockPos origin = new BlockPos(event.getChunkX() * 16, 0, event.getChunkZ() * 16);
        BlockPos position = choosePosition(event.getRand(), origin, world.getSeaLevel(), settings.lavaLakeChance);
        if (position != null) generate(world, event.getRand(), position, LAVA,
                OreSpawnApi.createSampler((WorldServer) world).get());
        event.setResult(Event.Result.DENY);
    }

    /** Exactly the native post-event coordinate draws and sea-level branch. */
    static BlockPos choosePosition(Random random, BlockPos origin, int seaLevel, int chance) {
        int x = random.nextInt(16) + 8;
        int y = random.nextInt(random.nextInt(248) + 8);
        int z = random.nextInt(16) + 8;
        return y < seaLevel || random.nextInt(chance / 8) == 0 ? origin.add(x, y, z) : null;
    }

    static boolean generate(World world, Random random, BlockPos position, WorldGenLakes lake,
            GeologySampler sampler) {
        BlockPos origin = position.add(-8, 0, -8);
        while (origin.getY() > 5 && world.isAirBlock(origin)) origin = origin.down();
        if (origin.getY() <= 4) return lake.generate(world, random, position);
        origin = origin.down(4);
        Map<BlockPos, IBlockState> original = new LinkedHashMap<>();
        // Native WorldGenLakes reads/writes only this bounded 16x8x16 box.
        // Snapshot loaded cells only; never cause neighbouring chunk loads.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            GeologyColumn column = null;
            for (int y = 0; y < 8; y++) {
                BlockPos at = origin.add(x, y, z);
                if (!world.isBlockLoaded(at, false)) continue;
                IBlockState state = world.getBlockState(at);
                if (NaturalFormations.material(state) == null || world.getTileEntity(at) != null) continue;
                if (column == null) column = sampler.sampleColumn(at.getX(), at.getZ(), 64);
                Optional<FormationSample> formation = column.formationAt(at.getY());
                // A material such as granite is also ordinary strata. Only an
                // exact winning formation here is eligible, never all granite.
                if (formation.isPresent() && formation.get().material().state().equals(state))
                    original.put(at, state);
            }
        }
        boolean generated = lake.generate(world, random, position);
        for (Map.Entry<BlockPos, IBlockState> entry : original.entrySet()) {
            BlockPos at = entry.getKey();
            // The native solid rim writes exactly plain stone. Keep actual
            // cavity air, lava, water, other ores and tile entities untouched.
            if (world.getBlockState(at).equals(Blocks.STONE.getDefaultState())
                    && world.getTileEntity(at) == null)
                world.setBlockState(at, entry.getValue(), 2);
        }
        return generated;
    }

    static synchronized void clear() { SETTINGS.clear(); }
}
