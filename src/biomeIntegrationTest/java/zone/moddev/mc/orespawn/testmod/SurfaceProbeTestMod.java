package zone.moddev.mc.orespawn.testmod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import zone.moddev.mc.orespawn.api.BiomePlacementMode;
import zone.moddev.mc.orespawn.api.BiomeRegionSize;
import zone.moddev.mc.orespawn.api.BiomeReplacementScope;
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.OreSpawnBiomes;
import zone.moddev.mc.orespawn.api.OreSpawnBiomes.BiomeReference;
import zone.moddev.mc.orespawn.api.OreSpawnBiomes.BiomeRegistrar;
import zone.moddev.mc.orespawn.api.OrePatternType;
import zone.moddev.mc.orespawn.api.OreSpawnPatternRegistry;
import zone.moddev.mc.orespawn.api.ProviderStatus;
import zone.moddev.mc.orespawn.api.StandardPatternSettings;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import zone.moddev.mc.orespawn.api.WorldgenProvider.BiomeSurfaceDefinition;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;
import zone.moddev.mc.orespawn.worldgen.SurfaceProbeSpringBridge;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.ChunkGeneratorFlat;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.IChunkGenSettings;
import net.minecraft.world.gen.surfacebuilders.CompositeSurfaceBuilder;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.LiquidsConfig;
import net.minecraft.world.gen.placement.IPlacementConfig;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Independent, test-only provider surface fixture. */
@Mod(SurfaceProbeTestMod.MODID)
public final class SurfaceProbeTestMod {
	static final String MODID = "surfaceprobe";

	private static final Logger LOGGER = LogManager.getLogger();
	private static final OrePatternType EXTERNAL_PATTERN = OrePatternType.create(
			StandardPatternSettings.CODEC, settings -> context -> true)
			.setRegistryName(MODID, "external_probe");
	private static final ProbeFeature TERRAIN_FEATURE = new ProbeFeature(ProbeStage.TERRAIN);
	private static final ProbeFeature STRUCTURE_FEATURE = new ProbeFeature(ProbeStage.STRUCTURE);
	private static final ProbeFeature VEGETATION_FEATURE = new ProbeFeature(ProbeStage.VEGETATION);
	private final BiomeRegistrar biomes;
	private final BiomeReference surfaceA;
	private final BiomeReference surfaceB;
	private static CompositeFeature<?, ?> terrainConfigured;
	private static CompositeFeature<?, ?> structureConfigured;
	private static CompositeFeature<?, ?> vegetationConfigured;
	private static final DimensionType OPEN = DimensionType.THE_END;
	private static final DimensionType ROOFED = DimensionType.NETHER;
	private static final ResourceLocation OPEN_ID = new ResourceLocation("minecraft:the_end");
	private static final ResourceLocation ROOFED_ID = new ResourceLocation("minecraft:the_nether");
	private static final ResourceLocation BIOME_A = new ResourceLocation(MODID + ":surface_a");
	private static final ResourceLocation BIOME_B = new ResourceLocation(MODID + ":surface_b");
	private static final ResourceLocation PROBE_GEOME = new ResourceLocation(MODID + ":dynamic_biome_geome");
	private static final ResourceLocation SPRING_ROCK = new ResourceLocation(MODID + ":rock/spring_host");
	private static final ResourceLocation DYNAMIC_FLUID = new ResourceLocation(MODID + ":fluid/dynamic_water");
	private static final BlockPos SPRING_POS = new BlockPos(1128, 32, 1128);
	private static final ResourceLocation[] BUILT_IN_GEOMES = {
			new ResourceLocation("orespawn:stable_craton"), new ResourceLocation("orespawn:mountain_belt"),
			new ResourceLocation("orespawn:volcanic_arc"), new ResourceLocation("orespawn:sedimentary_basin"),
			new ResourceLocation("orespawn:coastal_shelf"), new ResourceLocation("orespawn:arid_basin"),
			new ResourceLocation("orespawn:wetland_basin"), new ResourceLocation("orespawn:glacial_highland")
	};
	private static final Set<ResourceLocation> BASE_BIOMES = Collections.unmodifiableSet(
			new java.util.LinkedHashSet<ResourceLocation>(Arrays.asList(
			new ResourceLocation("minecraft", "the_end"),
			new ResourceLocation("minecraft", "small_end_islands"),
			new ResourceLocation("minecraft", "end_midlands"),
			new ResourceLocation("minecraft", "end_highlands"),
			new ResourceLocation("minecraft", "end_barrens"),
			new ResourceLocation("minecraft", "nether"), BIOME_A, BIOME_B)));
	private static final int MINIMUM_CHUNK = 63;
	private static final int MAXIMUM_CHUNK = 65;
	private static final int FLUID_PROBE_MIN_CHUNK_X = 60;
	private static final int FLUID_PROBE_MAX_CHUNK_X = 62;
	private static final int EXPECTED_COLUMNS = 9 * 16 * 16;
	private static final int EXPECTED_FILLER = EXPECTED_COLUMNS * 3;
	private static final String PHASE_PROPERTY = "surfaceprobe.integrationPhase";
	private static final String MARKER_NAME = "surfaceprobe-integration.properties";
	private static final String CHEST_ITEM_NAME = "surfaceprobe sentinel";

