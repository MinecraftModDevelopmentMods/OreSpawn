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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.util.RandomSource;

import com.mojang.serialization.Codec;

public class StoneReplacer implements Feature {
	public static final StoneReplacer FEATURE = new StoneReplacer();
	public static final com.mojang.serialization.MapCodec<StoneReplacer> CODEC =
			com.mojang.serialization.MapCodec.unit(FEATURE);
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
	}

	public static void registerConfiguredFeature() {
		placedFeature = WorldgenFeatureHolders.direct(FEATURE);
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

	static synchronized boolean wrapVanillaMatchingStoneFeatures(List<Holder<PlacedFeature>> features) {
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

	static synchronized void clearMatchingStoneGates() {
		MATCHING_STONE_GATES.clear();
	}

	public static com.mojang.serialization.MapCodec<? extends Feature>
			matchingStoneGateCodec() {
		return MatchingStoneGateFeature.CODEC;
	}

	@Override
	public com.mojang.serialization.MapCodec<StoneReplacer> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel world, ChunkGenerator chunkGenerator,
			RandomSource random, BlockPos pos) {
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
		return Holder.direct(new PlacedFeature(
				Holder.direct(new MatchingStoneGateFeature(original.value().feature())),
				original.value().placement()));
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

	private static final class MatchingStoneGateFeature implements Feature {
		static final com.mojang.serialization.MapCodec<MatchingStoneGateFeature> CODEC =
				Feature.CODEC.fieldOf("delegate")
						.xmap(MatchingStoneGateFeature::new, value -> value.delegate);
		final Holder<Feature> delegate;

		MatchingStoneGateFeature() {
			this(Holder.direct(FEATURE));
		}

		MatchingStoneGateFeature(Holder<Feature> delegate) {
			this.delegate = delegate;
		}

		@Override
		public com.mojang.serialization.MapCodec<MatchingStoneGateFeature> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel world, ChunkGenerator chunkGenerator,
				RandomSource random, BlockPos origin) {
			ResourceKey<Level> dimension = world.getLevel().dimension();
			if (!WorldgenBenchmark.isVanillaBaseline()
					&& TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
							dimension, OreSpawnConfig.placeOreSpawnRock(),
							GeomeConfig.hasTerrainReplacement(dimension))) {
				return false;
			}
			return delegate.value().place(world, chunkGenerator, random, origin);
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
