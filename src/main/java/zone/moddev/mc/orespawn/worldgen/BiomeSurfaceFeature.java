package zone.moddev.mc.orespawn.worldgen;

import java.util.function.Supplier;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Surface;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.ISeedReader;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraftforge.common.world.BiomeGenerationSettingsBuilder;
import net.minecraftforge.event.world.BiomeLoadingEvent;

/** Applies provider surfaces after base surfaces and lakes, before late features. */
public final class BiomeSurfaceFeature extends ContextFeature<NoFeatureConfig> {
	public static final BiomeSurfaceFeature FEATURE = new BiomeSurfaceFeature();
	private static ConfiguredFeature<?, ?> configuredFeature;

	private BiomeSurfaceFeature() {
		super(NoFeatureConfig.CODEC);
		setRegistryName(OreSpawn.MODID, "biome_surfaces");
	}

	public static void registerConfiguredFeature() {
		ResourceLocation id = new ResourceLocation(OreSpawn.MODID, "biome_surfaces");
		configuredFeature = Registry.register(WorldGenRegistries.CONFIGURED_FEATURE, id,
				FEATURE.configured(NoFeatureConfig.INSTANCE)
						.decorated(Placement.NOPE.configured(NoPlacementConfig.INSTANCE)));
	}

	public static void onBiomeLoading(BiomeLoadingEvent event) {
		install(event.getGeneration());
	}

	static boolean install(BiomeGenerationSettingsBuilder generation) {
		if (configuredFeature == null) return false;
		java.util.List<Supplier<ConfiguredFeature<?, ?>>> features = generation.getFeatures(
				GenerationStage.Decoration.LOCAL_MODIFICATIONS);
		if (features.stream().anyMatch(existing -> existing.get() == configuredFeature)) {
			return false;
		}
		features.add(() -> configuredFeature);
		return true;
	}

	static ConfiguredFeature<?, ?> configuredFeature() {
		return configuredFeature;
	}

	@Override
	boolean place(FeaturePlaceContext<NoFeatureConfig> context) {
		ISeedReader world = context.level();
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(world.getLevel().dimension());
		if (config == null || !config.hasSurfaces()) return false;
		IChunk chunk = world.getChunk(context.origin());
		BlockPos.Mutable cursor = new BlockPos.Mutable();
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
							256, 0);
					ceilingY = (int) (column >> 32);
					groundY = (int) column;
				} else {
					groundY = findOpenGround(chunk, cursor, x, z, localX, localZ,
							0);
				}
				if (groundY >= 0) {
					changed |= applyGround(chunk, world, config, cursor, x, z,
							groundY, 0);
				}
				if (ceilingY >= 0) {
					changed |= applyCeiling(chunk, world, config, cursor, x, z, ceilingY);
				}
			}
		}
		return changed;
	}

	private static int findOpenGround(IChunk chunk, BlockPos.Mutable cursor,
			int x, int z, int localX, int localZ, int minY) {
		int y = chunk.getHeight(Heightmap.Type.WORLD_SURFACE_WG, localX, localZ);
		while (y >= minY && open(chunk.getBlockState(cursor.set(x, y, z)))) y--;
		return y;
	}

	private static long findCeilingAndGround(IChunk chunk,
			BlockPos.Mutable cursor, int x, int z, int maxY, int minY) {
		int y = maxY - 1;
		while (y >= minY && open(chunk.getBlockState(cursor.set(x, y, z)))) y--;
		if (y < minY) return pack(Integer.MIN_VALUE, Integer.MIN_VALUE);
		while (y >= minY && !open(chunk.getBlockState(cursor.set(x, y, z)))) y--;
		int ceilingY = y + 1;
		while (y >= minY && open(chunk.getBlockState(cursor.set(x, y, z)))) y--;
		return pack(ceilingY, y);
	}

	private static boolean applyGround(IChunk chunk, ISeedReader world,
			BakedBiomeWorldgen config, BlockPos.Mutable cursor,
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

	private static boolean applyCeiling(IChunk chunk, ISeedReader world,
			BakedBiomeWorldgen config, BlockPos.Mutable cursor,
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
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasTileEntity();
	}
}
