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
import zone.moddev.mc.orespawn.api.ProviderStatus;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import zone.moddev.mc.orespawn.api.WorldgenProvider.BiomeSurfaceDefinition;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.FlatChunkGenerator;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Independent, test-only provider surface fixture. */
@Mod(SurfaceProbeTestMod.MODID)
public final class SurfaceProbeTestMod {
	static final String MODID = "surfaceprobe";

	private static final Logger LOGGER = LogManager.getLogger();
	private static final DeferredRegister<Feature<?>> FEATURES =
			DeferredRegister.create(ForgeRegistries.FEATURES, MODID);
	private static final RegistryObject<ProbeFeature> TERRAIN_FEATURE =
			FEATURES.register("terrain_setup", () -> new ProbeFeature(ProbeStage.TERRAIN));
	private static final RegistryObject<ProbeFeature> STRUCTURE_FEATURE =
			FEATURES.register("structure_sentinels", () -> new ProbeFeature(ProbeStage.STRUCTURE));
	private static final RegistryObject<ProbeFeature> VEGETATION_FEATURE =
			FEATURES.register("vegetation_sentinels", () -> new ProbeFeature(ProbeStage.VEGETATION));
	private static ConfiguredFeature<?, ?> terrainConfigured;
	private static ConfiguredFeature<?, ?> structureConfigured;
	private static ConfiguredFeature<?, ?> vegetationConfigured;
	private static final RegistryKey<World> OPEN = World.END;
	private static final RegistryKey<World> ROOFED = World.NETHER;
	private static final ResourceLocation OPEN_ID = new ResourceLocation("minecraft:the_end");
	private static final ResourceLocation ROOFED_ID = new ResourceLocation("minecraft:the_nether");
	private static final ResourceLocation BIOME_A = new ResourceLocation(MODID + ":surface_a");
	private static final ResourceLocation BIOME_B = new ResourceLocation(MODID + ":surface_b");
	private static final ResourceLocation PROBE_GEOME = new ResourceLocation(MODID + ":dynamic_biome_geome");
	private static final ResourceLocation DYNAMIC_FLUID = new ResourceLocation(MODID + ":fluid/dynamic_water");
	private static final ResourceLocation[] BUILT_IN_GEOMES = {
			new ResourceLocation("orespawn:stable_craton"), new ResourceLocation("orespawn:mountain_belt"),
			new ResourceLocation("orespawn:volcanic_arc"), new ResourceLocation("orespawn:sedimentary_basin"),
			new ResourceLocation("orespawn:coastal_shelf"), new ResourceLocation("orespawn:arid_basin"),
			new ResourceLocation("orespawn:wetland_basin"), new ResourceLocation("orespawn:glacial_highland")
	};
	private static final Set<ResourceLocation> BASE_BIOMES = Collections.unmodifiableSet(
			new java.util.LinkedHashSet<ResourceLocation>(Arrays.asList(
			new ResourceLocation("minecraft", "the_end"),
			new ResourceLocation("minecraft", "nether_wastes"),
			new ResourceLocation("minecraft", "soul_sand_valley"),
			new ResourceLocation("minecraft", "crimson_forest"),
			new ResourceLocation("minecraft", "warped_forest"),
			new ResourceLocation("minecraft", "basalt_deltas"))));
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
		FEATURES.register(modBus);
		modBus.addListener(this::setup);
		modBus.addListener(this::enqueueProvider);
		MinecraftForge.EVENT_BUS.addListener(this::addFixtureFeatures);
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::enableGeologyProbe);
		MinecraftForge.EVENT_BUS.addListener(this::auditGeneratedSurfaces);
	}

	private void setup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			terrainConfigured = registerConfigured("terrain_setup", TERRAIN_FEATURE.get());
			structureConfigured = registerConfigured("structure_sentinels", STRUCTURE_FEATURE.get());
			vegetationConfigured = registerConfigured("vegetation_sentinels", VEGETATION_FEATURE.get());
		});
	}

	private static ConfiguredFeature<?, ?> registerConfigured(String name,
			Feature<NoFeatureConfig> feature) {
		ResourceLocation id = new ResourceLocation(MODID, name);
		return Registry.register(WorldGenRegistries.CONFIGURED_FEATURE, id,
				feature.configured(NoFeatureConfig.INSTANCE)
						.decorated(Placement.NOPE.configured(NoPlacementConfig.INSTANCE)));
	}

	private void addFixtureFeatures(BiomeLoadingEvent event) {
		if (!BASE_BIOMES.contains(event.getName())) return;
		if (terrainConfigured == null || structureConfigured == null || vegetationConfigured == null) {
			throw new IllegalStateException("Surface probe features were not registered before biome loading");
		}
		addUnique(event.getGeneration().getFeatures(GenerationStage.Decoration.RAW_GENERATION),
				terrainConfigured);
		addUnique(event.getGeneration().getFeatures(GenerationStage.Decoration.SURFACE_STRUCTURES),
				structureConfigured);
		addUnique(event.getGeneration().getFeatures(GenerationStage.Decoration.VEGETAL_DECORATION),
				vegetationConfigured);
	}

	private static void addUnique(java.util.List<Supplier<ConfiguredFeature<?, ?>>> features,
			ConfiguredFeature<?, ?> feature) {
		if (features.stream().noneMatch(existing -> existing.get() == feature)) {
			features.add(() -> feature);
		}
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
		provider.biome(BIOME_A, java.util.Collections.singletonMap(PROBE_GEOME, 100.0D));
		provider.biome(BIOME_B, java.util.Collections.singletonMap(PROBE_GEOME, 100.0D));
	}

	private void enableGeologyProbe(FMLServerAboutToStartEvent event) {
		Path profile = event.getServer().getWorldPath(FolderName.ROOT).resolve("serverconfig")
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
		BiomeSurfaceDefinition surfaceA = surface(DyeColor.PINK, DyeColor.WHITE,
				DyeColor.BLUE, ceiling ? DyeColor.ORANGE : null);
		BiomeSurfaceDefinition surfaceB = surface(DyeColor.LIME, DyeColor.YELLOW,
				DyeColor.LIGHT_BLUE, ceiling ? DyeColor.MAGENTA : null);
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
		if (event.getServer().overworld().getSeed() != 0L) {
			throw new IllegalStateException("Surface probe expected actual world seed 0 but found "
					+ event.getServer().overworld().getSeed());
		}

		Path marker = event.getServer().getWorldPath(FolderName.ROOT).resolve(MARKER_NAME);
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
		event.getServer().halt(false);
	}

	private static ServerWorld requireLevel(FMLServerStartedEvent event, RegistryKey<World> key) {
		ServerWorld level = event.getServer().getLevel(key);
		if (level == null) throw new IllegalStateException("Surface probe dimension is unavailable: " + key.location());
		if (level.getChunkSource().getGenerator() instanceof FlatChunkGenerator) {
			throw new IllegalStateException("Surface probe requires normal noise terrain: " + key.location());
		}
		return level;
	}

	private static AuditResult auditDimension(ServerWorld level, boolean roofed) {
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
		BlockPos.Mutable pos = new BlockPos.Mutable();

		for (int chunkZ = MINIMUM_CHUNK; chunkZ <= MAXIMUM_CHUNK; chunkZ++) {
			for (int chunkX = MINIMUM_CHUNK; chunkX <= MAXIMUM_CHUNK; chunkX++) {
				level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
				Chunk chunk = level.getChunk(chunkX, chunkZ);
				int chunkMinX = chunkX << 4;
				int chunkMinZ = chunkZ << 4;
				for (int localZ = 0; localZ < 16; localZ++) {
					for (int localX = 0; localX < 16; localX++) {
						int x = chunkMinX + localX;
						int z = chunkMinZ + localZ;
						int groundY = findMarkedGround(chunk, pos, x, z, 0, 256);
						Biome biome = level.getBiome(pos.set(x, groundY, z));
						ResourceLocation biomeId = biomeId(level, biome);
						Material material = material(biomeId, roofed);
						float expectedTemperature = BIOME_A.equals(biomeId) ? 1.35F : 0.7F;
						float expectedDownfall = BIOME_A.equals(biomeId) ? 0.15F : 0.8F;
						Biome biomeValue = biome;
						if (Float.compare(biomeValue.getBaseTemperature(), expectedTemperature) != 0
								|| Float.compare(biomeValue.getDownfall(), expectedDownfall) != 0) {
							throw new IllegalStateException("Provider climate changed for " + biomeId
									+ " at " + pos + ": temperature="
									+ biomeValue.getBaseTemperature() + ", downfall="
									+ biomeValue.getDownfall());
						}
						if (BIOME_A.equals(biomeId)) biomeA++; else biomeB++;
						if (x > (MINIMUM_CHUNK << 4)) {
							ResourceLocation westBiome = biomeId(level,
									level.getBiome(pos.set(x - 1, groundY, z)));
							if (!westBiome.equals(biomeId)) edgeChanges++;
						}
						if (z > (MINIMUM_CHUNK << 4)) {
							ResourceLocation northBiome = biomeId(level,
									level.getBiome(pos.set(x, groundY, z - 1)));
							if (!northBiome.equals(biomeId)) edgeChanges++;
						}
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
								assertBlock(chunk, pos, x, groundY - depth, z,
										Blocks.DIORITE.defaultBlockState(), "dynamic-biome geome rock");
								geology++;
							}
						}
						if (roofed) {
							ResourceLocation ceilingBiome = biomeId(level, level.getBiome(
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
				sentinels += auditSentinels(level, chunk, pos, chunkMinX, chunkMinZ);
			}
		}

		if (top != EXPECTED_COLUMNS - 9 || underwater != 9 || filler != EXPECTED_FILLER
				|| biomeA == 0 || biomeB == 0 || edgeChanges == 0 || sentinels != 9 * 4
				|| geology != (roofed ? 0 : EXPECTED_FILLER)
				|| (roofed && (ceiling != EXPECTED_COLUMNS || roofTop != EXPECTED_COLUMNS))) {
			throw new IllegalStateException("Incomplete surface audit for " + level.dimension().location()
					+ ": top=" + top + ", underwater=" + underwater + ", filler=" + filler
					+ ", biomeA=" + biomeA + ", biomeB=" + biomeB + ", edges=" + edgeChanges
					+ ", sentinels=" + sentinels + ", geology=" + geology
					+ ", ceiling=" + ceiling + ", roofTop=" + roofTop);
		}
		long aquiferFluid = roofed ? 0L : auditDynamicFluid(level);
		return new AuditResult(top, underwater, filler, geology, ceiling, roofTop,
				biomeA, biomeB, edgeChanges, sentinels, aquiferFluid);
	}

	private static long auditDynamicFluid(ServerWorld level) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		long water = 0L;
		for (int chunkZ = MINIMUM_CHUNK; chunkZ <= MAXIMUM_CHUNK; chunkZ++) {
			for (int chunkX = MINIMUM_CHUNK; chunkX <= MAXIMUM_CHUNK; chunkX++) {
				level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
				Chunk chunk = level.getChunk(chunkX, chunkZ);
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
			throw new IllegalStateException("Forge 36 dynamic fluid deposit produced no covered flowing-water blocks");
		}
		return water;
	}

	private static int auditSentinels(ServerWorld level, Chunk chunk,
			BlockPos.Mutable pos, int minX, int minZ) {
		int groundTree = findMarkedGround(chunk, pos, minX + 4, minZ + 4,
				0, 256);
		assertBlock(chunk, pos, minX + 4, groundTree + 1, minZ + 4,
				Blocks.OAK_LOG.defaultBlockState(), "tree log");
		assertBlock(chunk, pos, minX + 4, groundTree + 3, minZ + 4,
				Blocks.OAK_LEAVES.defaultBlockState(), "tree leaves");

		int groundVegetation = findMarkedGround(chunk, pos, minX + 6, minZ + 6,
				0, 256);
		assertBlock(chunk, pos, minX + 6, groundVegetation + 1, minZ + 6,
				Blocks.DIRT.defaultBlockState(), "vegetation substrate");
		assertBlock(chunk, pos, minX + 6, groundVegetation + 2, minZ + 6,
				Blocks.OAK_SAPLING.defaultBlockState(), "vegetation");

		int groundStructure = findMarkedGround(chunk, pos, minX + 8, minZ + 8,
				0, 256);
		assertBlock(chunk, pos, minX + 8, groundStructure + 1, minZ + 8,
				Blocks.GOLD_BLOCK.defaultBlockState(), "authored structure");

		int groundChest = findMarkedGround(chunk, pos, minX + 10, minZ + 10,
				0, 256);
		assertBlock(chunk, pos, minX + 10, groundChest + 1, minZ + 10,
				Blocks.CHEST.defaultBlockState(), "chest sentinel");
		if (!(level.getBlockEntity(pos.set(minX + 10, groundChest + 1, minZ + 10))
				instanceof ChestTileEntity)) {
			throw new IllegalStateException("Chest block entity data changed at " + pos);
		}
		ChestTileEntity chest = (ChestTileEntity) level.getBlockEntity(pos);
		if (chest == null
				|| chest.getItem(0).getItem() != Items.DIAMOND
				|| chest.getItem(0).getHoverName() == null
				|| !CHEST_ITEM_NAME.equals(chest.getItem(0).getHoverName().getString())) {
			throw new IllegalStateException("Chest block entity data changed at " + pos);
		}
		return 4;
	}

	private static int findMarkedGround(IChunk chunk, BlockPos.Mutable pos,
			int x, int z, int minY, int maxY) {
		for (int y = maxY - 1; y >= minY; y--) {
			if (chunk.getBlockState(pos.set(x, y, z)).is(concreteBlock(DyeColor.BLACK))) return y + 5;
		}
		throw new IllegalStateException("Independent surface marker missing at " + x + "," + z);
	}

	private static void assertBlock(IChunk chunk, BlockPos.Mutable pos,
			int x, int y, int z, BlockState expected, String purpose) {
		BlockState actual = chunk.getBlockState(pos.set(x, y, z));
		if (!actual.is(expected.getBlock())) {
			throw new IllegalStateException("Expected " + purpose + " " + expected.getBlock()
					+ " at " + pos + " but found " + actual.getBlock());
		}
	}

	private static Material material(ResourceLocation biome, boolean roofed) {
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
		if (color == DyeColor.WHITE) return Blocks.WHITE_CONCRETE;
		if (color == DyeColor.ORANGE) return Blocks.ORANGE_CONCRETE;
		if (color == DyeColor.MAGENTA) return Blocks.MAGENTA_CONCRETE;
		if (color == DyeColor.LIGHT_BLUE) return Blocks.LIGHT_BLUE_CONCRETE;
		if (color == DyeColor.YELLOW) return Blocks.YELLOW_CONCRETE;
		if (color == DyeColor.LIME) return Blocks.LIME_CONCRETE;
		if (color == DyeColor.PINK) return Blocks.PINK_CONCRETE;
		if (color == DyeColor.GRAY) return Blocks.GRAY_CONCRETE;
		if (color == DyeColor.LIGHT_GRAY) return Blocks.LIGHT_GRAY_CONCRETE;
		if (color == DyeColor.CYAN) return Blocks.CYAN_CONCRETE;
		if (color == DyeColor.PURPLE) return Blocks.PURPLE_CONCRETE;
		if (color == DyeColor.BLUE) return Blocks.BLUE_CONCRETE;
		if (color == DyeColor.BROWN) return Blocks.BROWN_CONCRETE;
		if (color == DyeColor.GREEN) return Blocks.GREEN_CONCRETE;
		if (color == DyeColor.RED) return Blocks.RED_CONCRETE;
		return Blocks.BLACK_CONCRETE;
	}

	private static ResourceLocation biomeId(ServerWorld level, Biome biome) {
		return level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getKey(biome);
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
			super(NoFeatureConfig.CODEC);
			this.stage = stage;
		}

		@Override
		public boolean place(ISeedReader world, ChunkGenerator chunkGenerator, Random random,
				BlockPos origin, NoFeatureConfig config) {
			IChunk chunk = world.getChunk(origin);
			if (stage == ProbeStage.TERRAIN) return prepareTerrain(world, chunk);
			if (stage == ProbeStage.STRUCTURE) return placeStructureSentinels(world, chunk);
			return placeVegetationSentinels(world, chunk);
		}
	}

	private static boolean prepareTerrain(ISeedReader world, IChunk chunk) {
		boolean roofed = world.getLevel().dimension().equals(ROOFED);
		if (roofed && chunk.getPos().x >= FLUID_PROBE_MIN_CHUNK_X
				&& chunk.getPos().x <= FLUID_PROBE_MAX_CHUNK_X
				&& chunk.getPos().z >= MINIMUM_CHUNK && chunk.getPos().z <= MAXIMUM_CHUNK) return false;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		Heightmap surfaceHeight = chunk.getOrCreateHeightmapUnprimed(
				Heightmap.Type.WORLD_SURFACE_WG);
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int x = minX + localX;
				int z = minZ + localZ;
				int groundY = chunk.getHeight(Heightmap.Type.WORLD_SURFACE_WG, localX, localZ);
				if (roofed) {
					while (groundY > 0
							&& solid(chunk.getBlockState(pos.set(x, groundY, z)))) groundY--;
				}
				while (groundY > 0
						&& !solid(chunk.getBlockState(pos.set(x, groundY, z)))) groundY--;
				if (!roofed && groundY <= 0) groundY = 64;
				chunk.setBlockState(pos.set(x, groundY, z), Blocks.GRASS_BLOCK.defaultBlockState(), false);
				surfaceHeight.update(localX, groundY, localZ, Blocks.GRASS_BLOCK.defaultBlockState());
				for (int depth = 1; depth <= 3; depth++) {
					chunk.setBlockState(pos.set(x, groundY - depth, z), Blocks.DIRT.defaultBlockState(), false);
				}
				if (!roofed) {
					for (int depth = 6; depth <= 60 && groundY - depth >= 1; depth++) {
						chunk.setBlockState(pos.set(x, groundY - depth, z), Blocks.END_STONE.defaultBlockState(), false);
					}
				}
				chunk.setBlockState(pos.set(x, groundY - 5, z),
						concreteBlock(DyeColor.BLACK).defaultBlockState(), false);
				if (roofed) {
					for (int openY = groundY + 1; openY < 256; openY++) {
						chunk.setBlockState(pos.set(x, openY, z), Blocks.AIR.defaultBlockState(), false);
					}
					for (int roofY = groundY + 8; roofY <= groundY + 10; roofY++) {
						chunk.setBlockState(pos.set(x, roofY, z), Blocks.STONE.defaultBlockState(), false);
					}
					surfaceHeight.update(localX, groundY + 10, localZ,
							Blocks.STONE.defaultBlockState());
				}
				if (localX == 1 && localZ == 1) {
					chunk.setBlockState(pos.set(x, groundY + 1, z), Blocks.WATER.defaultBlockState(), false);
					surfaceHeight.update(localX, groundY + 1, localZ, Blocks.WATER.defaultBlockState());
				}
			}
		}
		return true;
	}

	private static boolean solid(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty();
	}

	private static boolean placeStructureSentinels(ISeedReader world, IChunk chunk) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		int structureY = markedGroundOrMissing(chunk, pos, minX + 8, minZ + 8, world);
		if (structureY == Integer.MIN_VALUE) return false;
		world.setBlock(pos.set(minX + 8, structureY + 1, minZ + 8),
				Blocks.GOLD_BLOCK.defaultBlockState(), 2);
		int chestY = markedGround(chunk, pos, minX + 10, minZ + 10, world);
		world.setBlock(pos.set(minX + 10, chestY + 1, minZ + 10), Blocks.CHEST.defaultBlockState(), 2);
		if (world.getBlockEntity(pos) instanceof ChestTileEntity) {
			ChestTileEntity chest = (ChestTileEntity) world.getBlockEntity(pos);
			ItemStack sentinel = new ItemStack(Items.DIAMOND);
			sentinel.setHoverName(new StringTextComponent(CHEST_ITEM_NAME));
			chest.setItem(0, sentinel);
			chest.setChanged();
		}
		return true;
	}

	private static boolean placeVegetationSentinels(ISeedReader world, IChunk chunk) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		int treeY = markedGroundOrMissing(chunk, pos, minX + 4, minZ + 4, world);
		if (treeY == Integer.MIN_VALUE) return false;
		for (int y = 1; y <= 2; y++) {
			world.setBlock(pos.set(minX + 4, treeY + y, minZ + 4), Blocks.OAK_LOG.defaultBlockState(), 2);
		}
		world.setBlock(pos.set(minX + 4, treeY + 3, minZ + 4), Blocks.OAK_LEAVES.defaultBlockState(), 2);
		int vegetationY = markedGround(chunk, pos, minX + 6, minZ + 6, world);
		world.setBlock(pos.set(minX + 6, vegetationY + 1, minZ + 6), Blocks.DIRT.defaultBlockState(), 2);
		world.setBlock(pos.set(minX + 6, vegetationY + 2, minZ + 6), Blocks.OAK_SAPLING.defaultBlockState(), 2);
		return true;
	}

	private static int markedGround(IChunk chunk, BlockPos.Mutable pos,
			int x, int z, ISeedReader world) {
		return findMarkedGround(chunk, pos, x, z, 0, 256);
	}

	private static int markedGroundOrMissing(IChunk chunk, BlockPos.Mutable pos,
			int x, int z, ISeedReader world) {
		for (int y = 255; y >= 0; y--) {
			if (chunk.getBlockState(pos.set(x, y, z)).is(concreteBlock(DyeColor.BLACK))) return y + 5;
		}
		return Integer.MIN_VALUE;
	}

	private static final class Material {
		private final BlockState top;
		private final BlockState filler;
		private final BlockState underwater;
		private final BlockState ceiling;

		Material(BlockState top, BlockState filler, BlockState underwater, BlockState ceiling) {
			this.top = top;
			this.filler = filler;
			this.underwater = underwater;
			this.ceiling = ceiling;
		}

		BlockState top() { return top; }
		BlockState filler() { return filler; }
		BlockState underwater() { return underwater; }
		BlockState ceiling() { return ceiling; }
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
