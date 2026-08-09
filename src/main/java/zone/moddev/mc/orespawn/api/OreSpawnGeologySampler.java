package zone.moddev.mc.orespawn.api;

import java.util.Locale;
import java.util.Optional;

import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;
import zone.moddev.mc.orespawn.worldgen.BakedGeomeConfig;
import zone.moddev.mc.orespawn.worldgen.Geology;
import zone.moddev.mc.orespawn.worldgen.GeomeConfig;
import zone.moddev.mc.orespawn.worldgen.GeomeGeology;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;
import zone.moddev.mc.orespawn.worldgen.WorldIds;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.world.server.ServerWorld;

final class OreSpawnGeologySampler implements GeologySampler {
	private static final ResourceLocation CYANO_GEOME = new ResourceLocation("orespawn", "cyano");

	private final ServerWorld level;
	private final ResourceLocation dimension;
	private final BakedGeomeConfig config;
	private final GeologyMode mode;
	private final GeomeGeology sky;
	private final Geology cyano;

	private OreSpawnGeologySampler(ServerWorld level) {
		this.level = level;
		dimension = WorldIds.dimension(level);
		config = GeomeConfig.baked(dimension);
		WorldGeologyProfile profile = WorldGeologyProfileManager.activeProfile();
		mode = profile.geologyMode();
		if (mode == GeologyMode.LEGACY) {
			cyano = new Geology(level.getSeed(), profile.cyanoGeomeSize(), profile.cyanoRockLayerNoise(),
					profile.cyanoLayerThickness(), config);
			sky = null;
		} else {
			sky = new GeomeGeology(level.getSeed(), config);
			cyano = null;
		}
	}

	static GeologySampler create(ServerWorld level) {
		if (level == null || WorldGeologyProfileManager.activeServer() != level.getServer()) {
			throw new IllegalStateException("The level is not part of OreSpawn's active server");
		}
		return new OreSpawnGeologySampler(level);
	}

	@Override
	public GeologyColumn sampleColumn(int blockX, int blockZ, int surfaceY) {
		BlockPos position = new BlockPos(blockX, surfaceY, blockZ);
		Biome biome = level.getBiome(position);
		ResourceLocation biomeId = WorldIds.biome(biome);
		if (biomeId == null) biomeId = new ResourceLocation("orespawn", "unregistered_biome");
		if (mode == GeologyMode.LEGACY) {
			return new CyanoColumn(biomeId, blockX, blockZ, surfaceY);
		}
		GeomeGeology.ColumnSample sample = sky.sampleColumn(biome, biomeId, blockX, blockZ);
		return new SkyColumn(biomeId, blockX, blockZ, surfaceY, sample);
	}

	private abstract class BaseColumn implements GeologyColumn {
		private final ResourceLocation biome;
		private final int x;
		private final int z;
		private final int surfaceY;

		BaseColumn(ResourceLocation biome, int x, int z, int surfaceY) {
			this.biome = biome;
			this.x = x;
			this.z = z;
			this.surfaceY = surfaceY;
		}

		@Override public ResourceLocation dimension() { return dimension; }
		@Override public ResourceLocation biome() { return biome; }
		@Override public int blockX() { return x; }
		@Override public int blockZ() { return z; }
		@Override public int surfaceY() { return surfaceY; }
	}

	private final class SkyColumn extends BaseColumn {
		private final GeomeGeology.ColumnSample sample;

		SkyColumn(ResourceLocation biome, int x, int z, int surfaceY, GeomeGeology.ColumnSample sample) {
			super(biome, x, z, surfaceY);
			this.sample = sample;
		}

		@Override public ResourceLocation geome() { return geomeId(sample.geomeName()); }
		@Override public BlockState rockAt(int y) { return sample.rockAt(y); }
		@Override public Optional<GeologyFamily> familyAt(int y) { return family(sample.familyAt(y)); }
	}

	private final class CyanoColumn extends BaseColumn {
		CyanoColumn(ResourceLocation biome, int x, int z, int surfaceY) {
			super(biome, x, z, surfaceY);
		}

		@Override public ResourceLocation geome() { return CYANO_GEOME; }
		@Override public BlockState rockAt(int y) { return cyano.getStoneAt(blockX(), y, blockZ()).getDefaultState(); }
		@Override public Optional<GeologyFamily> familyAt(int y) {
			Block block = rockAt(y).getBlock();
			for (RockFamily candidate : RockFamily.values()) {
				for (BlockState state : config.statesForFamily(candidate)) {
					if (state.getBlock() == block) {
						return family(candidate);
					}
				}
			}
			return Optional.empty();
		}
	}

	private static Optional<GeologyFamily> family(RockFamily family) {
		return family == null ? Optional.empty()
				: Optional.of(GeologyFamily.valueOf(family.name()));
	}

	private static ResourceLocation geomeId(String name) {
		try {
			return name.indexOf(':') >= 0 ? new ResourceLocation(name)
					: new ResourceLocation("orespawn", name.toLowerCase(Locale.ROOT));
		} catch (RuntimeException ignored) {
			return new ResourceLocation("orespawn", "unknown");
		}
	}
}
