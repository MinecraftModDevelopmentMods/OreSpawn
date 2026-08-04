package zone.moddev.mc.orespawn.testmod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import zone.moddev.mc.orespawn.api.BiomePlacementMode;
import zone.moddev.mc.orespawn.api.BiomeRegionSize;
import zone.moddev.mc.orespawn.api.BiomeReplacementScope;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.ProviderStatus;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import zone.moddev.mc.orespawn.api.WorldgenProvider.BiomeSurfaceDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test-only provider mod which exercises the same custom-biome path used by
 * CakeWorld. This source set is excluded from every published OreSpawn jar.
 */
@Mod(CakeWorldBiomeIntegrationTestMod.MODID)
public final class CakeWorldBiomeIntegrationTestMod {
	static final String MODID = "cakeworldprobe";

	private static final Logger LOGGER = LogManager.getLogger();
	private static final Identifier DIMENSION = Identifier.parse("minecraft:the_nether");
	private static final Identifier BIOME = Identifier.parse(MODID + ":cake_plains");
	private static final Identifier PINK_CONCRETE = Identifier.parse("minecraft:pink_concrete");
	private static final Identifier WHITE_CONCRETE = Identifier.parse("minecraft:white_concrete");
	private static final int MINIMUM_CHUNK = 63;
	private static final int MAXIMUM_CHUNK = 65;
	private static final String PHASE_PROPERTY = "cakeworld.biomeIntegrationPhase";
	private static final String MARKER_NAME = "cakeworld-biome-integration.properties";

	public CakeWorldBiomeIntegrationTestMod(IEventBus modBus) {
		modBus.addListener(this::enqueueProvider);
		NeoForge.EVENT_BUS.addListener(this::auditGeneratedBiome);
	}

	private void enqueueProvider(InterModEnqueueEvent event) {
		BiomeSurfaceDefinition surface = BiomeSurfaceDefinition.builder()
				.topBlock(PINK_CONCRETE)
				.fillerBlock(WHITE_CONCRETE)
				.fillerDepth(3)
				.build();
		WorldgenProvider provider = WorldgenProvider.builder(MODID, 1)
				.biomePalette(Identifier.parse(MODID + ":normal_terrain"), DIMENSION,
						palette -> palette
								.mode(BiomePlacementMode.REPLACE)
								.scope(BiomeReplacementScope.MINECRAFT_ONLY)
								.regionSize(BiomeRegionSize.TINY)
								.coverage(1.0D)
								.fallbackWeight(0.0D)
								.biome(BIOME, biome -> biome
										.weight(1.0D)
										.temperature(-2.0D, 2.0D)
										.downfall(0.0D, 1.0D)
										.surface(surface)))
				.build();
		if (!OreSpawnApi.enqueue(provider)) {
			throw new IllegalStateException("Could not enqueue CakeWorld biome integration provider");
		}
	}

	private void auditGeneratedBiome(ServerStartedEvent event) {
		String phase = System.getProperty(PHASE_PROPERTY, "").trim();
		if (!phase.equals("fresh") && !phase.equals("reload")) {
			throw new IllegalStateException("Missing or invalid " + PHASE_PROPERTY + ": " + phase);
		}
		if (OreSpawnApi.getProviderStatus(MODID) != ProviderStatus.ACTIVE) {
			throw new IllegalStateException("CakeWorld biome integration provider is not active");
		}

		ServerLevel level = event.getServer().getLevel(Level.NETHER);
		if (level == null) {
			throw new IllegalStateException("Biome integration dimension is unavailable: " + DIMENSION);
		}
		if (level.getChunkSource().getGenerator() instanceof FlatLevelSource) {
			throw new IllegalStateException("Biome integration test requires normal noise terrain");
		}

		Path marker = event.getServer().getWorldPath(LevelResource.ROOT).resolve(MARKER_NAME);
		Properties previous = phase.equals("reload") ? readMarker(marker) : null;
		if (phase.equals("fresh") && Files.exists(marker)) {
			throw new IllegalStateException("Fresh biome integration world retained a reload marker");
		}

		AuditResult result = auditChunks(level);
		if (previous != null) {
			assertReloadValue(previous, "seed", level.getSeed());
			assertReloadValue(previous, "matching_chunks", result.matchingChunks());
			assertReloadValue(previous, "pink_surface", result.pinkSurface());
			assertReloadValue(previous, "white_filler", result.whiteFiller());
			previous.setProperty("reload_verified", "true");
			writeMarker(marker, previous);
		} else {
			writeMarker(marker, level.getSeed(), result);
		}

		LOGGER.info("CAKEWORLD_BIOME_INTEGRATION PASS phase={} biome={} chunks={} "
				+ "pink_surface={} white_filler={} temperature={} downfall={}",
				phase, BIOME, result.matchingChunks(), result.pinkSurface(), result.whiteFiller(),
				result.temperature(), result.downfall());
	}

