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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
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
import net.minecraft.world.level.levelgen.placement.FeatureDecorator;
import net.minecraft.world.level.levelgen.feature.configurations.NoneDecoratorConfiguration;
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
	private static ConfiguredFeature<?, ?> configuredFeature;

	private final Lock geologyLock = new ReentrantLock();
	private final Map<net.minecraft.resources.ResourceKey<Level>, CachedGeology> geologyByDimension =
			new ConcurrentHashMap<>();

	private StoneReplacer() {
		super(NoneFeatureConfiguration.CODEC);
		setRegistryName(OreSpawn.MODID, "stone_replacer");
	}

	public static void registerConfiguredFeature() {
		ResourceLocation id = new ResourceLocation(OreSpawn.MODID, "stone_replacer");
		configuredFeature = Registry.register(BuiltinRegistries.CONFIGURED_FEATURE, id,
				FEATURE.configured(NoneFeatureConfiguration.INSTANCE)
						.decorated(FeatureDecorator.NOPE.configured(NoneDecoratorConfiguration.INSTANCE)));
	}

	public static void onBiomeLoading(BiomeLoadingEvent event) {
		if (WorldgenBenchmark.isVanillaBaseline()) {
			return;
		}

		if (TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(event.getCategory(),
				OreSpawnConfig.placeOreSpawnRock(), GeomeConfig.hasTerrainReplacement(Level.OVERWORLD))) {
			removeVanillaMatchingStoneFeatures(event);
		}
		if (configuredFeature != null) {
			event.getGeneration().getFeatures(GenerationStep.Decoration.UNDERGROUND_ORES)
					.add(() -> configuredFeature);
		}
	}

	static ConfiguredFeature<?, ?> configuredFeature() {
		return configuredFeature;
	}

	static boolean removeVanillaMatchingStoneFeatures(List<Supplier<ConfiguredFeature<?, ?>>> features) {
		return features.removeIf(StoneReplacer::isVanillaMatchingStoneFeature);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel world = context.level();
		BlockPos pos = context.origin();
		net.minecraft.resources.ResourceKey<Level> dimension = world.getLevel().dimension();
		BakedTerrainDimension terrain = GeomeConfig.terrainDimension(dimension);
		BakedGeomeConfig config = GeomeConfig.baked(dimension);
		if (!OreSpawnConfig.placeOreSpawnRock() || terrain == null || config == null) {
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

	private static boolean isVanillaMatchingStoneFeature(Supplier<ConfiguredFeature<?, ?>> feature) {
		ResourceLocation featureId = BuiltinRegistries.CONFIGURED_FEATURE.getKey(feature.get());
		for (ResourceLocation id : VANILLA_MATCHING_STONE_FEATURES) {
			if (id.equals(featureId)) {
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
							? new CachedGeology(seed, mode, new Geology(seed, profile.cyanoGeomeSize(),
									profile.cyanoRockLayerNoise(), profile.cyanoLayerThickness(), config), null)
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
