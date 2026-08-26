package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.OreSpawnConfig;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraftforge.event.world.BiomeLoadingEvent;

public class StoneReplacer extends ContextFeature<NoFeatureConfig> {
	public static final StoneReplacer FEATURE = new StoneReplacer();
	private static final ResourceLocation[] VANILLA_MATCHING_STONE_FEATURES = new ResourceLocation[] {
			new ResourceLocation("minecraft", "ore_granite"),
			new ResourceLocation("minecraft", "ore_diorite"),
			new ResourceLocation("minecraft", "ore_andesite")
	};
	private static ConfiguredFeature<?, ?> configuredFeature;

	private final Lock geologyLock = new ReentrantLock();
	private final Map<net.minecraft.util.RegistryKey<World>, CachedGeology> geologyByDimension =
			new ConcurrentHashMap<>();

	private StoneReplacer() {
		super(NoFeatureConfig.CODEC);
		setRegistryName(OreSpawn.MODID, "stone_replacer");
	}

	public static void registerConfiguredFeature() {
		ResourceLocation id = new ResourceLocation(OreSpawn.MODID, "stone_replacer");
		configuredFeature = Registry.register(WorldGenRegistries.CONFIGURED_FEATURE, id,
				FEATURE.configured(NoFeatureConfig.INSTANCE)
						.decorated(Placement.NOPE.configured(NoPlacementConfig.INSTANCE)));
	}

	public static void onBiomeLoading(BiomeLoadingEvent event) {
		if (WorldgenBenchmark.isVanillaBaseline()) {
			return;
		}

		if (TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(event.getCategory(),
				OreSpawnConfig.placeOreSpawnRock(), GeomeConfig.hasTerrainReplacement(World.OVERWORLD))) {
			removeVanillaMatchingStoneFeatures(event);
		}
		install(event.getGeneration());
	}

	static boolean install(net.minecraftforge.common.world.BiomeGenerationSettingsBuilder generation) {
		return placeUniqueAt(generation.getFeatures(
				GenerationStage.Decoration.LOCAL_MODIFICATIONS), configuredFeature, 0);
	}

	static ConfiguredFeature<?, ?> configuredFeature() {
		return configuredFeature;
	}

	static boolean removeVanillaMatchingStoneFeatures(List<Supplier<ConfiguredFeature<?, ?>>> features) {
		return features.removeIf(StoneReplacer::isVanillaMatchingStoneFeature);
	}

	static boolean placeUniqueAt(List<Supplier<ConfiguredFeature<?, ?>>> features,
			ConfiguredFeature<?, ?> feature, int index) {
		if (feature == null) return false;
		int current = -1;
		for (int candidate = 0; candidate < features.size(); candidate++) {
			if (features.get(candidate).get() == feature) {
				current = candidate;
				break;
			}
		}
		int target = Math.min(index, features.size() - (current >= 0 ? 1 : 0));
		if (current == target) return false;
		if (current >= 0) features.remove(current);
		features.add(target, () -> feature);
		return true;
	}

	@Override
	boolean place(FeaturePlaceContext<NoFeatureConfig> context) {
		ISeedReader world = context.level();
		BlockPos pos = context.origin();
		net.minecraft.util.RegistryKey<World> dimension = world.getLevel().dimension();
		BakedTerrainDimension terrain = GeomeConfig.terrainDimension(dimension);
		BakedGeomeConfig config = GeomeConfig.baked(dimension);
		WorldGeologyProfile profile = WorldGeologyProfileManager.activeProfile();
		if (!OreSpawnConfig.placeOreSpawnRock() || terrain == null || config == null
				|| (profile.hasLegacyMineralogySnapshot() && !profile.cyanoEnabled())) {
			return false;
		}

		IChunk chunk = world.getChunk(pos);
		CachedGeology geology = geology(dimension, world.getSeed(), config);
		if (geology.legacy != null) {
			geology.legacy.replaceStoneInChunk(world, chunk, terrain);
		} else {
			geology.sky.replaceStoneInChunk(world, chunk, terrain);
		}
		return true;
	}

	private static void removeVanillaMatchingStoneFeatures(BiomeLoadingEvent event) {
		event.getGeneration().getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.removeIf(StoneReplacer::isVanillaMatchingStoneFeature);
	}

	private static boolean isVanillaMatchingStoneFeature(Supplier<ConfiguredFeature<?, ?>> feature) {
		ResourceLocation featureId = WorldGenRegistries.CONFIGURED_FEATURE.getKey(feature.get());
		for (ResourceLocation id : VANILLA_MATCHING_STONE_FEATURES) {
			if (id.equals(featureId)) {
				return true;
			}
		}
		return false;
	}

	private CachedGeology geology(net.minecraft.util.RegistryKey<World> dimension, long seed,
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