	private static AuditResult auditChunks(ServerLevel level) {
		int matchingChunks = 0;
		long pinkSurface = 0L;
		long whiteFiller = 0L;
		float temperature = Float.NaN;
		float downfall = Float.NaN;
		BlockPos.MutableBlockPos center = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();

		for (int chunkZ = MINIMUM_CHUNK; chunkZ <= MAXIMUM_CHUNK; chunkZ++) {
			for (int chunkX = MINIMUM_CHUNK; chunkX <= MAXIMUM_CHUNK; chunkX++) {
				level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				center.set((chunkX << 4) + 8, level.getSeaLevel(), (chunkZ << 4) + 8);
				var biome = level.getBiome(center);
				Identifier actual = biome.unwrapKey().map(key -> key.identifier()).orElse(null);
				if (!BIOME.equals(actual)) {
					throw new IllegalStateException("Expected " + BIOME + " at chunk "
							+ chunkX + "," + chunkZ + " but found " + actual);
				}
				matchingChunks++;
				if (Float.isNaN(temperature)) {
					temperature = biome.value().getModifiedClimateSettings().temperature();
					downfall = biome.value().getModifiedClimateSettings().downfall();
				}

				for (int localZ = 0; localZ < 16; localZ++) {
					for (int localX = 0; localX < 16; localX++) {
						int surfaceY = chunk.getHeight(
								Heightmap.Types.WORLD_SURFACE_WG, localX, localZ) - 1;
						int blockX = (chunkX << 4) + localX;
						int blockZ = (chunkZ << 4) + localZ;
						if (chunk.getBlockState(block.set(blockX, surfaceY, blockZ))
								.is(Blocks.CONCRETE.pick(DyeColor.PINK))) {
							pinkSurface++;
						}
						for (int depth = 1; depth <= 3; depth++) {
							if (chunk.getBlockState(block.set(blockX, surfaceY - depth, blockZ))
									.is(Blocks.CONCRETE.pick(DyeColor.WHITE))) {
								whiteFiller++;
							}
						}
					}
				}
			}
		}

		int expectedChunks = (MAXIMUM_CHUNK - MINIMUM_CHUNK + 1)
				* (MAXIMUM_CHUNK - MINIMUM_CHUNK + 1);
		if (matchingChunks != expectedChunks || pinkSurface == 0L || whiteFiller == 0L) {
			throw new IllegalStateException("Incomplete custom-biome generation: chunks="
					+ matchingChunks + ", pink=" + pinkSurface + ", white=" + whiteFiller);
		}
		if (Float.compare(temperature, 1.35F) != 0 || Float.compare(downfall, 0.15F) != 0) {
			throw new IllegalStateException("Custom biome climate was not loaded: temperature="
					+ temperature + ", downfall=" + downfall);
		}
		return new AuditResult(matchingChunks, pinkSurface, whiteFiller, temperature, downfall);
	}

	private static Properties readMarker(Path marker) {
		if (!Files.isRegularFile(marker)) {
			throw new IllegalStateException("Reload phase did not reuse the fresh test world: " + marker);
		}
		Properties values = new Properties();
		try (InputStream input = Files.newInputStream(marker)) {
			values.load(input);
			return values;
		} catch (IOException exception) {
			throw new IllegalStateException("Could not read biome integration marker", exception);
		}
	}

	private static void writeMarker(Path marker, long seed, AuditResult result) {
		Properties values = new Properties();
		values.setProperty("seed", Long.toString(seed));
		values.setProperty("matching_chunks", Integer.toString(result.matchingChunks()));
		values.setProperty("pink_surface", Long.toString(result.pinkSurface()));
		values.setProperty("white_filler", Long.toString(result.whiteFiller()));
		writeMarker(marker, values);
	}

	private static void writeMarker(Path marker, Properties values) {
		try (OutputStream output = Files.newOutputStream(marker)) {
			values.store(output, "OreSpawn custom-biome integration test");
		} catch (IOException exception) {
			throw new IllegalStateException("Could not write biome integration marker", exception);
		}
	}

	private static void assertReloadValue(Properties previous, String name, long actual) {
		long expected;
		try {
			expected = Long.parseLong(previous.getProperty(name, ""));
		} catch (NumberFormatException exception) {
			throw new IllegalStateException("Invalid biome integration marker value: " + name, exception);
		}
		if (expected != actual) {
			throw new IllegalStateException("Reloaded biome integration value changed for " + name
					+ ": expected " + expected + " but found " + actual);
		}
	}

	private record AuditResult(int matchingChunks, long pinkSurface, long whiteFiller,
			float temperature, float downfall) {
	}
}
