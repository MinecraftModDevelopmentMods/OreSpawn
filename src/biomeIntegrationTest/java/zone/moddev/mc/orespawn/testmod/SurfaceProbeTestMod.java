package zone.moddev.mc.orespawn.testmod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import zone.moddev.mc.orespawn.api.BiomePlacementMode;
import zone.moddev.mc.orespawn.api.BiomeRegionSize;
import zone.moddev.mc.orespawn.api.BiomeReplacementScope;
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.OreHeightDistribution;
import zone.moddev.mc.orespawn.api.OrePattern;
import zone.moddev.mc.orespawn.api.ProviderStatus;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import zone.moddev.mc.orespawn.api.WorldgenProvider.BiomeSurfaceDefinition;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Independent, test-only provider surface fixture. */
@Mod(SurfaceProbeTestMod.MODID)
public final class SurfaceProbeTestMod {
	static final String MODID = "surfaceprobe";

	private static final Logger LOGGER = LogManager.getLogger();
	private static final DeferredRegister<Feature<?>> FEATURES =
			DeferredRegister.create(ForgeRegistries.FEATURES, MODID);
	private static final ResourceKey<Level> OPEN = Level.END;
	private static final ResourceKey<Level> ROOFED = Level.NETHER;
	private static final Identifier OPEN_ID = Identifier.parse("minecraft:the_end");
	private static final Identifier ROOFED_ID = Identifier.parse("minecraft:the_nether");
	private static final Identifier BIOME_A = Identifier.parse(MODID + ":surface_a");
	private static final Identifier BIOME_B = Identifier.parse(MODID + ":surface_b");
	private static final Identifier PROBE_GEOME = Identifier.parse(MODID + ":dynamic_biome_geome");
	private static final Identifier PROBE_GEOME_ALTERNATIVE =
			Identifier.parse(MODID + ":dynamic_biome_geome_alternative");
	private static final Identifier DYNAMIC_FLUID = Identifier.parse(MODID + ":fluid/dynamic_water");
	private static final Identifier DYNAMIC_ORE =
			Identifier.parse(MODID + ":ore/dynamic_biome_filter");
	private static final Block[] NATURAL_SOURCES = {
			Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.PODZOL,
			Blocks.ROOTED_DIRT, Blocks.GRAVEL, Blocks.SAND, Blocks.RED_SAND,
			Blocks.CLAY, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA,
			Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA
	};
	private static final Block[] INVALID_TERRAIN_HOSTS = {
			Blocks.AIR, Blocks.WATER, Blocks.BEDROCK, Blocks.CHEST
	};
	private static final Identifier[] BUILT_IN_GEOMES = {
			Identifier.parse("orespawn:stable_craton"), Identifier.parse("orespawn:mountain_belt"),
			Identifier.parse("orespawn:volcanic_arc"), Identifier.parse("orespawn:sedimentary_basin"),
			Identifier.parse("orespawn:coastal_shelf"), Identifier.parse("orespawn:arid_basin"),
			Identifier.parse("orespawn:wetland_basin"), Identifier.parse("orespawn:glacial_highland")
	};
	private static final int MINIMUM_CHUNK = 63;
	private static final int MAXIMUM_CHUNK = 65;
	private static final int EXPECTED_COLUMNS = 9 * 16 * 16;
	private static final int EXPECTED_FILLER = EXPECTED_COLUMNS * 3;
	private static final int EXPECTED_NATURAL_SOURCES = 9 * NATURAL_SOURCES.length;
	private static final String PHASE_PROPERTY = "surfaceprobe.integrationPhase";
	private static final String MARKER_NAME = "surfaceprobe-integration.properties";
	private static final String CHEST_ITEM_NAME = "surfaceprobe sentinel";
	private static final String RAW_CHEST_ITEM_NAME = "surfaceprobe raw block entity sentinel";

	static {
		FEATURES.register("terrain_setup", () -> new ProbeFeature(ProbeStage.TERRAIN));
		FEATURES.register("structure_sentinels", () -> new ProbeFeature(ProbeStage.STRUCTURE));
		FEATURES.register("vegetation_sentinels", () -> new ProbeFeature(ProbeStage.VEGETATION));
	}

	public SurfaceProbeTestMod(FMLJavaModLoadingContext context) {
		BusGroup modBusGroup = context.getModBusGroup();
		FEATURES.register(modBusGroup);
		InterModEnqueueEvent.getBus(modBusGroup).addListener(this::enqueueProvider);
		ServerAboutToStartEvent.BUS.addListener(this::enableGeologyProbe);
		ServerStartedEvent.BUS.addListener(this::auditGeneratedSurfaces);
	}

