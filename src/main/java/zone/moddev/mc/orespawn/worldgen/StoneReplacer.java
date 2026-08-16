package zone.moddev.mc.orespawn.worldgen;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import zone.moddev.mc.orespawn.OreSpawnConfig;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import com.mojang.serialization.Codec;

public class StoneReplacer extends Feature<NoneFeatureConfiguration> {
	public static final StoneReplacer FEATURE = new StoneReplacer();
	private static final MatchingStoneGateFeature MATCHING_STONE_GATE =
			new MatchingStoneGateFeature();
	private static final Identifier[] VANILLA_MATCHING_STONE_FEATURES = new Identifier[] {
			Identifier.fromNamespaceAndPath("minecraft", "ore_granite_upper"),
			Identifier.fromNamespaceAndPath("minecraft", "ore_granite_lower"),
			Identifier.fromNamespaceAndPath("minecraft", "ore_diorite_upper"),
			Identifier.fromNamespaceAndPath("minecraft", "ore_diorite_lower"),
			Identifier.fromNamespaceAndPath("minecraft", "ore_andesite_upper"),
			Identifier.fromNamespaceAndPath("minecraft", "ore_andesite_lower"),
			Identifier.fromNamespaceAndPath("minecraft", "ore_tuff")
	};
	private static Holder<PlacedFeature> placedFeature;
	private static final Map<PlacedFeature, Holder<PlacedFeature>> MATCHING_STONE_GATES =
			new IdentityHashMap<>();

	private final Lock geologyLock = new ReentrantLock();
	private final Map<net.minecraft.resources.ResourceKey<Level>, CachedGeology> geologyByDimension =
			new ConcurrentHashMap<>();

	private StoneReplacer() {
		super(NoneFeatureConfiguration.CODEC);
	}

	public static void registerConfiguredFeature() {
		placedFeature = WorldgenFeatureHolders.direct(FEATURE);
	}

	static Holder<PlacedFeature> placedFeature() {
		return placedFeature;
	}

	static boolean removeVanillaMatchingStoneFeatures(List<Holder<PlacedFeature>> features) {
		return features.removeIf(StoneReplacer::isVanillaMatchingStoneFeature);
	}

	static boolean wrapVanillaMatchingStoneFeatures(List<Holder<PlacedFeature>> features) {
		boolean changed = false;
		for (int i = 0; i < features.size(); i++) {
			Holder<PlacedFeature> original = features.get(i);
			if (!isVanillaMatchingStoneFeature(original)) continue;
			Holder<PlacedFeature> wrapper = MATCHING_STONE_GATES.computeIfAbsent(
					original.value(), ignored -> matchingStoneGate(original));
			features.set(i, wrapper);
			changed = true;
		}
		return changed;
	}

	public static Feature<?> matchingStoneGateFeature() {
		return MATCHING_STONE_GATE;
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

	static boolean isVanillaMatchingStoneFeature(Holder<PlacedFeature> feature) {
		for (Identifier id : VANILLA_MATCHING_STONE_FEATURES) {
			if (feature.is(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Replacing one ordinary rock with another cannot affect any heightmap or
	 * light data. Bypass {@link ChunkAccess#setBlockState} in that case: 26.1
	 * updates every persisted heightmap for each call, which is disproportionately
	 * expensive when a geology pass changes millions of otherwise equivalent
	 * solid blocks. The section setter still updates palette and block/fluid
	 * counts; unusual configured outputs retain Minecraft's full update path.
	 */
	static void setRockState(ChunkAccess chunk, BlockPos pos, BlockState current,
			BlockState replacement) {
		if (hasEquivalentHeightAndLightProperties(current, replacement)) {
			LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
			// Like NoiseBasedChunkGenerator, this feature exclusively owns the
			// section it is generating, so the palette's unchecked worldgen path is safe.
			section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15,
					replacement, false);
		} else {
			chunk.setBlockState(pos, replacement, 0);
		}
	}

	static boolean hasEquivalentHeightAndLightProperties(BlockState current,
			BlockState replacement) {
		if (current.getFluidState().isEmpty() != replacement.getFluidState().isEmpty()) {
			return false;
		}
		if (LightEngine.hasDifferentLightProperties(current, replacement)) {
			return false;
		}
		for (Heightmap.Types type : Heightmap.Types.values()) {
			if (type.isOpaque().test(current) != type.isOpaque().test(replacement)) {
				return false;
			}
		}
		return true;
	}

	private static Holder<PlacedFeature> matchingStoneGate(Holder<PlacedFeature> original) {
		MatchingStoneConfig config = new MatchingStoneConfig(original.value().feature());
		Holder<ConfiguredFeature<?, ?>> configured = Holder.direct(
				new ConfiguredFeature<MatchingStoneConfig, MatchingStoneGateFeature>(
						MATCHING_STONE_GATE, config));
		return Holder.direct(new PlacedFeature(configured, original.value().placement()));
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

	private static final class MatchingStoneConfig implements FeatureConfiguration {
		static final Codec<MatchingStoneConfig> CODEC = ConfiguredFeature.CODEC
				.fieldOf("delegate")
				.xmap(MatchingStoneConfig::new, value -> value.delegate)
				.codec();
		final Holder<ConfiguredFeature<?, ?>> delegate;

		MatchingStoneConfig(Holder<ConfiguredFeature<?, ?>> delegate) {
			this.delegate = delegate;
		}
	}

	private static final class MatchingStoneGateFeature extends Feature<MatchingStoneConfig> {
		MatchingStoneGateFeature() {
			super(MatchingStoneConfig.CODEC);
		}

		@Override
		public boolean place(FeaturePlaceContext<MatchingStoneConfig> context) {
			ResourceKey<Level> dimension = context.level().getLevel().dimension();
			if (!WorldgenBenchmark.isVanillaBaseline()
					&& TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
							dimension, OreSpawnConfig.placeOreSpawnRock(),
							GeomeConfig.hasTerrainReplacement(dimension))) {
				return false;
			}
			return context.config().delegate.value().place(context.level(),
					context.chunkGenerator(), context.random(), context.origin());
		}
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