	public SurfaceProbeTestMod() {
		FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
		IEventBus modBus = context.getModEventBus();
		biomes = OreSpawnBiomes.registrar(MODID);
		surfaceA = OreSpawnBiomes.blankAndRegister(
				biomes, "surface_a", builder -> configureBiome(builder, 1.35F, 0.15F));
		surfaceB = OreSpawnBiomes.blankAndRegister(
				biomes, "surface_b", builder -> configureBiome(builder, 0.7F, 0.8F));
		modBus.addGenericListener(Biome.class, this::tagBiomes);
		modBus.addGenericListener(OrePatternType.class, this::registerPatterns);
		modBus.addListener(this::setup);
		modBus.addListener(this::enqueueProvider);
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::enableGeologyProbe);
		MinecraftForge.EVENT_BUS.addListener(this::auditGeneratedSurfaces);
	}

	private void setup(FMLCommonSetupEvent event) {
		terrainConfigured = configured(TERRAIN_FEATURE);
		structureConfigured = configured(STRUCTURE_FEATURE);
		vegetationConfigured = configured(VEGETATION_FEATURE);
		addFixtureFeatures();
	}

	private void registerPatterns(RegistryEvent.Register<OrePatternType> event) {
		event.getRegistry().register(EXTERNAL_PATTERN);
	}

	private void tagBiomes(RegistryEvent.Register<Biome> event) {
		BiomeDictionary.addTypes(surfaceA.get(),
				BiomeDictionary.Type.HOT, BiomeDictionary.Type.DRY);
		BiomeDictionary.addTypes(surfaceB.get(),
				BiomeDictionary.Type.HOT, BiomeDictionary.Type.WET);
	}

	private static void configureBiome(Biome.BiomeBuilder builder, float temperature, float downfall) {
		builder.precipitation(Biome.RainType.RAIN).category(Biome.Category.NONE)
				.depth(0.1F).scale(0.2F).temperature(temperature).downfall(downfall)
				.waterColor(4159204).waterFogColor(329011)
				.surfaceBuilder(new CompositeSurfaceBuilder<>(Biome.DEFAULT_SURFACE_BUILDER,
						Biome.GRASS_DIRT_GRAVEL_SURFACE));
	}

	private static CompositeFeature<?, ?> configured(Feature<NoFeatureConfig> feature) {
		return Biome.createCompositeFeature(feature, new NoFeatureConfig(),
				Biome.PASSTHROUGH, IPlacementConfig.NO_PLACEMENT_CONFIG);
	}

	private static void addFixtureFeatures() {
		for (ResourceLocation id : BASE_BIOMES) {
			Biome biome = ForgeRegistries.BIOMES.getValue(id);
			if (biome == null) throw new IllegalStateException("Surface probe biome is unavailable: " + id);
			addUnique(biome.getFeatures(GenerationStage.Decoration.RAW_GENERATION), terrainConfigured);
			addUnique(biome.getFeatures(GenerationStage.Decoration.SURFACE_STRUCTURES), structureConfigured);
			addUnique(biome.getFeatures(GenerationStage.Decoration.VEGETAL_DECORATION), vegetationConfigured);
		}
	}

	private static void addUnique(java.util.List<CompositeFeature<?, ?>> features,
			CompositeFeature<?, ?> feature) {
		if (!features.contains(feature)) features.add(feature);
	}

	private void enqueueProvider(InterModEnqueueEvent event) {
		WorldgenProvider.Builder provider = WorldgenProvider.builder(MODID, 1);
		addDynamicBiomeGeology(provider);
		provider.fluidDeposit(DYNAMIC_FLUID, blockId(Blocks.WATER), deposit -> deposit
				.dimension(OPEN_ID, placement -> placement
						.yRange(16, 24)
						.attempts(12.0D)
						.radius(1, 1)
						.verticalRadius(1, 1)
						.maxLobes(1)
						.minSolidCover(1)
						.minSolidShell(1)
						.hostBlock(blockId(Blocks.DIORITE))));
		addPalette(provider, "open_palette_0", OPEN_ID, false);
		addPalette(provider, "roofed_palette_0", ROOFED_ID, true);
		provider.dimensionMaterials(new ResourceLocation(MODID + ":materials/nether"), ROOFED_ID,
				materials -> materials.defaultFluid(blockId(Blocks.WATER)));
		if (!OreSpawnApi.enqueue(provider.build())) {
			throw new IllegalStateException("Could not enqueue surface probe provider");
		}
	}

	private static void addDynamicBiomeGeology(WorldgenProvider.Builder provider) {
		provider.geome(PROBE_GEOME, geome -> geome
				.baseWeight(0.0D)
				.familyWeight(GeologyFamily.SEDIMENTARY, 1.0D));
		provider.rock(new ResourceLocation(MODID + ":rock/dynamic_biome"), blockId(Blocks.DIORITE),
				GeologyFamily.SEDIMENTARY, rock -> {
					rock.dimensions(java.util.Collections.singleton(OPEN_ID));
					rock.geomeWeight(PROBE_GEOME, 1.0D);
					for (ResourceLocation geome : BUILT_IN_GEOMES) rock.geomeWeight(geome, 0.0D);
				});
		provider.rock(new ResourceLocation(MODID + ":rock/fallback"), blockId(Blocks.GRANITE),
				GeologyFamily.SEDIMENTARY, rock -> {
					rock.dimensions(java.util.Collections.singleton(OPEN_ID));
					rock.geomeWeight(PROBE_GEOME, 0.0D);
					for (ResourceLocation geome : BUILT_IN_GEOMES) rock.geomeWeight(geome, 1.0D);
				});
		provider.rock(SPRING_ROCK, blockId(Blocks.DIAMOND_BLOCK),
				GeologyFamily.IGNEOUS_INTRUSIVE, rock -> {
					rock.dimensions(java.util.Collections.singleton(
							new ResourceLocation("minecraft", "overworld")));
					for (ResourceLocation geome : BUILT_IN_GEOMES) rock.geomeWeight(geome, 1.0D);
				});
		provider.biome(BIOME_A, java.util.Collections.singletonMap(PROBE_GEOME, 100.0D));
		provider.biome(BIOME_B, java.util.Collections.singletonMap(PROBE_GEOME, 100.0D));
	}

	private void enableGeologyProbe(FMLServerAboutToStartEvent event) {
		Path profile = worldRoot(event.getServer()).resolve("serverconfig")
				.resolve("orespawn-worldgen.json");
		JsonObject root;
		try (BufferedReader reader = Files.newBufferedReader(profile)) {
			root = new JsonParser().parse(reader).getAsJsonObject();
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Could not read the test-owned End geology profile", exception);
		}
		try {
			root.addProperty("place_fluid_deposits", true);
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
			end.add("host_blocks", hosts);
			end.add("host_tags", new JsonArray());
			terrain.add(OPEN_ID.toString(), end);
			try (BufferedWriter writer = Files.newBufferedWriter(profile)) {
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
			ResourceLocation dimension, boolean ceiling) {
		BiomeSurfaceDefinition surfaceA = surface(EnumDyeColor.PINK, EnumDyeColor.WHITE,
				EnumDyeColor.BLUE, ceiling ? EnumDyeColor.ORANGE : null);
		BiomeSurfaceDefinition surfaceB = surface(EnumDyeColor.LIME, EnumDyeColor.YELLOW,
				EnumDyeColor.LIGHT_BLUE, ceiling ? EnumDyeColor.MAGENTA : null);
		provider.biomePalette(new ResourceLocation(MODID + ":" + name), dimension,
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

	private static BiomeSurfaceDefinition surface(EnumDyeColor top, EnumDyeColor filler,
			EnumDyeColor underwater, EnumDyeColor ceiling) {
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

	private static ResourceLocation blockId(Block block) {
		return ForgeRegistries.BLOCKS.getKey(block);
	}

	private void auditGeneratedSurfaces(FMLServerStartedEvent event) {
		String phase = System.getProperty(PHASE_PROPERTY, "").trim();
		if (!phase.equals("fresh") && !phase.equals("reload")) {
			throw new IllegalStateException("Missing or invalid " + PHASE_PROPERTY + ": " + phase);
		}
		if (OreSpawnApi.getProviderStatus(MODID) != ProviderStatus.ACTIVE) {
			throw new IllegalStateException("Surface probe provider is not active");
		}
		verifyOrePatternRegistry();
		WorldServer overworld = event.getServer().getWorld(DimensionType.OVERWORLD);
		if (overworld == null || overworld.getSeed() != 0L) {
			throw new IllegalStateException("Surface probe expected actual world seed 0 but found "
					+ (overworld == null ? "missing overworld" : overworld.getSeed()));
		}

		Path marker = worldRoot(event.getServer()).resolve(MARKER_NAME);
		Properties previous = phase.equals("reload") ? readMarker(marker) : null;
		if (phase.equals("fresh") && Files.exists(marker)) {
			throw new IllegalStateException("Fresh surface probe retained a reload marker");
		}

		Map<String, AuditResult> results = new LinkedHashMap<>();
		results.put("open", auditDimension(requireLevel(event, OPEN), false));
		results.put("roofed", auditDimension(requireLevel(event, ROOFED), true));
		SpringResult spring = auditProviderRockSpring(overworld, phase);
		Properties current = properties(overworld.getSeed(), results, spring);
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
		event.getServer().initiateShutdown();
	}

	private static void verifyOrePatternRegistry() {
		for (String name : Arrays.asList("default", "vein", "normal_cloud", "precision",
				"clusters", "underfluids")) {
			if (OreSpawnPatternRegistry.registry().getValue(
					new ResourceLocation("orespawn", name)) == null) {
				throw new IllegalStateException("Built-in ore pattern is missing: " + name);
			}
		}
		if (OreSpawnPatternRegistry.registry().getValue(
				new ResourceLocation(MODID, "external_probe")) != EXTERNAL_PATTERN) {
			throw new IllegalStateException("Fixture-owned external ore pattern did not register");
		}
	}

	private static SpringResult auditProviderRockSpring(WorldServer level, String phase) {
		LiquidsConfig spring = SurfaceProbeSpringBridge.findRewrittenSpring(
				ForgeRegistries.BIOMES.getValues());
		if (spring == null) {
			throw new IllegalStateException("No rewritten Forge 28 vanilla spring leaf was found");
		}
		if (!SurfaceProbeSpringBridge.recognizesProviderRock(Blocks.DIAMOND_BLOCK)) {
			throw new IllegalStateException("Baked provider rock was not accepted by the spring wrapper");
		}
		level.getChunk(SPRING_POS.getX() >> 4, SPRING_POS.getZ() >> 4);
		if ("fresh".equals(phase)) {
			for (BlockPos rock : Arrays.asList(SPRING_POS.up(), SPRING_POS.down(),
					SPRING_POS.west(), SPRING_POS.east(), SPRING_POS.north())) {
				level.setBlockState(rock, Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
			}
			level.setBlockState(SPRING_POS, Blocks.AIR.getDefaultState(), 2);
			level.setBlockState(SPRING_POS.south(), Blocks.AIR.getDefaultState(), 2);
			if (!SurfaceProbeSpringBridge.place(level, SPRING_POS, spring)) {
				throw new IllegalStateException("Rewritten spring did not place in provider rock cavity");
			}
		}
		Block expected = spring.field_202459_a.getDefaultState().getBlockState().getBlock();
		Block actual = level.getBlockState(SPRING_POS).getBlock();
		if (actual != expected) {
			throw new IllegalStateException("Provider-rock spring changed across " + phase
					+ ": expected " + expected + " but found " + actual);
		}
		return new SpringResult(ForgeRegistries.BLOCKS.getKey(actual));
	}

	private static Path worldRoot(MinecraftServer server) {
		return server.getActiveAnvilConverter().getFile(server.getFolderName(), "level.dat")
				.toPath().toAbsolutePath().normalize().getParent();
	}

	private static WorldServer requireLevel(FMLServerStartedEvent event, DimensionType key) {
		WorldServer level = event.getServer().getWorld(key);
		if (level == null) throw new IllegalStateException("Surface probe dimension is unavailable: " + DimensionType.func_212678_a(key));
		if (level.getChunkProvider().getChunkGenerator() instanceof ChunkGeneratorFlat) {
			throw new IllegalStateException("Surface probe requires normal noise terrain: " + DimensionType.func_212678_a(key));
		}
		return level;
	}

	private static AuditResult auditDimension(WorldServer level, boolean roofed) {
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
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int chunkZ = MINIMUM_CHUNK; chunkZ <= MAXIMUM_CHUNK; chunkZ++) {
			for (int chunkX = MINIMUM_CHUNK; chunkX <= MAXIMUM_CHUNK; chunkX++) {
				level.getChunk(chunkX, chunkZ);
				Chunk chunk = level.getChunk(chunkX, chunkZ);
				int chunkMinX = chunkX << 4;
				int chunkMinZ = chunkZ << 4;
				for (int localZ = 0; localZ < 16; localZ++) {
					for (int localX = 0; localX < 16; localX++) {
						int x = chunkMinX + localX;
						int z = chunkMinZ + localZ;
						int groundY = findMarkedGround(chunk, pos, x, z, 0, 256);
						Biome biome = level.getBiome(pos.setPos(x, groundY, z));
						ResourceLocation biomeId = biomeId(level, biome);
						Material material = material(biomeId, roofed);
						float expectedTemperature = BIOME_A.equals(biomeId) ? 1.35F : 0.7F;
						float expectedDownfall = BIOME_A.equals(biomeId) ? 0.15F : 0.8F;
						Biome biomeValue = biome;
						if (Float.compare(biomeValue.getDefaultTemperature(), expectedTemperature) != 0
								|| Float.compare(biomeValue.getDownfall(), expectedDownfall) != 0) {
							throw new IllegalStateException("Provider climate changed for " + biomeId
									+ " at " + pos + ": temperature="
									+ biomeValue.getDefaultTemperature() + ", downfall="
									+ biomeValue.getDownfall());
						}
						if (BIOME_A.equals(biomeId)) biomeA++; else biomeB++;
						if (x > (MINIMUM_CHUNK << 4)) {
							ResourceLocation westBiome = biomeId(level,
									level.getBiome(pos.setPos(x - 1, groundY, z)));
							if (!westBiome.equals(biomeId)) edgeChanges++;
						}
						if (z > (MINIMUM_CHUNK << 4)) {
							ResourceLocation northBiome = biomeId(level,
									level.getBiome(pos.setPos(x, groundY, z - 1)));
							if (!northBiome.equals(biomeId)) edgeChanges++;
						}
						boolean waterColumn = localX == 1 && localZ == 1;
						IBlockState expectedTop = waterColumn ? material.underwater() : material.top();
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
								assertBlock(chunk, pos, x, groundY - depth, z,
										Blocks.DIORITE.getDefaultState(), "dynamic-biome geome rock");
								geology++;
							}
						}
						if (roofed) {
							ResourceLocation ceilingBiome = biomeId(level, level.getBiome(
									pos.setPos(x, groundY + 8, z)));
							IBlockState expectedCeiling = material(ceilingBiome, true).ceiling();
							assertBlock(chunk, pos, x, groundY + 8, z, expectedCeiling,
									"roof underside");
							assertBlock(chunk, pos, x, groundY + 10, z, Blocks.STONE.getDefaultState(),
									"roof top");
							ceiling++;
							roofTop++;
						}
					}
				}
				sentinels += auditSentinels(level, chunk, pos, chunkMinX, chunkMinZ);
			}
		}

		if (top != EXPECTED_COLUMNS - 9 || underwater != 9 || filler != EXPECTED_FILLER
				|| biomeA == 0 || biomeB == 0 || edgeChanges == 0 || sentinels != 9 * 4
				|| geology != (roofed ? 0 : EXPECTED_FILLER)
				|| (roofed && (ceiling != EXPECTED_COLUMNS || roofTop != EXPECTED_COLUMNS))) {
			throw new IllegalStateException("Incomplete surface audit for " + DimensionType.func_212678_a(level.dimension.getType())
					+ ": top=" + top + ", underwater=" + underwater + ", filler=" + filler
					+ ", biomeA=" + biomeA + ", biomeB=" + biomeB + ", edges=" + edgeChanges
					+ ", sentinels=" + sentinels + ", geology=" + geology
					+ ", ceiling=" + ceiling + ", roofTop=" + roofTop);
		}
		long aquiferFluid = roofed ? 0L : auditDynamicFluid(level);
		return new AuditResult(top, underwater, filler, geology, ceiling, roofTop,
				biomeA, biomeB, edgeChanges, sentinels, aquiferFluid);
	}

	private static long auditDynamicFluid(WorldServer level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		long water = 0L;
		for (int chunkZ = MINIMUM_CHUNK; chunkZ <= MAXIMUM_CHUNK; chunkZ++) {
			for (int chunkX = MINIMUM_CHUNK; chunkX <= MAXIMUM_CHUNK; chunkX++) {
				level.getChunk(chunkX, chunkZ);
				Chunk chunk = level.getChunk(chunkX, chunkZ);
				for (int x = chunk.getPos().getXStart(); x <= chunk.getPos().getXEnd(); x++) {
					for (int z = chunk.getPos().getZStart(); z <= chunk.getPos().getZEnd(); z++) {
						for (int y = 12; y <= 30; y++) {
							if (chunk.getBlockState(pos.setPos(x, y, z)).getBlock() == Blocks.WATER) water++;
						}
					}
				}
			}
		}
		if (water == 0L) {
			throw new IllegalStateException("Forge 25 dynamic fluid deposit produced no covered flowing-water blocks");
		}
		return water;
	}

	private static int auditSentinels(WorldServer level, Chunk chunk,
			BlockPos.MutableBlockPos pos, int minX, int minZ) {
		int groundTree = findMarkedGround(chunk, pos, minX + 4, minZ + 4,
				0, 256);
		assertBlock(chunk, pos, minX + 4, groundTree + 1, minZ + 4,
				Blocks.OAK_LOG.getDefaultState(), "tree log");
		assertBlock(chunk, pos, minX + 4, groundTree + 3, minZ + 4,
				Blocks.OAK_LEAVES.getDefaultState(), "tree leaves");

		int groundVegetation = findMarkedGround(chunk, pos, minX + 6, minZ + 6,
				0, 256);
		assertBlock(chunk, pos, minX + 6, groundVegetation + 1, minZ + 6,
				Blocks.DIRT.getDefaultState(), "vegetation substrate");
		assertBlock(chunk, pos, minX + 6, groundVegetation + 2, minZ + 6,
				Blocks.OAK_SAPLING.getDefaultState(), "vegetation");

		int groundStructure = findMarkedGround(chunk, pos, minX + 8, minZ + 8,
				0, 256);
		assertBlock(chunk, pos, minX + 8, groundStructure + 1, minZ + 8,
				Blocks.GOLD_BLOCK.getDefaultState(), "authored structure");

		int groundChest = findMarkedGround(chunk, pos, minX + 10, minZ + 10,
				0, 256);
		assertBlock(chunk, pos, minX + 10, groundChest + 1, minZ + 10,
				Blocks.CHEST.getDefaultState(), "chest sentinel");
		if (!(level.getTileEntity(pos.setPos(minX + 10, groundChest + 1, minZ + 10))
				instanceof TileEntityChest)) {
			throw new IllegalStateException("Chest block entity data changed at " + pos);
		}
		TileEntityChest chest = (TileEntityChest) level.getTileEntity(pos);
		if (chest == null
				|| chest.getStackInSlot(0).getItem() != Items.DIAMOND
				|| !CHEST_ITEM_NAME.equals(chest.getStackInSlot(0).getDisplayName().getString())) {
			throw new IllegalStateException("Chest block entity data changed at " + pos);
		}
		return 4;
	}

	private static int findMarkedGround(IChunk chunk, BlockPos.MutableBlockPos pos,
			int x, int z, int minY, int maxY) {
		for (int y = maxY - 1; y >= minY; y--) {
			if (chunk.getBlockState(pos.setPos(x, y, z)).getBlock() == concreteBlock(EnumDyeColor.BLACK)) return y + 5;
		}
		throw new IllegalStateException("Independent surface marker missing at " + x + "," + z);
	}

	private static void assertBlock(IChunk chunk, BlockPos.MutableBlockPos pos,
			int x, int y, int z, IBlockState expected, String purpose) {
		IBlockState actual = chunk.getBlockState(pos.setPos(x, y, z));
		if (actual.getBlock() != expected.getBlock()) {
			throw new IllegalStateException("Expected " + purpose + " " + expected.getBlock()
					+ " at " + pos + " but found " + actual.getBlock());
		}
	}

	private static Material material(ResourceLocation biome, boolean roofed) {
		if (BIOME_A.equals(biome)) {
			return new Material(concrete(EnumDyeColor.PINK), concrete(EnumDyeColor.WHITE),
					concrete(EnumDyeColor.BLUE), roofed ? concrete(EnumDyeColor.ORANGE) : null);
		}
		if (BIOME_B.equals(biome)) {
			return new Material(concrete(EnumDyeColor.LIME), concrete(EnumDyeColor.YELLOW),
					concrete(EnumDyeColor.LIGHT_BLUE), roofed ? concrete(EnumDyeColor.MAGENTA) : null);
		}
		throw new IllegalStateException("Unexpected provider biome " + biome);
	}

	private static IBlockState concrete(EnumDyeColor color) {
		return concreteBlock(color).getDefaultState();
	}

	private static Block concreteBlock(EnumDyeColor color) {
		if (color == EnumDyeColor.WHITE) return Blocks.WHITE_CONCRETE;
		if (color == EnumDyeColor.ORANGE) return Blocks.ORANGE_CONCRETE;
		if (color == EnumDyeColor.MAGENTA) return Blocks.MAGENTA_CONCRETE;
		if (color == EnumDyeColor.LIGHT_BLUE) return Blocks.LIGHT_BLUE_CONCRETE;
		if (color == EnumDyeColor.YELLOW) return Blocks.YELLOW_CONCRETE;
		if (color == EnumDyeColor.LIME) return Blocks.LIME_CONCRETE;
		if (color == EnumDyeColor.PINK) return Blocks.PINK_CONCRETE;
		if (color == EnumDyeColor.GRAY) return Blocks.GRAY_CONCRETE;
		if (color == EnumDyeColor.LIGHT_GRAY) return Blocks.LIGHT_GRAY_CONCRETE;
		if (color == EnumDyeColor.CYAN) return Blocks.CYAN_CONCRETE;
		if (color == EnumDyeColor.PURPLE) return Blocks.PURPLE_CONCRETE;
		if (color == EnumDyeColor.BLUE) return Blocks.BLUE_CONCRETE;
		if (color == EnumDyeColor.BROWN) return Blocks.BROWN_CONCRETE;
		if (color == EnumDyeColor.GREEN) return Blocks.GREEN_CONCRETE;
		if (color == EnumDyeColor.RED) return Blocks.RED_CONCRETE;
		return Blocks.BLACK_CONCRETE;
	}

	private static ResourceLocation biomeId(WorldServer level, Biome biome) {
		return ForgeRegistries.BIOMES.getKey(biome);
	}

	private static Properties properties(long seed, Map<String, AuditResult> results,
			SpringResult spring) {
		Properties values = new Properties();
		values.setProperty("seed", Long.toString(seed));
		values.setProperty("dimensions", Integer.toString(results.size()));
		values.setProperty("columns_per_dimension", Integer.toString(EXPECTED_COLUMNS));
		values.setProperty("spring.x", Integer.toString(SPRING_POS.getX()));
		values.setProperty("spring.y", Integer.toString(SPRING_POS.getY()));
		values.setProperty("spring.z", Integer.toString(SPRING_POS.getZ()));
		values.setProperty("spring.block", spring.block().toString());
		values.setProperty("external_pattern", new ResourceLocation(MODID, "external_probe").toString());
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

	private static final class ProbeFeature extends Feature<NoFeatureConfig> {
		private final ProbeStage stage;

		private ProbeFeature(ProbeStage stage) {
			super();
			this.stage = stage;
		}

		@Override
		public boolean func_212245_a(IWorld world,
				IChunkGenerator<? extends IChunkGenSettings> chunkGenerator, Random random,
				BlockPos origin, NoFeatureConfig config) {
			IChunk chunk = world.getChunk((origin.getX() >> 4) + 1,
					(origin.getZ() >> 4) + 1);
			if (stage == ProbeStage.TERRAIN) return prepareTerrain(world, chunk);
			if (stage == ProbeStage.STRUCTURE) return placeStructureSentinels(world, chunk);
			return placeVegetationSentinels(world, chunk);
		}
	}

	private static boolean prepareTerrain(IWorld world, IChunk chunk) {
		boolean roofed = ROOFED_ID.equals(DimensionType.func_212678_a(world.getWorld().dimension.getType()));
		if (roofed && chunk.getPos().x >= FLUID_PROBE_MIN_CHUNK_X
				&& chunk.getPos().x <= FLUID_PROBE_MAX_CHUNK_X
				&& chunk.getPos().z >= MINIMUM_CHUNK && chunk.getPos().z <= MAXIMUM_CHUNK) return false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = chunk.getPos().getXStart();
		int minZ = chunk.getPos().getZStart();
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int x = minX + localX;
				int z = minZ + localZ;
				int groundY = chunk.getTopBlockY(Heightmap.Type.WORLD_SURFACE_WG, localX, localZ);
				if (roofed) {
					while (groundY > 0
							&& solid(chunk.getBlockState(pos.setPos(x, groundY, z)))) groundY--;
				}
				while (groundY > 0
						&& !solid(chunk.getBlockState(pos.setPos(x, groundY, z)))) groundY--;
				if (!roofed && groundY <= 0) groundY = 64;
				// Forge 25 decoration may revisit a neighbouring chunk through the
				// +8 feature origin. Never let a later RAW_GENERATION pass bury the
				// provider surface that the first pass already corrected.
				if (chunk.getBlockState(pos.setPos(x, groundY - 5, z)).getBlock()
						== concreteBlock(EnumDyeColor.BLACK)) continue;
				chunk.setBlockState(pos.setPos(x, groundY, z), Blocks.GRASS_BLOCK.getDefaultState(), false);
				for (int depth = 1; depth <= 3; depth++) {
					chunk.setBlockState(pos.setPos(x, groundY - depth, z), Blocks.DIRT.getDefaultState(), false);
				}
				if (!roofed) {
					for (int depth = 6; depth <= 40; depth++) {
						chunk.setBlockState(pos.setPos(x, groundY - depth, z), Blocks.END_STONE.getDefaultState(), false);
					}
				}
				chunk.setBlockState(pos.setPos(x, groundY - 5, z),
						concreteBlock(EnumDyeColor.BLACK).getDefaultState(), false);
				if (roofed) {
					for (int openY = groundY + 1; openY < 256; openY++) {
						chunk.setBlockState(pos.setPos(x, openY, z), Blocks.AIR.getDefaultState(), false);
					}
					for (int roofY = groundY + 8; roofY <= groundY + 10; roofY++) {
						chunk.setBlockState(pos.setPos(x, roofY, z), Blocks.STONE.getDefaultState(), false);
					}
				}
				if (localX == 1 && localZ == 1) {
					chunk.setBlockState(pos.setPos(x, groundY + 1, z), Blocks.WATER.getDefaultState(), false);
				}
			}
		}
		// Decoration writes do not update Forge 25's frozen *_WG maps. Rebuild
		// the controlled fixture map so the production pass sees the exposed Y.
		SurfaceProbeSpringBridge.rebuildWorldSurfaceHeight(chunk);
		return true;
	}

	private static boolean solid(IBlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty();
	}

	private static boolean placeStructureSentinels(IWorld world, IChunk chunk) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = chunk.getPos().getXStart();
		int minZ = chunk.getPos().getZStart();
		int structureY = markedGroundOrMissing(chunk, pos, minX + 8, minZ + 8, world);
		if (structureY == Integer.MIN_VALUE) return false;
		world.setBlockState(pos.setPos(minX + 8, structureY + 1, minZ + 8),
				Blocks.GOLD_BLOCK.getDefaultState(), 2);
		int chestY = markedGround(chunk, pos, minX + 10, minZ + 10, world);
		world.setBlockState(pos.setPos(minX + 10, chestY + 1, minZ + 10), Blocks.CHEST.getDefaultState(), 2);
		if (world.getTileEntity(pos) instanceof TileEntityChest) {
			TileEntityChest chest = (TileEntityChest) world.getTileEntity(pos);
			ItemStack sentinel = new ItemStack(Items.DIAMOND);
			sentinel.setDisplayName(new TextComponentString(CHEST_ITEM_NAME));
			chest.setInventorySlotContents(0, sentinel);
			chest.markDirty();
		}
		return true;
	}

	private static boolean placeVegetationSentinels(IWorld world, IChunk chunk) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = chunk.getPos().getXStart();
		int minZ = chunk.getPos().getZStart();
		int treeY = markedGroundOrMissing(chunk, pos, minX + 4, minZ + 4, world);
		if (treeY == Integer.MIN_VALUE) return false;
		for (int y = 1; y <= 2; y++) {
			world.setBlockState(pos.setPos(minX + 4, treeY + y, minZ + 4), Blocks.OAK_LOG.getDefaultState(), 2);
		}
		world.setBlockState(pos.setPos(minX + 4, treeY + 3, minZ + 4), Blocks.OAK_LEAVES.getDefaultState(), 2);
		int vegetationY = markedGround(chunk, pos, minX + 6, minZ + 6, world);
		world.setBlockState(pos.setPos(minX + 6, vegetationY + 1, minZ + 6), Blocks.DIRT.getDefaultState(), 2);
		world.setBlockState(pos.setPos(minX + 6, vegetationY + 2, minZ + 6), Blocks.OAK_SAPLING.getDefaultState(), 2);
		return true;
	}

	private static int markedGround(IChunk chunk, BlockPos.MutableBlockPos pos,
			int x, int z, IWorld world) {
		return findMarkedGround(chunk, pos, x, z, 0, 256);
	}

	private static int markedGroundOrMissing(IChunk chunk, BlockPos.MutableBlockPos pos,
			int x, int z, IWorld world) {
		for (int y = 255; y >= 0; y--) {
			if (chunk.getBlockState(pos.setPos(x, y, z)).getBlock() == concreteBlock(EnumDyeColor.BLACK)) return y + 5;
		}
		return Integer.MIN_VALUE;
	}

	private static final class Material {
		private final IBlockState top;
		private final IBlockState filler;
		private final IBlockState underwater;
		private final IBlockState ceiling;

		Material(IBlockState top, IBlockState filler, IBlockState underwater, IBlockState ceiling) {
			this.top = top;
			this.filler = filler;
			this.underwater = underwater;
			this.ceiling = ceiling;
		}

		IBlockState top() { return top; }
		IBlockState filler() { return filler; }
		IBlockState underwater() { return underwater; }
		IBlockState ceiling() { return ceiling; }
	}

	private static final class SpringResult {
		private final ResourceLocation block;

		SpringResult(ResourceLocation block) {
			this.block = block;
		}

		ResourceLocation block() { return block; }
	}

	private static final class AuditResult {
		private final long top;
		private final long underwater;
		private final long filler;
		private final long geology;
		private final long ceiling;
		private final long roofTop;
		private final int biomeA;
		private final int biomeB;
		private final int edgeChanges;
		private final int sentinels;
		private final long aquiferFluid;

		AuditResult(long top, long underwater, long filler, long geology, long ceiling,
				long roofTop, int biomeA, int biomeB, int edgeChanges, int sentinels,
				long aquiferFluid) {
			this.top = top;
			this.underwater = underwater;
			this.filler = filler;
			this.geology = geology;
			this.ceiling = ceiling;
			this.roofTop = roofTop;
			this.biomeA = biomeA;
			this.biomeB = biomeB;
			this.edgeChanges = edgeChanges;
			this.sentinels = sentinels;
			this.aquiferFluid = aquiferFluid;
		}

		long top() { return top; }
		long underwater() { return underwater; }
		long filler() { return filler; }
		long geology() { return geology; }
		long ceiling() { return ceiling; }
		long roofTop() { return roofTop; }
		int biomeA() { return biomeA; }
		int biomeB() { return biomeB; }
		int edgeChanges() { return edgeChanges; }
		int sentinels() { return sentinels; }
		long aquiferFluid() { return aquiferFluid; }
	}
}