	private void enqueueProvider(InterModEnqueueEvent event) {
		WorldgenProvider.Builder provider = WorldgenProvider.builder(MODID, 1);
		addDynamicBiomeGeology(provider);
		provider.ore(DYNAMIC_ORE, blockId(Blocks.DIAMOND_BLOCK), ore -> ore
				.retrogen(false)
				.dimension(OPEN_ID, placement -> placement
						.yRange(16, 48)
						.attempts(16.0D)
						.quantity(8)
						.pattern(OrePattern.CLUSTER)
						.heightDistribution(OreHeightDistribution.UNIFORM)
						.discardChanceOnAirExposure(0.0D)
						.spread(4, 3)
						.nodeSize(3)
						.hostBlock(blockId(Blocks.CALCITE))
						.hostBlock(blockId(Blocks.BASALT))
						.biome(BIOME_A)
						.biomeDictionary("COLD")
						.excludeBiome(BIOME_B)
						.excludeBiomeDictionary("SPOOKY")));
		provider.fluidDeposit(DYNAMIC_FLUID, blockId(Blocks.WATER), deposit -> deposit
				.dimension(OPEN_ID, placement -> placement
						.yRange(16, 24)
						.attempts(12.0D)
						.radius(1, 1)
						.verticalRadius(1, 1)
						.maxLobes(1)
						.minSolidCover(1)
						.minSolidShell(1)
						.hostBlock(blockId(Blocks.CALCITE))));
		addPalette(provider, "open_palette", OPEN_ID, false);
		addPalette(provider, "roofed_palette", ROOFED_ID, true);
		provider.dimensionMaterials(Identifier.parse(MODID + ":materials/nether"), ROOFED_ID,
				materials -> materials.defaultFluid(blockId(Blocks.WATER)));
		if (!OreSpawnApi.enqueue(provider.build())) {
			throw new IllegalStateException("Could not enqueue surface probe provider");
		}
	}

	private static void addDynamicBiomeGeology(WorldgenProvider.Builder provider) {
		provider.geome(PROBE_GEOME, geome -> geome
				.baseWeight(0.0D)
				.familyWeight(GeologyFamily.SEDIMENTARY, 1.0D));
		provider.geome(PROBE_GEOME_ALTERNATIVE, geome -> geome
				.baseWeight(0.0D)
				.familyWeight(GeologyFamily.SEDIMENTARY, 1.0D));
		provider.rock(Identifier.parse(MODID + ":rock/dynamic_biome"), blockId(Blocks.CALCITE),
				GeologyFamily.SEDIMENTARY, rock -> {
					rock.dimensions(java.util.Collections.singleton(OPEN_ID));
					rock.geomeWeight(PROBE_GEOME, 1.0D);
					rock.geomeWeight(PROBE_GEOME_ALTERNATIVE, 0.0D);
					for (Identifier geome : BUILT_IN_GEOMES) rock.geomeWeight(geome, 0.0D);
				});
		provider.rock(Identifier.parse(MODID + ":rock/dynamic_biome_alternative"), blockId(Blocks.BASALT),
				GeologyFamily.SEDIMENTARY, rock -> {
					rock.dimensions(java.util.Collections.singleton(OPEN_ID));
					rock.geomeWeight(PROBE_GEOME, 0.0D);
					rock.geomeWeight(PROBE_GEOME_ALTERNATIVE, 1.0D);
					for (Identifier geome : BUILT_IN_GEOMES) rock.geomeWeight(geome, 0.0D);
				});
		provider.rock(Identifier.parse(MODID + ":rock/fallback"), blockId(Blocks.DEEPSLATE),
				GeologyFamily.SEDIMENTARY, rock -> {
					rock.dimensions(java.util.Collections.singleton(OPEN_ID));
					rock.geomeWeight(PROBE_GEOME, 0.0D);
					rock.geomeWeight(PROBE_GEOME_ALTERNATIVE, 0.0D);
					for (Identifier geome : BUILT_IN_GEOMES) rock.geomeWeight(geome, 1.0D);
				});
		Map<Identifier, Double> biomeAWeights = new LinkedHashMap<>();
		biomeAWeights.put(PROBE_GEOME, 6.0D);
		biomeAWeights.put(PROBE_GEOME_ALTERNATIVE, 14.0D);
		provider.biome(BIOME_A, biomeAWeights);
		provider.biome(BIOME_B, java.util.Collections.singletonMap(PROBE_GEOME, 100.0D));
	}

	private void enableGeologyProbe(ServerAboutToStartEvent event) {
		Path profile = event.getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig")
				.resolve("orespawn-worldgen.json");
		JsonObject root;
		try (var reader = Files.newBufferedReader(profile)) {
			root = new JsonParser().parse(reader).getAsJsonObject();
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Could not read the test-owned End geology profile", exception);
		}
		try {
			root.addProperty("place_fluid_deposits", true);
			root.addProperty("place_ores", true);
			JsonObject dictionary = root.getAsJsonObject("biome_dictionary");
			if (dictionary == null) {
				dictionary = new JsonObject();
				root.add("biome_dictionary", dictionary);
			}
			JsonObject cold = dictionary.getAsJsonObject("COLD");
			if (cold == null) {
				cold = new JsonObject();
				dictionary.add("COLD", cold);
			}
			cold.addProperty(PROBE_GEOME.toString(), 8.0D);
			JsonObject terrain = root.getAsJsonObject("terrain_dimensions");
			if (terrain == null) {
				terrain = new JsonObject();
				root.add("terrain_dimensions", terrain);
			}
			JsonObject end = new JsonObject();
			end.addProperty("enabled", true);
			end.add("biome_ids", new JsonArray());
			JsonArray namespaces = new JsonArray();
			namespaces.add(MODID);
			end.add("biome_namespaces", namespaces);
			JsonArray hosts = new JsonArray();
			hosts.add(blockId(Blocks.END_STONE).toString());
			for (Block source : NATURAL_SOURCES) hosts.add(blockId(source).toString());
			for (Block source : INVALID_TERRAIN_HOSTS) hosts.add(blockId(source).toString());
			end.add("host_blocks", hosts);
			end.add("host_tags", new JsonArray());
			terrain.add(OPEN_ID.toString(), end);
			try (var writer = Files.newBufferedWriter(profile)) {
				new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
			}
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Could not write the test-owned End geology profile", exception);
		}
		if (!WorldGeologyProfileManager.reloadActiveProfile()) {
			throw new IllegalStateException("Could not reload the test-owned End geology profile");
		}
	}

