package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.OreSpawnConfig;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.IPlacementConfig;

public class StoneReplacer extends ContextFeature<NoFeatureConfig> {
	public static final StoneReplacer FEATURE = new StoneReplacer();
	private static final ResourceLocation[] VANILLA_MATCHING_STONE_FEATURES = new ResourceLocation[] {
			new ResourceLocation("minecraft", "ore_granite"),
			new ResourceLocation("minecraft", "ore_diorite"),
			new ResourceLocation("minecraft", "ore_andesite")
	};
	private static CompositeFeature<?, ?> configuredFeature;

	private final Lock geologyLock = new ReentrantLock();
	private final Map<ResourceLocation, CachedGeology> geologyByDimension =
			new ConcurrentHashMap<>();

	private StoneReplacer() {
		super();
	}

	public static void registerConfiguredFeature() {
		configuredFeature = net.minecraft.world.biome.Biome.createCompositeFeature(
				FEATURE, new NoFeatureConfig(), net.minecraft.world.biome.Biome.PASSTHROUGH,
				IPlacementConfig.NO_PLACEMENT_CONFIG);
	}

	static CompositeFeature<?, ?> configuredFeature() {
		return configuredFeature;
	}

	static boolean removeVanillaMatchingStoneFeatures(List<CompositeFeature<?, ?>> features) {
		return features.removeIf(StoneReplacer::isVanillaMatchingStoneFeature);
	}

	@Override
	boolean place(FeaturePlaceContext<NoFeatureConfig> context) {
		IWorld world = context.level();
		ResourceLocation dimension = WorldIds.dimension(world);
		BakedTerrainDimension terrain = GeomeConfig.terrainDimension(dimension);
		BakedGeomeConfig config = GeomeConfig.baked(dimension);
		WorldGeologyProfile profile = WorldGeologyProfileManager.activeProfile();
		if (!OreSpawnConfig.placeOreSpawnRock() || terrain == null || config == null
				|| (profile.hasLegacyMineralogySnapshot() && !profile.cyanoEnabled())) {
			return false;
		}

		IChunk chunk = context.decorationChunk();
		CachedGeology geology = geology(dimension, world.getSeed(), config);
		if (geology.legacy != null) {
			geology.legacy.replaceStoneInChunk(world, chunk, terrain);
		} else {
			geology.sky.replaceStoneInChunk(world, chunk, terrain);
		}
		return true;
	}

	private static boolean isVanillaMatchingStoneFeature(CompositeFeature<?, ?> feature) {
		return ConfiguredFeatureInspector.outputsAny(feature,
				net.minecraft.init.Blocks.GRANITE, net.minecraft.init.Blocks.DIORITE,
				net.minecraft.init.Blocks.ANDESITE);
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
