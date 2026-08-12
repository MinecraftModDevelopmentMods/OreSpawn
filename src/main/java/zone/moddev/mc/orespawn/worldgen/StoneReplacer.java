package zone.moddev.mc.orespawn.worldgen;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.OreSpawnConfig;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public final class StoneReplacer {
	public static final StoneReplacer FEATURE = new StoneReplacer();
	private static final ResourceLocation[] VANILLA_MATCHING_STONE_FEATURES = new ResourceLocation[] {
			new ResourceLocation("minecraft", "ore_granite"),
			new ResourceLocation("minecraft", "ore_diorite"),
			new ResourceLocation("minecraft", "ore_andesite")
	};
	private final Lock geologyLock = new ReentrantLock();
	private final Map<ResourceLocation, CachedGeology> geologyByDimension =
			new ConcurrentHashMap<>();

	private StoneReplacer() {
	}

	public static void registerConfiguredFeature() {
		// Forge 1.10 invokes this pass from OreSpawnWorldGenerator.
	}

	boolean generate(World world, Chunk chunk, Random random) {
		ResourceLocation dimension = WorldIds.dimension(world);
		BakedTerrainDimension terrain = GeomeConfig.terrainDimension(dimension);
		BakedGeomeConfig config = GeomeConfig.baked(dimension);
		if (!OreSpawnConfig.placeOreSpawnRock() || terrain == null || config == null) {
			return false;
		}

		CachedGeology geology = geology(dimension, world.getSeed(), config);
		if (geology.legacy != null) {
			geology.legacy.replaceStoneInChunk(world, chunk, terrain);
		} else {
			geology.sky.replaceStoneInChunk(world, chunk, terrain);
		}
		return true;
	}

	private CachedGeology geology(ResourceLocation dimension, long seed,
			BakedGeomeConfig config) {
		CachedGeology current = geologyByDimension.get(dimension);
		GeologyMode mode = WorldGeologyProfileManager.geologyMode();
		if (current == null || current.seed != seed || current.mode != mode) {
			geologyLock.lock();
			try {
				current = geologyByDimension.get(dimension);
				if (current == null || current.seed != seed || current.mode != mode) {
					WorldGeologyProfile profile = WorldGeologyProfileManager.activeProfile();
					current = mode == GeologyMode.LEGACY
							? new CachedGeology(seed, mode, new Geology(seed, profile, config), null)
							: new CachedGeology(seed, mode, null, new GeomeGeology(seed, config));
					geologyByDimension.put(dimension, current);
				}
			} finally {
				geologyLock.unlock();
			}
		}
		return current;
	}

	public static void refreshWorldConfig() {
		FEATURE.clearCachedGeology();
	}

	private void clearCachedGeology() {
		geologyLock.lock();
		try {
			geologyByDimension.clear();
		} finally {
			geologyLock.unlock();
		}
	}

	private static final class CachedGeology {
		final long seed;
		final GeologyMode mode;
		final Geology legacy;
		final GeomeGeology sky;

		CachedGeology(long seed, GeologyMode mode, Geology legacy, GeomeGeology sky) {
			this.seed = seed;
			this.mode = mode;
			this.legacy = legacy;
			this.sky = sky;
		}
	}
}