	private static void addPalette(WorldgenProvider.Builder provider, String name,
			Identifier dimension, boolean ceiling) {
		BiomeSurfaceDefinition surfaceA = surface(DyeColor.PINK, DyeColor.WHITE,
				DyeColor.BLUE, ceiling ? DyeColor.ORANGE : null);
		BiomeSurfaceDefinition surfaceB = surface(DyeColor.LIME, DyeColor.YELLOW,
				DyeColor.LIGHT_BLUE, ceiling ? DyeColor.MAGENTA : null);
		provider.biomePalette(Identifier.parse(MODID + ":" + name), dimension,
				palette -> palette
						.mode(BiomePlacementMode.REPLACE)
						.scope(BiomeReplacementScope.MINECRAFT_ONLY)
						.regionSize(BiomeRegionSize.TINY)
						.coverage(1.0D)
						.fallbackWeight(0.0D)
						.biome(BIOME_A, biome -> biome
								.weight(1.0D)
								.temperature(-2.0D, 2.0D)
								.downfall(0.0D, 1.0D)
								.surface(surfaceA))
						.biome(BIOME_B, biome -> biome
								.weight(1.0D)
								.temperature(-2.0D, 2.0D)
								.downfall(0.0D, 1.0D)
								.surface(surfaceB)));
	}

	private static BiomeSurfaceDefinition surface(DyeColor top, DyeColor filler,
			DyeColor underwater, DyeColor ceiling) {
		BiomeSurfaceDefinition.Builder builder = BiomeSurfaceDefinition.builder()
				.topBlock(blockId(concreteBlock(top)))
				.fillerBlock(blockId(concreteBlock(filler)))
				.fillerDepth(3)
				.underwaterBlock(blockId(concreteBlock(underwater)));
		if (ceiling != null) {
			builder.ceilingBlock(blockId(concreteBlock(ceiling)));
		}
		return builder.build();
	}

	private static Identifier blockId(Block block) {
		return ForgeRegistries.BLOCKS.getKey(block);
	}

	private void auditGeneratedSurfaces(ServerStartedEvent event) {
		String phase = System.getProperty(PHASE_PROPERTY, "").trim();
		if (!phase.equals("fresh") && !phase.equals("reload")) {
			throw new IllegalStateException("Missing or invalid " + PHASE_PROPERTY + ": " + phase);
		}
		if (OreSpawnApi.getProviderStatus(MODID) != ProviderStatus.ACTIVE) {
			throw new IllegalStateException("Surface probe provider is not active");
		}

		Path marker = event.getServer().getWorldPath(LevelResource.ROOT).resolve(MARKER_NAME);
		Properties previous = phase.equals("reload") ? readMarker(marker) : null;
		if (phase.equals("fresh") && Files.exists(marker)) {
			throw new IllegalStateException("Fresh surface probe retained a reload marker");
		}

		Map<String, AuditResult> results = new LinkedHashMap<>();
		results.put("open", auditDimension(requireLevel(event, OPEN), false));
		results.put("roofed", auditDimension(requireLevel(event, ROOFED), true));
		Properties current = properties(event.getServer().overworld().getSeed(), results);
		if (previous == null) {
			writeMarker(marker, current);
		} else {
			for (String name : current.stringPropertyNames()) {
				String expected = previous.getProperty(name);
				String actual = current.getProperty(name);
				if (!actual.equals(expected)) {
					throw new IllegalStateException("Reloaded surface value changed for " + name
							+ ": expected " + expected + " but found " + actual);
				}
			}
			previous.setProperty("reload_verified", "true");
			writeMarker(marker, previous);
		}

		LOGGER.info("SURFACEPROBE PASS phase={} open={} roofed={}",
				phase, results.get("open"), results.get("roofed"));
	}

	private static ServerLevel requireLevel(ServerStartedEvent event, ResourceKey<Level> key) {
		ServerLevel level = event.getServer().getLevel(key);
		if (level == null) throw new IllegalStateException("Surface probe dimension is unavailable: " + key.identifier());
		if (level.getChunkSource().getGenerator() instanceof FlatLevelSource) {
			throw new IllegalStateException("Surface probe requires normal noise terrain: " + key.identifier());
		}
		return level;
	}

