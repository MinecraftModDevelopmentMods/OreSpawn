package zone.moddev.mc.orespawn.worldgen;

import java.util.function.Supplier;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.FeatureDecorator;
import net.minecraft.world.level.levelgen.feature.configurations.NoneDecoratorConfiguration;
import net.minecraftforge.common.world.BiomeGenerationSettingsBuilder;
import net.minecraftforge.event.world.BiomeLoadingEvent;

/** Applies provider surfaces after base surfaces and lakes, before late features. */
public final class BiomeSurfaceFeature extends Feature<NoneFeatureConfiguration> {
	public static final BiomeSurfaceFeature FEATURE = new BiomeSurfaceFeature();
	private static ConfiguredFeature<?, ?> configuredFeature;

	private BiomeSurfaceFeature() {
		super(NoneFeatureConfiguration.CODEC);
		setRegistryName(OreSpawn.MODID, "biome_surfaces");
	}

	public static void registerConfiguredFeature() {
		ResourceLocation id = new ResourceLocation(OreSpawn.MODID, "biome_surfaces");
		configuredFeature = Registry.register(BuiltinRegistries.CONFIGURED_FEATURE, id,
				FEATURE.configured(NoneFeatureConfiguration.INSTANCE)
						.decorated(FeatureDecorator.NOPE.configured(NoneDecoratorConfiguration.INSTANCE)));
	}

	public static void onBiomeLoading(BiomeLoadingEvent event) {
		install(event.getGeneration());
	}

	static boolean install(BiomeGenerationSettingsBuilder generation) {
		if (configuredFeature == null) return false;
		java.util.List<Supplier<ConfiguredFeature<?, ?>>> features = generation.getFeatures(
				GenerationStep.Decoration.LOCAL_MODIFICATIONS);
		int stoneIndex = -1;
		for (int index = 0; index < features.size(); index++) {
			if (features.get(index).get() == StoneReplacer.configuredFeature()) {
				stoneIndex = index;
				break;
			}
		}
		return StoneReplacer.placeUniqueAt(features, configuredFeature,
				stoneIndex >= 0 ? stoneIndex + 1 : features.size());
	}

	static ConfiguredFeature<?, ?> configuredFeature() {
		return configuredFeature;
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel world = context.level();
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(world.getLevel().dimension());
		if (config == null || !config.hasSurfaces()) return false;
		ChunkAccess chunk = world.getChunk(context.origin());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		boolean ceilingDimension = world.getLevel().dimensionType().hasCeiling();
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int x = minX + localX;
				int z = minZ + localZ;
				int groundY;
				int ceilingY = Integer.MIN_VALUE;
				if (ceilingDimension) {
					long column = findCeilingAndGround(chunk, cursor, x, z,
							world.getMaxBuildHeight(), world.getMinBuildHeight());
					ceilingY = (int) (column >> 32);
					groundY = (int) column;
				} else {
					groundY = findOpenGround(chunk, cursor, x, z, localX, localZ,
							world.getMinBuildHeight());
				}
				if (groundY >= world.getMinBuildHeight()) {
					changed |= applyGround(chunk, world, config, cursor, x, z,
							groundY, world.getMinBuildHeight());
				}
				if (ceilingY >= world.getMinBuildHeight()) {
					changed |= applyCeiling(chunk, world, config, cursor, x, z, ceilingY);
				}
			}
		}
		return changed;
	}

	private static int findOpenGround(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
			int x, int z, int localX, int localZ, int minY) {
		int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, localX, localZ);
		while (y >= minY && open(chunk.getBlockState(cursor.set(x, y, z)))) y--;
		return y;
	}

	private static long findCeilingAndGround(ChunkAccess chunk,
			BlockPos.MutableBlockPos cursor, int x, int z, int maxY, int minY) {
		int y = maxY - 1;
		while (y >= minY && open(chunk.getBlockState(cursor.set(x, y, z)))) y--;
		if (y < minY) return pack(Integer.MIN_VALUE, Integer.MIN_VALUE);
		while (y >= minY && !open(chunk.getBlockState(cursor.set(x, y, z)))) y--;
		int ceilingY = y + 1;
		while (y >= minY && open(chunk.getBlockState(cursor.set(x, y, z)))) y--;
		return pack(ceilingY, y);
	}

	private static boolean applyGround(ChunkAccess chunk, WorldGenLevel world,
			BakedBiomeWorldgen config, BlockPos.MutableBlockPos cursor,
			int x, int z, int y, int minY) {
		cursor.set(x, y, z);
		BlockState source = chunk.getBlockState(cursor);
		if (!replaceable(source)) return false;
		Surface surface = config.surfaces.get(world.getBiome(cursor));
		if (surface == null) return false;
		cursor.set(x, y + 1, z);
		boolean underwater = !chunk.getBlockState(cursor).getFluidState().isEmpty();
		BlockState top = underwater && surface.underwater != null
				? surface.underwater : surface.top;
		boolean changed = false;
		cursor.set(x, y, z);
		if (top != null) {
			chunk.setBlockState(cursor, top, false);
			changed = true;
		}
		if (surface.filler != null) {
			for (int depth = 1; depth <= surface.fillerDepth && y - depth >= minY; depth++) {
				cursor.set(x, y - depth, z);
				if (!replaceable(chunk.getBlockState(cursor))) break;
				chunk.setBlockState(cursor, surface.filler, false);
				changed = true;
			}
		}
		return changed;
	}

	private static boolean applyCeiling(ChunkAccess chunk, WorldGenLevel world,
			BakedBiomeWorldgen config, BlockPos.MutableBlockPos cursor,
			int x, int z, int ceilingY) {
		cursor.set(x, ceilingY, z);
		BlockState source = chunk.getBlockState(cursor);
		if (!replaceable(source)) return false;
		Surface surface = config.surfaces.get(world.getBiome(cursor));
		if (surface == null || surface.ceiling == null) return false;
		chunk.setBlockState(cursor, surface.ceiling, false);
		return true;
	}

	private static boolean open(BlockState state) {
		return state.isAir() || !state.getFluidState().isEmpty();
	}

	private static long pack(int high, int low) {
		return ((long) high << 32) | (low & 0xFFFFFFFFL);
	}

	private static boolean replaceable(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity();
	}
}
