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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.event.world.BiomeLoadingEvent;

public class StoneReplacer extends Feature<NoneFeatureConfiguration> {
	public static final StoneReplacer FEATURE = new StoneReplacer();
	private static final ResourceLocation[] VANILLA_MATCHING_STONE_FEATURES = new ResourceLocation[] {
			new ResourceLocation("minecraft", "ore_granite_upper"),
			new ResourceLocation("minecraft", "ore_granite_lower"),
			new ResourceLocation("minecraft", "ore_diorite_upper"),
			new ResourceLocation("minecraft", "ore_diorite_lower"),
			new ResourceLocation("minecraft", "ore_andesite_upper"),
			new ResourceLocation("minecraft", "ore_andesite_lower"),
			new ResourceLocation("minecraft", "ore_tuff")
	};
	private static Holder<PlacedFeature> placedFeature;

	private final Lock geologyLock = new ReentrantLock();
	private final Map<net.minecraft.resources.ResourceKey<Level>, CachedGeology> geologyByDimension =
			new ConcurrentHashMap<>();

	private StoneReplacer() {
		super(NoneFeatureConfiguration.CODEC);
		setRegistryName(OreSpawn.MODID, "stone_replacer");
	}

	public static void registerConfiguredFeature() {
		ResourceLocation id = new ResourceLocation(OreSpawn.MODID, "stone_replacer");
		Holder<ConfiguredFeature<?, ?>> configured = BuiltinRegistries.register(BuiltinRegistries.CONFIGURED_FEATURE,
				id, new ConfiguredFeature<NoneFeatureConfiguration, StoneReplacer>(FEATURE,
						NoneFeatureConfiguration.INSTANCE));
		placedFeature = BuiltinRegistries.register(BuiltinRegistries.PLACED_FEATURE, id,
				new PlacedFeature(configured, Collections.emptyList()));
	}

	public static void onBiomeLoading(BiomeLoadingEvent event) {
		if (WorldgenBenchmark.isVanillaBaseline()) {
			return;
		}

		if (TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(event.getCategory(),
				OreSpawnConfig.placeOreSpawnRock(), GeomeConfig.hasTerrainReplacement(Level.OVERWORLD))) {
			removeVanillaMatchingStoneFeatures(event);
		}
		install(event.getGeneration());
	}

	static boolean install(net.minecraftforge.common.world.BiomeGenerationSettingsBuilder generation) {
		return placeUniqueAt(generation.getFeatures(
				GenerationStep.Decoration.LOCAL_MODIFICATIONS), placedFeature, 0);
	}

	static Holder<PlacedFeature> placedFeature() {
		return placedFeature;
	}

	static boolean placeUniqueAt(List<Holder<PlacedFeature>> features,
			Holder<PlacedFeature> feature, int index) {
		if (feature == null) return false;
		int current = -1;
		for (int candidate = 0; candidate < features.size(); candidate++) {
			if (features.get(candidate).value() == feature.value()) {
				current = candidate;
				break;
			}
		}
		int target = Math.min(index, features.size() - (current >= 0 ? 1 : 0));
		if (current == target) return false;
		if (current >= 0) features.remove(current);
		features.add(target, feature);
		return true;
	}

	static boolean removeVanillaMatchingStoneFeatures(List<Holder<PlacedFeature>> features) {
		return features.removeIf(StoneReplacer::isVanillaMatchingStoneFeature);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel world = context.level();
		BlockPos pos = context.origin();
		net.minecraft.resources.ResourceKey<Level> dimension = world.getLevel().dimension();
		BakedTerrainDimension terrain = GeomeConfig.terrainDimension(dimension);
		BakedGeomeConfig config = GeomeConfig.baked(dimension);
		WorldGeologyProfile profile = WorldGeologyProfileManager.activeProfile();
		if (!OreSpawnConfig.placeOreSpawnRock() || terrain == null || config == null
				|| (profile.hasLegacyMineralogySnapshot() && !profile.cyanoEnabled())) {
			return false;
		}

		ChunkAccess chunk = world.getChunk(pos);
		CachedGeology geology = geology(dimension, world.getSeed(), config);
		if (geology.legacy != null) {
			geology.legacy.replaceStoneInChunk(world, chunk, terrain);
		} else {
			geology.sky.replaceStoneInChunk(world, chunk, terrain);
		}
		return true;
	}

	private static void removeVanillaMatchingStoneFeatures(BiomeLoadingEvent event) {
		event.getGeneration().getFeatures(GenerationStep.Decoration.UNDERGROUND_ORES)
				.removeIf(StoneReplacer::isVanillaMatchingStoneFeature);
	}

	private static boolean isVanillaMatchingStoneFeature(Holder<PlacedFeature> feature) {
		for (ResourceLocation id : VANILLA_MATCHING_STONE_FEATURES) {
			if (feature.is(id)) {
				return true;
			}
		}
		return false;
	}

	private CachedGeology geology(net.minecraft.resources.ResourceKey<Level> dimension, long seed,
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