	private static AuditResult auditDimension(ServerLevel level, boolean roofed) {
		long top = 0L;
		long underwater = 0L;
		long filler = 0L;
		long geology = 0L;
		long ceiling = 0L;
		long roofTop = 0L;
		int biomeA = 0;
		int biomeB = 0;
		int edgeChanges = 0;
		int sentinels = 0;
		long rawNaturalSources = 0L;
		long structureNaturalSources = 0L;
		long vegetationNaturalSources = 0L;
		long cavePockets = 0L;
		long underwaterPockets = 0L;
		long rawBedrock = 0L;
		long rawBlockEntities = 0L;
		long dictionaryPrimary = 0L;
		long dictionaryAlternative = 0L;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int chunkZ = MINIMUM_CHUNK; chunkZ <= MAXIMUM_CHUNK; chunkZ++) {
			Identifier previousChunkBiome = null;
			for (int chunkX = MINIMUM_CHUNK; chunkX <= MAXIMUM_CHUNK; chunkX++) {
				level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				int chunkMinX = chunkX << 4;
				int chunkMinZ = chunkZ << 4;
				int centerGroundY = findMarkedGround(chunk, pos, chunkMinX + 8, chunkMinZ + 8,
						level.getMinY(), level.getMaxY());
				Identifier generationBiomeId = biomeId(level.getBiome(
						pos.set(chunkMinX + 8, centerGroundY, chunkMinZ + 8)));
				for (int localZ = 0; localZ < 16; localZ++) {
					for (int localX = 0; localX < 16; localX++) {
						int x = chunkMinX + localX;
						int z = chunkMinZ + localZ;
						int groundY = findMarkedGround(chunk, pos, x, z, level.getMinY(), level.getMaxY());
						var biome = level.getBiome(pos.set(x, groundY, z));
						Identifier biomeId = biomeId(biome);
						Material material = material(biomeId, roofed);
						float expectedTemperature = BIOME_A.equals(biomeId) ? 1.35F : 0.7F;
						float expectedDownfall = BIOME_A.equals(biomeId) ? 0.15F : 0.8F;
						var climate = biome.value().getModifiedClimateSettings();
						if (Float.compare(climate.temperature(), expectedTemperature) != 0
								|| Float.compare(climate.downfall(), expectedDownfall) != 0) {
							throw new IllegalStateException("Provider climate changed for " + biomeId
									+ " at " + pos + ": " + climate);
						}
						if (BIOME_A.equals(biomeId)) biomeA++; else biomeB++;
						boolean waterColumn = localX == 1 && localZ == 1;
						BlockState expectedTop = waterColumn ? material.underwater() : material.top();
						assertBlock(chunk, pos, x, groundY, z, expectedTop,
								"provider top at exposed marked ground");
						if (waterColumn) underwater++; else top++;
						for (int depth = 1; depth <= 3; depth++) {
							assertBlock(chunk, pos, x, groundY - depth, z, material.filler(),
									"provider filler depth " + depth);
							filler++;
						}
						if (!roofed) {
							for (int depth = 6; depth <= 8; depth++) {
								BlockState geologyState = chunk.getBlockState(pos.set(x, groundY - depth, z));
								if (geologyState.is(Blocks.BASALT)) {
									dictionaryAlternative++;
								} else if (geologyState.is(Blocks.CALCITE)) {
									dictionaryPrimary++;
								} else {
									throw new IllegalStateException("Unexpected dynamic-biome geome rock at "
											+ pos + " in " + biomeId + ": " + geologyState);
								}
								geology++;
							}
						}
						if (roofed) {
							Identifier ceilingBiome = biomeId(level.getBiome(
									pos.set(x, groundY + 8, z)));
							BlockState expectedCeiling = material(ceilingBiome, true).ceiling();
							assertBlock(chunk, pos, x, groundY + 8, z, expectedCeiling,
									"roof underside");
							assertBlock(chunk, pos, x, groundY + 10, z, Blocks.STONE.defaultBlockState(),
									"roof top");
							ceiling++;
							roofTop++;
						}
					}
				}
				if (previousChunkBiome != null && !previousChunkBiome.equals(generationBiomeId)) edgeChanges++;
				previousChunkBiome = generationBiomeId;
				sentinels += auditSentinels(level, chunk, pos, chunkMinX, chunkMinZ);
				if (!roofed) {
					NaturalSourceAudit natural = auditNaturalSources(level, chunk, pos,
							chunkMinX, chunkMinZ);
					rawNaturalSources += natural.rawConverted();
					structureNaturalSources += natural.structurePreserved();
					vegetationNaturalSources += natural.vegetationPreserved();
					cavePockets += natural.cavePreserved();
					underwaterPockets += natural.underwaterPreserved();
					rawBedrock += natural.bedrockPreserved();
					rawBlockEntities += natural.blockEntityPreserved();
				}
			}
		}

		long dynamicBiomeOre = roofed ? 0L : auditDynamicBiomeOre(level);
		if (top != EXPECTED_COLUMNS - 9 || underwater != 9 || filler != EXPECTED_FILLER
				|| biomeA == 0 || biomeB == 0 || edgeChanges == 0 || sentinels != 9 * 4
				|| geology != (roofed ? 0 : EXPECTED_FILLER)
				|| (roofed && (ceiling != EXPECTED_COLUMNS || roofTop != EXPECTED_COLUMNS))
				|| (!roofed && (rawNaturalSources != EXPECTED_NATURAL_SOURCES
						|| structureNaturalSources != EXPECTED_NATURAL_SOURCES
						|| vegetationNaturalSources != EXPECTED_NATURAL_SOURCES
						|| cavePockets != 54 || underwaterPockets != 63
						|| rawBedrock != 9 || rawBlockEntities != 9
						|| dictionaryPrimary != EXPECTED_FILLER || dictionaryAlternative != 0
						|| dynamicBiomeOre == 0))) {
			throw new IllegalStateException("Incomplete surface audit for " + level.dimension().identifier()
					+ ": top=" + top + ", underwater=" + underwater + ", filler=" + filler
					+ ", biomeA=" + biomeA + ", biomeB=" + biomeB + ", edges=" + edgeChanges
					+ ", sentinels=" + sentinels + ", geology=" + geology
					+ ", ceiling=" + ceiling + ", roofTop=" + roofTop
					+ ", rawNatural=" + rawNaturalSources
					+ ", structureNatural=" + structureNaturalSources
					+ ", vegetationNatural=" + vegetationNaturalSources
					+ ", cavePockets=" + cavePockets
					+ ", underwaterPockets=" + underwaterPockets
					+ ", rawBedrock=" + rawBedrock
					+ ", rawBlockEntities=" + rawBlockEntities
					+ ", dictionaryPrimary=" + dictionaryPrimary
					+ ", dictionaryAlternative=" + dictionaryAlternative
					+ ", dynamicBiomeOre=" + dynamicBiomeOre);
		}
		long aquiferFluid = roofed ? 0L : auditDynamicFluid(level);
		return new AuditResult(top, underwater, filler, geology, ceiling, roofTop,
				biomeA, biomeB, edgeChanges, sentinels, aquiferFluid,
				rawNaturalSources, structureNaturalSources, vegetationNaturalSources,
				cavePockets, underwaterPockets, rawBedrock, rawBlockEntities,
				dictionaryPrimary, dictionaryAlternative, dynamicBiomeOre);
	}

	private static long auditDynamicBiomeOre(ServerLevel level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		long count = 0L;
		for (int chunkZ = MINIMUM_CHUNK; chunkZ <= MAXIMUM_CHUNK; chunkZ++) {
			for (int chunkX = MINIMUM_CHUNK; chunkX <= MAXIMUM_CHUNK; chunkX++) {
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
					for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
						for (int y = 16; y <= 48; y++) {
							if (chunk.getBlockState(pos.set(x, y, z)).is(Blocks.DIAMOND_BLOCK)) count++;
						}
					}
				}
			}
		}
		if (count == 0L) {
			throw new IllegalStateException("Dynamic-registry biome filter produced no managed ore");
		}
		return count;
	}

	private static NaturalSourceAudit auditNaturalSources(ServerLevel level, LevelChunk chunk,
			BlockPos.MutableBlockPos pos, int minX, int minZ) {
		long rawConverted = 0L;
		long structurePreserved = 0L;
		long vegetationPreserved = 0L;
		long cavePreserved = 0L;
		long underwaterPreserved = 0L;
		long bedrockPreserved = 0L;
		long blockEntityPreserved = 0L;
		for (int index = 0; index < NATURAL_SOURCES.length; index++) {
			int x = naturalX(minX, index);
			int z = naturalZ(minZ, index);
			int groundY = findMarkedGround(chunk, pos, x, z,
					level.getMinY(), level.getMaxY());
			BlockState converted = chunk.getBlockState(pos.set(x, groundY - 12, z));
			if (converted.is(Blocks.CALCITE) || converted.is(Blocks.BASALT)) rawConverted++;
			Block pocket = chunk.getBlockState(pos.set(x, groundY - 11, z)).getBlock();
			if (index < NATURAL_SOURCES.length / 2) {
				if (pocket == Blocks.AIR) cavePreserved++;
			} else if (pocket == Blocks.WATER) {
				underwaterPreserved++;
			}
			if (chunk.getBlockState(pos.set(x, groundY - 16, z)).is(NATURAL_SOURCES[index])) {
				structurePreserved++;
			}
			if (chunk.getBlockState(pos.set(x, groundY - 20, z)).is(NATURAL_SOURCES[index])) {
				vegetationPreserved++;
			}
		}
		int bedrockGroundY = findMarkedGround(chunk, pos, minX + 11, minZ + 12,
				level.getMinY(), level.getMaxY());
		if (chunk.getBlockState(pos.set(minX + 11, bedrockGroundY - 24, minZ + 12)).is(Blocks.BEDROCK)) {
			bedrockPreserved++;
		}
		int chestGroundY = findMarkedGround(chunk, pos, minX + 12, minZ + 12,
				level.getMinY(), level.getMaxY());
		pos.set(minX + 12, chestGroundY - 24, minZ + 12);
		if (chunk.getBlockState(pos).is(Blocks.CHEST)
				&& level.getBlockEntity(pos) instanceof ChestBlockEntity chest
				&& chest.getItem(0).is(Items.EMERALD)
				&& RAW_CHEST_ITEM_NAME.equals(chest.getItem(0).getHoverName().getString())) {
			blockEntityPreserved++;
		}
		return new NaturalSourceAudit(rawConverted, structurePreserved,
				vegetationPreserved, cavePreserved, underwaterPreserved,
				bedrockPreserved, blockEntityPreserved);
	}

	private static long auditDynamicFluid(ServerLevel level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		long water = 0L;
		for (int chunkZ = MINIMUM_CHUNK; chunkZ <= MAXIMUM_CHUNK; chunkZ++) {
			for (int chunkX = MINIMUM_CHUNK; chunkX <= MAXIMUM_CHUNK; chunkX++) {
				level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
					for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
						for (int y = 12; y <= 30; y++) {
							if (chunk.getBlockState(pos.set(x, y, z)).is(Blocks.WATER)) water++;
						}
					}
				}
			}
		}
		if (water == 0L) {
			throw new IllegalStateException("Forge 61 dynamic fluid deposit produced no covered flowing-water blocks");
		}
		return water;
	}

	private static int auditSentinels(ServerLevel level, LevelChunk chunk,
			BlockPos.MutableBlockPos pos, int minX, int minZ) {
		int groundTree = findMarkedGround(chunk, pos, minX + 4, minZ + 4,
				level.getMinY(), level.getMaxY());
		assertBlock(chunk, pos, minX + 4, groundTree + 1, minZ + 4,
				Blocks.OAK_LOG.defaultBlockState(), "tree log");
		assertBlock(chunk, pos, minX + 4, groundTree + 3, minZ + 4,
				Blocks.OAK_LEAVES.defaultBlockState(), "tree leaves");

		int groundVegetation = findMarkedGround(chunk, pos, minX + 6, minZ + 6,
				level.getMinY(), level.getMaxY());
		assertBlock(chunk, pos, minX + 6, groundVegetation + 1, minZ + 6,
				Blocks.DIRT.defaultBlockState(), "vegetation substrate");
		assertBlock(chunk, pos, minX + 6, groundVegetation + 2, minZ + 6,
				Blocks.OAK_SAPLING.defaultBlockState(), "vegetation");

		int groundStructure = findMarkedGround(chunk, pos, minX + 8, minZ + 8,
				level.getMinY(), level.getMaxY());
		assertBlock(chunk, pos, minX + 8, groundStructure + 1, minZ + 8,
				Blocks.GOLD_BLOCK.defaultBlockState(), "authored structure");

		int groundChest = findMarkedGround(chunk, pos, minX + 10, minZ + 10,
				level.getMinY(), level.getMaxY());
		assertBlock(chunk, pos, minX + 10, groundChest + 1, minZ + 10,
				Blocks.CHEST.defaultBlockState(), "chest sentinel");
		if (!(level.getBlockEntity(pos.set(minX + 10, groundChest + 1, minZ + 10))
				instanceof ChestBlockEntity chest)
				|| !chest.getItem(0).is(Items.DIAMOND)
				|| chest.getItem(0).getHoverName() == null
				|| !CHEST_ITEM_NAME.equals(chest.getItem(0).getHoverName().getString())) {
			throw new IllegalStateException("Chest block entity data changed at " + pos);
		}
		return 4;
	}

	private static int findMarkedGround(ChunkAccess chunk, BlockPos.MutableBlockPos pos,
			int x, int z, int minY, int maxY) {
		for (int y = maxY - 1; y >= minY; y--) {
			if (chunk.getBlockState(pos.set(x, y, z)).is(concreteBlock(DyeColor.BLACK))) return y + 5;
		}
		throw new IllegalStateException("Independent surface marker missing at " + x + "," + z);
	}

	private static void assertBlock(ChunkAccess chunk, BlockPos.MutableBlockPos pos,
			int x, int y, int z, BlockState expected, String purpose) {
		BlockState actual = chunk.getBlockState(pos.set(x, y, z));
		if (!actual.is(expected.getBlock())) {
			throw new IllegalStateException("Expected " + purpose + " " + expected.getBlock()
					+ " at " + pos + " but found " + actual.getBlock());
		}
	}

	private static Material material(Identifier biome, boolean roofed) {
		if (BIOME_A.equals(biome)) {
			return new Material(concrete(DyeColor.PINK), concrete(DyeColor.WHITE),
					concrete(DyeColor.BLUE), roofed ? concrete(DyeColor.ORANGE) : null);
		}
		if (BIOME_B.equals(biome)) {
			return new Material(concrete(DyeColor.LIME), concrete(DyeColor.YELLOW),
					concrete(DyeColor.LIGHT_BLUE), roofed ? concrete(DyeColor.MAGENTA) : null);
		}
		throw new IllegalStateException("Unexpected provider biome " + biome);
	}

	private static BlockState concrete(DyeColor color) {
		return concreteBlock(color).defaultBlockState();
	}

	private static Block concreteBlock(DyeColor color) {
		return switch (color) {
			case WHITE -> Blocks.WHITE_CONCRETE;
			case ORANGE -> Blocks.ORANGE_CONCRETE;
			case MAGENTA -> Blocks.MAGENTA_CONCRETE;
			case LIGHT_BLUE -> Blocks.LIGHT_BLUE_CONCRETE;
			case YELLOW -> Blocks.YELLOW_CONCRETE;
			case LIME -> Blocks.LIME_CONCRETE;
			case PINK -> Blocks.PINK_CONCRETE;
			case GRAY -> Blocks.GRAY_CONCRETE;
			case LIGHT_GRAY -> Blocks.LIGHT_GRAY_CONCRETE;
			case CYAN -> Blocks.CYAN_CONCRETE;
			case PURPLE -> Blocks.PURPLE_CONCRETE;
			case BLUE -> Blocks.BLUE_CONCRETE;
			case BROWN -> Blocks.BROWN_CONCRETE;
			case GREEN -> Blocks.GREEN_CONCRETE;
			case RED -> Blocks.RED_CONCRETE;
			case BLACK -> Blocks.BLACK_CONCRETE;
		};
	}

	private static Identifier biomeId(net.minecraft.core.Holder<Biome> biome) {
		return biome.unwrapKey().map(key -> key.identifier()).orElse(null);
	}

	private static Properties properties(long seed, Map<String, AuditResult> results) {
		Properties values = new Properties();
		values.setProperty("seed", Long.toString(seed));
		values.setProperty("dimensions", Integer.toString(results.size()));
		values.setProperty("columns_per_dimension", Integer.toString(EXPECTED_COLUMNS));
		for (Map.Entry<String, AuditResult> entry : results.entrySet()) {
			String prefix = entry.getKey() + ".";
			AuditResult result = entry.getValue();
			values.setProperty(prefix + "top", Long.toString(result.top()));
			values.setProperty(prefix + "underwater", Long.toString(result.underwater()));
			values.setProperty(prefix + "filler", Long.toString(result.filler()));
			values.setProperty(prefix + "geology", Long.toString(result.geology()));
			values.setProperty(prefix + "ceiling", Long.toString(result.ceiling()));
			values.setProperty(prefix + "roof_top", Long.toString(result.roofTop()));
			values.setProperty(prefix + "biome_a", Integer.toString(result.biomeA()));
			values.setProperty(prefix + "biome_b", Integer.toString(result.biomeB()));
			values.setProperty(prefix + "edge_changes", Integer.toString(result.edgeChanges()));
			values.setProperty(prefix + "sentinels", Integer.toString(result.sentinels()));
			values.setProperty(prefix + "aquifer_fluid", Long.toString(result.aquiferFluid()));
			values.setProperty(prefix + "raw_natural_sources", Long.toString(result.rawNaturalSources()));
			values.setProperty(prefix + "structure_natural_sources", Long.toString(result.structureNaturalSources()));
			values.setProperty(prefix + "vegetation_natural_sources", Long.toString(result.vegetationNaturalSources()));
			values.setProperty(prefix + "cave_pockets", Long.toString(result.cavePockets()));
			values.setProperty(prefix + "underwater_pockets", Long.toString(result.underwaterPockets()));
			values.setProperty(prefix + "raw_bedrock", Long.toString(result.rawBedrock()));
			values.setProperty(prefix + "raw_block_entities", Long.toString(result.rawBlockEntities()));
			values.setProperty(prefix + "dictionary_primary", Long.toString(result.dictionaryPrimary()));
			values.setProperty(prefix + "dictionary_alternative", Long.toString(result.dictionaryAlternative()));
			values.setProperty(prefix + "dynamic_biome_ore", Long.toString(result.dynamicBiomeOre()));
		}
		return values;
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
			throw new IllegalStateException("Could not read surface integration marker", exception);
		}
	}

	private static void writeMarker(Path marker, Properties values) {
		try (OutputStream output = Files.newOutputStream(marker)) {
			values.store(output, "OreSpawn provider surface integration test");
		} catch (IOException exception) {
			throw new IllegalStateException("Could not write surface integration marker", exception);
		}
	}

	private enum ProbeStage { TERRAIN, STRUCTURE, VEGETATION }

	private static final class ProbeFeature extends Feature<NoneFeatureConfiguration> {
		private final ProbeStage stage;

		private ProbeFeature(ProbeStage stage) {
			super(NoneFeatureConfiguration.CODEC);
			this.stage = stage;
		}

		@Override
		public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
			WorldGenLevel world = context.level();
			ChunkAccess chunk = world.getChunk(context.origin());
			return switch (stage) {
				case TERRAIN -> prepareTerrain(world, chunk);
				case STRUCTURE -> placeStructureSentinels(world, chunk);
				case VEGETATION -> placeVegetationSentinels(world, chunk);
			};
		}
	}

	private static boolean prepareTerrain(WorldGenLevel world, ChunkAccess chunk) {
		boolean roofed = world.getLevel().dimension().equals(ROOFED);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Heightmap surfaceHeight = chunk.getOrCreateHeightmapUnprimed(
				Heightmap.Types.WORLD_SURFACE_WG);
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int x = minX + localX;
				int z = minZ + localZ;
				int groundY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, localX, localZ);
				if (roofed) {
					while (groundY > world.getMinY()
							&& solid(chunk.getBlockState(pos.set(x, groundY, z)))) groundY--;
				}
				while (groundY > world.getMinY()
						&& !solid(chunk.getBlockState(pos.set(x, groundY, z)))) groundY--;
				if (!roofed && groundY <= world.getMinY()) groundY = 64;
				chunk.setBlockState(pos.set(x, groundY, z), Blocks.GRASS_BLOCK.defaultBlockState(), 0);
				surfaceHeight.update(localX, groundY, localZ, Blocks.GRASS_BLOCK.defaultBlockState());
				for (int depth = 1; depth <= 3; depth++) {
					chunk.setBlockState(pos.set(x, groundY - depth, z), Blocks.DIRT.defaultBlockState(), 0);
				}
				if (!roofed) {
					for (int depth = 6; depth <= 60 && groundY - depth >= 1; depth++) {
						chunk.setBlockState(pos.set(x, groundY - depth, z), Blocks.END_STONE.defaultBlockState(), 0);
					}
				}
				chunk.setBlockState(pos.set(x, groundY - 5, z),
						concreteBlock(DyeColor.BLACK).defaultBlockState(), 0);
				if (roofed) {
					for (int openY = groundY + 1; openY < world.getMaxY(); openY++) {
						chunk.setBlockState(pos.set(x, openY, z), Blocks.AIR.defaultBlockState(), 0);
					}
					for (int roofY = groundY + 8; roofY <= groundY + 10; roofY++) {
						chunk.setBlockState(pos.set(x, roofY, z), Blocks.STONE.defaultBlockState(), 0);
					}
					surfaceHeight.update(localX, groundY + 10, localZ,
							Blocks.STONE.defaultBlockState());
				}
				if (localX == 1 && localZ == 1) {
					chunk.setBlockState(pos.set(x, groundY + 1, z), Blocks.WATER.defaultBlockState(), 0);
					surfaceHeight.update(localX, groundY + 1, localZ, Blocks.WATER.defaultBlockState());
				}
			}
		}
		if (!roofed) placeRawNaturalSources(world, chunk, pos, minX, minZ);
		return true;
	}

	private static void placeRawNaturalSources(WorldGenLevel world, ChunkAccess chunk,
			BlockPos.MutableBlockPos pos, int minX, int minZ) {
		for (int index = 0; index < NATURAL_SOURCES.length; index++) {
			int x = naturalX(minX, index);
			int z = naturalZ(minZ, index);
			int groundY = findMarkedGround(chunk, pos, x, z,
					world.getMinY(), world.getMaxY());
			chunk.setBlockState(pos.set(x, groundY - 12, z),
					NATURAL_SOURCES[index].defaultBlockState(), 0);
			chunk.setBlockState(pos.set(x, groundY - 11, z),
					(index < NATURAL_SOURCES.length / 2 ? Blocks.AIR : Blocks.WATER)
							.defaultBlockState(), 0);
		}
		int bedrockGroundY = findMarkedGround(chunk, pos, minX + 11, minZ + 12,
				world.getMinY(), world.getMaxY());
		chunk.setBlockState(pos.set(minX + 11, bedrockGroundY - 24, minZ + 12),
				Blocks.BEDROCK.defaultBlockState(), 0);
		int chestGroundY = findMarkedGround(chunk, pos, minX + 12, minZ + 12,
				world.getMinY(), world.getMaxY());
		world.setBlock(pos.set(minX + 12, chestGroundY - 24, minZ + 12),
				Blocks.CHEST.defaultBlockState(), 2);
		if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			ItemStack sentinel = new ItemStack(Items.EMERALD);
			sentinel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					Component.literal(RAW_CHEST_ITEM_NAME));
			chest.setItem(0, sentinel);
			chest.setChanged();
		}
	}

	private static boolean solid(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty();
	}

	private static boolean placeStructureSentinels(WorldGenLevel world, ChunkAccess chunk) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		int structureY = markedGround(chunk, pos, minX + 8, minZ + 8, world);
		world.setBlock(pos.set(minX + 8, structureY + 1, minZ + 8),
				Blocks.GOLD_BLOCK.defaultBlockState(), 2);
		int chestY = markedGround(chunk, pos, minX + 10, minZ + 10, world);
		world.setBlock(pos.set(minX + 10, chestY + 1, minZ + 10), Blocks.CHEST.defaultBlockState(), 2);
		if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			ItemStack sentinel = new ItemStack(Items.DIAMOND);
			sentinel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					Component.literal(CHEST_ITEM_NAME));
			chest.setItem(0, sentinel);
			chest.setChanged();
		}
		placeAuthoredNaturalSources(world, chunk, pos, minX, minZ, 16);
		return true;
	}

	private static boolean placeVegetationSentinels(WorldGenLevel world, ChunkAccess chunk) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		int treeY = markedGround(chunk, pos, minX + 4, minZ + 4, world);
		for (int y = 1; y <= 2; y++) {
			world.setBlock(pos.set(minX + 4, treeY + y, minZ + 4), Blocks.OAK_LOG.defaultBlockState(), 2);
		}
		world.setBlock(pos.set(minX + 4, treeY + 3, minZ + 4), Blocks.OAK_LEAVES.defaultBlockState(), 2);
		int vegetationY = markedGround(chunk, pos, minX + 6, minZ + 6, world);
		world.setBlock(pos.set(minX + 6, vegetationY + 1, minZ + 6), Blocks.DIRT.defaultBlockState(), 2);
		world.setBlock(pos.set(minX + 6, vegetationY + 2, minZ + 6), Blocks.OAK_SAPLING.defaultBlockState(), 2);
		placeAuthoredNaturalSources(world, chunk, pos, minX, minZ, 20);
		return true;
	}

	private static void placeAuthoredNaturalSources(WorldGenLevel world, ChunkAccess chunk,
			BlockPos.MutableBlockPos pos, int minX, int minZ, int depth) {
		if (!world.getLevel().dimension().equals(OPEN)) return;
		for (int index = 0; index < NATURAL_SOURCES.length; index++) {
			int x = naturalX(minX, index);
			int z = naturalZ(minZ, index);
			int groundY = findMarkedGround(chunk, pos, x, z,
					world.getMinY(), world.getMaxY());
			world.setBlock(pos.set(x, groundY - depth, z),
					NATURAL_SOURCES[index].defaultBlockState(), 2);
		}
	}

	private static int naturalX(int minX, int index) {
		return minX + 12 + index % 4;
	}

	private static int naturalZ(int minZ, int index) {
		return minZ + 1 + index / 4;
	}

	private static int markedGround(ChunkAccess chunk, BlockPos.MutableBlockPos pos,
			int x, int z, WorldGenLevel world) {
		return findMarkedGround(chunk, pos, x, z, world.getMinY(), world.getMaxY());
	}

	private record Material(BlockState top, BlockState filler,
			BlockState underwater, BlockState ceiling) { }

	private record NaturalSourceAudit(long rawConverted, long structurePreserved,
			long vegetationPreserved, long cavePreserved, long underwaterPreserved,
			long bedrockPreserved, long blockEntityPreserved) { }

	private record AuditResult(long top, long underwater, long filler, long geology,
			long ceiling, long roofTop, int biomeA, int biomeB,
			int edgeChanges, int sentinels, long aquiferFluid,
			long rawNaturalSources, long structureNaturalSources,
			long vegetationNaturalSources, long cavePockets, long underwaterPockets,
			long rawBedrock, long rawBlockEntities,
			long dictionaryPrimary, long dictionaryAlternative, long dynamicBiomeOre) { }
}
