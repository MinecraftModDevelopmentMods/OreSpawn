package zone.moddev.mc.orespawn.testmod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.LinkedHashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import zone.moddev.mc.orespawn.api.BiomePlacementMode;
import zone.moddev.mc.orespawn.api.BiomeRegionSize;
import zone.moddev.mc.orespawn.api.BiomeReplacementScope;
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.OrePatternType;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.OreSpawnBiomes;
import zone.moddev.mc.orespawn.api.OreSpawnBiomes.BiomeReference;
import zone.moddev.mc.orespawn.api.OreSpawnBiomes.BiomeRegistrar;
import zone.moddev.mc.orespawn.api.OreSpawnPatternRegistry;
import zone.moddev.mc.orespawn.api.ProviderStatus;
import zone.moddev.mc.orespawn.api.StandardPatternSettings;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import zone.moddev.mc.orespawn.api.WorldgenProvider.BiomeSurfaceDefinition;
import zone.moddev.mc.orespawn.api.WorldgenProvider.TerrainDimensionDefinition;
import zone.moddev.mc.orespawn.worldgen.SurfaceProbeSpringBridge;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderFlat;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.FMLCommonHandler;

/** Independent Forge 1.10 provider-surface and exact-biome regression fixture. */
@Mod(modid = SurfaceProbeTestMod.MODID, name = "OreSpawn Surface Probe",
		version = "1.0.0", acceptedMinecraftVersions = "[1.10.2]",
		dependencies = "required-after:orespawn@[4.0.6,5.0.0)")
public final class SurfaceProbeTestMod {
	static final String MODID = "surfaceprobe";
	private static final Logger LOGGER = LogManager.getLogger();
	private static final ResourceLocation END = new ResourceLocation("minecraft", "the_end");
	private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");
	private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
	private static final ResourceLocation BIOME_A = new ResourceLocation(MODID, "surface_a");
	private static final ResourceLocation BIOME_B = new ResourceLocation(MODID, "surface_b");
	private static final ResourceLocation PROBE_GEOME = new ResourceLocation(MODID, "exact_biome");
	private static final ResourceLocation SPRING_ROCK = new ResourceLocation(MODID, "rock/spring_host");
	private static final BlockPos SPRING_POS = new BlockPos(1128, 32, 1128);
	private static final int MIN_CHUNK = 63;
	private static final int MAX_CHUNK = 65;
	private static final int COLUMNS = 9 * 16 * 16;
	private static final int FILLER = COLUMNS * 3;
	private static final int GROUND_Y = 200;
	private static final int MARKER_Y = GROUND_Y - 5;
	private static final int ROOF_UNDERSIDE_Y = 220;
	private static final int ROOF_TOP_Y = 222;
	private static final int GEOLOGY_MIN_Y = 20;
	private static final int GEOLOGY_MAX_Y = 22;
	private static final String PHASE_PROPERTY = "surfaceprobe.integrationPhase";
	private static final String MARKER_NAME = "surfaceprobe-integration.properties";
	private static final String CHEST_ITEM_NAME = "surfaceprobe sentinel";
	private static final ResourceLocation[] BUILT_IN_GEOMES = {
			new ResourceLocation("orespawn", "stable_craton"),
			new ResourceLocation("orespawn", "mountain_belt"),
			new ResourceLocation("orespawn", "volcanic_arc"),
			new ResourceLocation("orespawn", "sedimentary_basin"),
			new ResourceLocation("orespawn", "coastal_shelf"),
			new ResourceLocation("orespawn", "arid_basin"),
			new ResourceLocation("orespawn", "wetland_basin"),
			new ResourceLocation("orespawn", "glacial_highland")
	};

	private static final OrePatternType EXTERNAL_PATTERN = OrePatternType.create(
			StandardPatternSettings.CODEC, settings -> context -> false)
			.setRegistryName(MODID, "external_probe");

	private final BiomeRegistrar registrar = OreSpawnBiomes.registrar(MODID);
	private final BiomeReference surfaceA = OreSpawnBiomes.blankAndRegister(registrar,
			"surface_a", properties -> configure(properties, 1.35F, 0.15F));
	private final BiomeReference surfaceB = OreSpawnBiomes.blankAndRegister(registrar,
			"surface_b", properties -> configure(properties, 0.7F, 0.8F));
	private final Set<String> preparedTerrain = new LinkedHashSet<>();

	public SurfaceProbeTestMod() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	private static void configure(Biome.BiomeProperties properties,
			float temperature, float rainfall) {
		properties.setBaseHeight(0.1F).setHeightVariation(0.2F)
				.setTemperature(temperature).setRainfall(rainfall)
				.setWaterColor(4159204);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void registerBiomes(RegistryEvent.Register<Biome> event) {
		surfaceA.get().theBiomeDecorator = new ProbeDecorator();
		surfaceB.get().theBiomeDecorator = new ProbeDecorator();
		BiomeDictionary.registerBiomeType(surfaceA.get(), BiomeDictionary.Type.HOT, BiomeDictionary.Type.DRY);
		BiomeDictionary.registerBiomeType(surfaceB.get(), BiomeDictionary.Type.HOT, BiomeDictionary.Type.WET);
	}

	@SubscribeEvent
	public void registerPatterns(RegistryEvent.Register<OrePatternType> event) {
		event.getRegistry().register(EXTERNAL_PATTERN);
	}

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		GameRegistry.registerWorldGenerator(new ProbeGenerator(), 1000);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void placeControlledTerrain(DecorateBiomeEvent.Pre event) {
		World world = event.getWorld();
		int chunkX = event.getPos().getX() >> 4;
		int chunkZ = event.getPos().getZ() >> 4;
		if ((world.provider.getDimension() != 1 && world.provider.getDimension() != -1)
				|| chunkX < MIN_CHUNK || chunkX > MAX_CHUNK
				|| chunkZ < MIN_CHUNK || chunkZ > MAX_CHUNK) return;
		String key = world.provider.getDimension() + ":" + chunkX + ":" + chunkZ;
		if (!preparedTerrain.add(key)) return;
		Chunk chunk = world.getChunkProvider().provideChunk(chunkX, chunkZ);
		ProbeGenerator.placeTerrain(chunk, chunkX << 4, chunkZ << 4,
				world.provider.getDimension() == -1);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void verifySurfaceStage(DecorateBiomeEvent.Pre event) {
		World world = event.getWorld();
		int chunkX = event.getPos().getX() >> 4;
		int chunkZ = event.getPos().getZ() >> 4;
		if ((world.provider.getDimension() != 1 && world.provider.getDimension() != -1)
				|| chunkX < MIN_CHUNK || chunkX > MAX_CHUNK
				|| chunkZ < MIN_CHUNK || chunkZ > MAX_CHUNK) return;
		BlockPos pos = new BlockPos(chunkX << 4, GROUND_Y, chunkZ << 4);
		if (world.getBlockState(pos).getBlock() == Blocks.GRASS) {
			throw new IllegalStateException("OreSpawn surface did not run within the native decoration stage at "
					+ pos + " in dimension " + world.provider.getDimension());
		}
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		WorldgenProvider.Builder provider = WorldgenProvider.builder(MODID, 1);
		addGeology(provider);
		// This stable palette id makes seed zero select both fixture biomes on
		// opposite sides of the 1,024-block Tiny-region boundary.
		addPalette(provider, "end_palette_1", END, false);
		addPalette(provider, "nether_palette_1", NETHER, true);
		if (!OreSpawnApi.enqueue(provider.build())) {
			throw new IllegalStateException("Could not enqueue the surfaceprobe provider");
		}
	}

	private static void addGeology(WorldgenProvider.Builder provider) {
		provider.geome(PROBE_GEOME, geome -> geome.baseWeight(0.0D)
				.familyWeight(GeologyFamily.SEDIMENTARY, 1.0D));
		provider.rock(new ResourceLocation(MODID, "rock/exact_biome"), id(Blocks.PRISMARINE),
				GeologyFamily.SEDIMENTARY, rock -> {
					rock.dimension(END).yRange(0, 255).geomeWeight(PROBE_GEOME, 1.0D);
					for (ResourceLocation geome : BUILT_IN_GEOMES) rock.geomeWeight(geome, 0.0D);
				});
		provider.rock(new ResourceLocation(MODID, "rock/fallback"), id(Blocks.NETHERRACK),
				GeologyFamily.SEDIMENTARY, rock -> {
					rock.dimension(END).yRange(0, 255).geomeWeight(PROBE_GEOME, 0.0D);
					for (ResourceLocation geome : BUILT_IN_GEOMES) rock.geomeWeight(geome, 1.0D);
				});
		provider.rock(SPRING_ROCK, id(Blocks.PURPUR_BLOCK),
				GeologyFamily.IGNEOUS_INTRUSIVE, rock -> {
					rock.dimension(OVERWORLD).yRange(0, 0).weight(0.000001D);
					for (ResourceLocation geome : BUILT_IN_GEOMES) rock.geomeWeight(geome, 1.0D);
				});
		provider.biome(BIOME_A, Collections.singletonMap(PROBE_GEOME, 100.0D));
		provider.biome(BIOME_B, Collections.singletonMap(PROBE_GEOME, 100.0D));
		provider.terrainDimension(TerrainDimensionDefinition.builder(END)
				.biomeNamespace(MODID).hostBlock(id(Blocks.END_STONE)).build());
	}

	private static void addPalette(WorldgenProvider.Builder provider, String name,
			ResourceLocation dimension, boolean ceiling) {
		BiomeSurfaceDefinition a = surface(Blocks.EMERALD_BLOCK, Blocks.QUARTZ_BLOCK,
				Blocks.LAPIS_BLOCK, ceiling ? Blocks.IRON_BLOCK : null);
		BiomeSurfaceDefinition b = surface(Blocks.DIAMOND_BLOCK, Blocks.REDSTONE_BLOCK,
				Blocks.COAL_BLOCK, ceiling ? Blocks.GOLD_BLOCK : null);
		provider.biomePalette(new ResourceLocation(MODID, name), dimension,
				palette -> palette.mode(BiomePlacementMode.REPLACE)
						.scope(BiomeReplacementScope.MINECRAFT_ONLY)
						.regionSize(BiomeRegionSize.TINY).coverage(1.0D).fallbackWeight(0.0D)
						.biome(BIOME_A, biome -> biome.weight(1.0D)
								.temperature(-2.0D, 2.0D).downfall(0.0D, 1.0D).surface(a))
						.biome(BIOME_B, biome -> biome.weight(1.0D)
								.temperature(-2.0D, 2.0D).downfall(0.0D, 1.0D).surface(b)));
	}

	private static BiomeSurfaceDefinition surface(Block top, Block filler,
			Block underwater, Block ceiling) {
		BiomeSurfaceDefinition.Builder builder = BiomeSurfaceDefinition.builder()
				.topBlock(id(top)).fillerBlock(id(filler)).underwaterBlock(id(underwater))
				.fillerDepth(3);
		if (ceiling != null) builder.ceilingBlock(id(ceiling));
		return builder.build();
	}

	private static ResourceLocation id(Block block) {
		ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
		if (id == null) throw new IllegalStateException("Unregistered fixture block " + block);
		return id;
	}

	@EventHandler
	public void serverStarted(FMLServerStartedEvent event) {
		String phase = System.getProperty(PHASE_PROPERTY, "").trim();
		if (!"fresh".equals(phase) && !"reload".equals(phase)) {
			throw new IllegalStateException("Missing or invalid " + PHASE_PROPERTY + ": " + phase);
		}
		if (OreSpawnApi.getProviderStatus(MODID) != ProviderStatus.ACTIVE) {
			throw new IllegalStateException("surfaceprobe provider is not active");
		}
		verifyPatterns();
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		WorldServer overworld = server.worldServerForDimension(0);
		if (overworld == null || overworld.getSeed() != 0L) {
			throw new IllegalStateException("surfaceprobe requires seed token zsjpxah (hash zero)");
		}
		Path marker = worldRoot(server).resolve(MARKER_NAME);
		Properties previous = "reload".equals(phase) ? read(marker) : null;
		if ("fresh".equals(phase) && Files.exists(marker)) {
			throw new IllegalStateException("Fresh surfaceprobe retained an old marker");
		}
		Map<String, Audit> results = new LinkedHashMap<>();
		results.put("end", audit(requireWorld(server, 1), false));
		results.put("nether", audit(requireWorld(server, -1), true));
		ResourceLocation spring = auditSpring(overworld, phase);
		Properties current = properties(overworld.getSeed(), results, spring);
		if (previous == null) {
			write(marker, current);
		} else {
			for (String key : current.stringPropertyNames()) {
				if (!current.getProperty(key).equals(previous.getProperty(key))) {
					throw new IllegalStateException("Reload changed " + key + ": expected "
							+ previous.getProperty(key) + " but found " + current.getProperty(key));
				}
			}
			previous.setProperty("reload_verified", "true");
			write(marker, previous);
		}
		LOGGER.info("SURFACEPROBE PASS phase={} end={} nether={}",
				phase, results.get("end"), results.get("nether"));
		server.initiateShutdown();
	}

	private static WorldServer requireWorld(MinecraftServer server, int dimension) {
		WorldServer world = server.worldServerForDimension(dimension);
		if (world == null) {
			DimensionManager.initDimension(dimension);
			world = DimensionManager.getWorld(dimension);
		}
		if (world == null) throw new IllegalStateException("Missing dimension " + dimension);
		if (world.getChunkProvider().chunkGenerator instanceof ChunkProviderFlat) {
			throw new IllegalStateException("surfaceprobe requires normal-noise dimension " + dimension);
		}
		return world;
	}

	private static Audit audit(WorldServer world, boolean roofed) {
		long dry = 0, wet = 0, filler = 0, geology = 0, ceiling = 0, roof = 0;
		int biomeA = 0, biomeB = 0, edges = 0, sentinels = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		loadPopulationBorder(world);
		for (int chunkZ = MIN_CHUNK; chunkZ <= MAX_CHUNK; chunkZ++) {
			for (int chunkX = MIN_CHUNK; chunkX <= MAX_CHUNK; chunkX++) {
				Chunk chunk = world.getChunkProvider().provideChunk(chunkX, chunkZ);
				if (!chunk.isTerrainPopulated()) {
					throw new IllegalStateException("Normal chunk population did not complete at "
							+ chunkX + "," + chunkZ);
				}
				int minX = chunkX << 4, minZ = chunkZ << 4;
				for (int localZ = 0; localZ < 16; localZ++) {
					for (int localX = 0; localX < 16; localX++) {
						int x = minX + localX, z = minZ + localZ;
						int ground = markedGround(chunk, cursor, x, z);
						Biome biome = world.getBiome(cursor.setPos(x, ground, z));
						ResourceLocation biomeId = biome.getRegistryName();
						Material material = material(biomeId, roofed);
						float temperature = BIOME_A.equals(biomeId) ? 1.35F : 0.7F;
						float rainfall = BIOME_A.equals(biomeId) ? 0.15F : 0.8F;
						if (Float.compare(biome.getTemperature(), temperature) != 0
								|| Float.compare(biome.getRainfall(), rainfall) != 0) {
							throw new IllegalStateException("Climate mismatch for " + biomeId + " at " + cursor);
						}
						if (BIOME_A.equals(biomeId)) biomeA++; else biomeB++;
						if (x > (MIN_CHUNK << 4)
								&& !biomeId.equals(world.getBiome(cursor.setPos(x - 1, ground, z)).getRegistryName())) edges++;
						if (z > (MIN_CHUNK << 4)
								&& !biomeId.equals(world.getBiome(cursor.setPos(x, ground, z - 1)).getRegistryName())) edges++;
						boolean underwater = localX == 1 && localZ == 1;
						Block expectedTop = underwater ? material.underwater : material.top;
						assertBlock(chunk, cursor, x, ground, z,
								expectedTop, "surface top");
						if (underwater) wet++; else dry++;
						for (int depth = 1; depth <= 3; depth++) {
							assertBlock(chunk, cursor, x, ground - depth, z, material.filler,
									"surface filler " + depth);
							filler++;
						}
						if (!roofed) {
							for (int y = GEOLOGY_MIN_Y; y <= GEOLOGY_MAX_Y; y++) {
								assertBlock(chunk, cursor, x, y, z, Blocks.PRISMARINE, "exact-biome geology");
								geology++;
							}
						} else {
							assertBlock(chunk, cursor, x, ROOF_UNDERSIDE_Y, z, material.ceiling, "roof underside");
							assertBlock(chunk, cursor, x, ROOF_TOP_Y, z, Blocks.STONE, "roof top");
							ceiling++; roof++;
						}
					}
				}
				sentinels += auditSentinels(world, minX, minZ);
			}
		}
		if (dry != COLUMNS - 9 || wet != 9 || filler != FILLER || biomeA == 0 || biomeB == 0
				|| edges == 0 || sentinels != 36 || geology != (roofed ? 0 : FILLER)
				|| (roofed && (ceiling != COLUMNS || roof != COLUMNS))) {
			throw new IllegalStateException("Incomplete surface audit: dry=" + dry + ", wet=" + wet
					+ ", filler=" + filler + ", biomeA=" + biomeA + ", biomeB=" + biomeB
					+ ", edges=" + edges + ", sentinels=" + sentinels + ", geology=" + geology
					+ ", ceiling=" + ceiling + ", roof=" + roof);
		}
		return new Audit(dry, wet, filler, geology, ceiling, roof, biomeA, biomeB, edges, sentinels);
	}

	private static void loadPopulationBorder(WorldServer world) {
		for (int chunkZ = MIN_CHUNK - 1; chunkZ <= MAX_CHUNK + 1; chunkZ++) {
			for (int chunkX = MIN_CHUNK - 1; chunkX <= MAX_CHUNK + 1; chunkX++) {
				world.getChunkProvider().provideChunk(chunkX, chunkZ);
			}
		}
	}

	private static int auditSentinels(WorldServer world, int minX, int minZ) {
		assertBlock(world, minX + 4, GROUND_Y + 1, minZ + 4, Blocks.LOG, "tree log");
		assertBlock(world, minX + 4, GROUND_Y + 4, minZ + 4, Blocks.LEAVES, "tree leaves");
		assertBlock(world, minX + 6, GROUND_Y + 1, minZ + 6, Blocks.DIRT, "vegetation substrate");
		assertBlock(world, minX + 6, GROUND_Y + 2, minZ + 6, Blocks.SAPLING, "vegetation");
		assertBlock(world, minX + 8, GROUND_Y + 1, minZ + 8, Blocks.BRICK_BLOCK, "structure");
		BlockPos chestPos = new BlockPos(minX + 10, GROUND_Y + 1, minZ + 10);
		assertBlock(world, chestPos.getX(), chestPos.getY(), chestPos.getZ(), Blocks.CHEST, "chest");
		if (!(world.getTileEntity(chestPos) instanceof TileEntityChest)) {
			throw new IllegalStateException("Chest block entity missing at " + chestPos);
		}
		ItemStack stack = ((TileEntityChest) world.getTileEntity(chestPos)).getStackInSlot(0);
		if (stack.getItem() != Items.DIAMOND || !CHEST_ITEM_NAME.equals(stack.getDisplayName())) {
			throw new IllegalStateException("Chest inventory changed at " + chestPos);
		}
		return 4;
	}

	private static ResourceLocation auditSpring(WorldServer world, String phase) {
		if (!SurfaceProbeSpringBridge.recognizesProviderRock(Blocks.PURPUR_BLOCK)) {
			throw new IllegalStateException("Provider rock is not accepted by the spring compatibility path");
		}
		world.getChunkProvider().provideChunk(SPRING_POS.getX() >> 4, SPRING_POS.getZ() >> 4);
		if ("fresh".equals(phase)) {
			for (BlockPos rock : Arrays.asList(SPRING_POS.up(), SPRING_POS.down(),
					SPRING_POS.west(), SPRING_POS.east(), SPRING_POS.north())) {
				world.setBlockState(rock, Blocks.PURPUR_BLOCK.getDefaultState(), 2);
			}
			world.setBlockToAir(SPRING_POS);
			world.setBlockToAir(SPRING_POS.south());
			if (!SurfaceProbeSpringBridge.placeWater(world, SPRING_POS)) {
				throw new IllegalStateException("Provider-rock spring was rejected");
			}
		}
		Block block = world.getBlockState(SPRING_POS).getBlock();
		if (block != Blocks.FLOWING_WATER && block != Blocks.WATER) {
			throw new IllegalStateException("Provider-rock spring changed across " + phase + ": " + block);
		}
		return id(block);
	}

	private static void verifyPatterns() {
		for (String name : Arrays.asList("default", "vein", "normal_cloud", "precision",
				"clusters", "underfluids")) {
			if (OreSpawnPatternRegistry.registry().getValue(new ResourceLocation("orespawn", name)) == null) {
				throw new IllegalStateException("Missing built-in ore pattern " + name);
			}
		}
		if (OreSpawnPatternRegistry.registry().getValue(
				new ResourceLocation(MODID, "external_probe")) != EXTERNAL_PATTERN) {
			throw new IllegalStateException("Fixture external ore pattern did not register");
		}
	}

	private static Material material(ResourceLocation biome, boolean roofed) {
		if (BIOME_A.equals(biome)) return new Material(Blocks.EMERALD_BLOCK, Blocks.QUARTZ_BLOCK,
				Blocks.LAPIS_BLOCK, roofed ? Blocks.IRON_BLOCK : null);
		if (BIOME_B.equals(biome)) return new Material(Blocks.DIAMOND_BLOCK, Blocks.REDSTONE_BLOCK,
				Blocks.COAL_BLOCK, roofed ? Blocks.GOLD_BLOCK : null);
		throw new IllegalStateException("Unexpected provider biome " + biome);
	}

	private static int markedGround(Chunk chunk, BlockPos.MutableBlockPos cursor, int x, int z) {
		for (int y = 255; y >= 0; y--) {
			if (chunk.getBlockState(cursor.setPos(x, y, z)).getBlock() == Blocks.OBSIDIAN) return y + 5;
		}
		throw new IllegalStateException("Surface marker missing at " + x + "," + z);
	}

	private static void assertBlock(Chunk chunk, BlockPos.MutableBlockPos cursor,
			int x, int y, int z, Block expected, String purpose) {
		Block actual = chunk.getBlockState(cursor.setPos(x, y, z)).getBlock();
		if (actual != expected) throw new IllegalStateException("Expected " + purpose + " " + expected
				+ " at " + cursor + " but found " + actual);
	}

	private static void assertBlock(World world, int x, int y, int z, Block expected, String purpose) {
		BlockPos pos = new BlockPos(x, y, z);
		Block actual = world.getBlockState(pos).getBlock();
		if (actual != expected) throw new IllegalStateException("Expected " + purpose + " " + expected
				+ " at " + pos + " but found " + actual);
	}

	private static Path worldRoot(MinecraftServer server) {
		return server.getActiveAnvilConverter().getFile(server.getFolderName(), "level.dat")
				.toPath().toAbsolutePath().normalize().getParent();
	}

	private static Properties properties(long seed, Map<String, Audit> audits, ResourceLocation spring) {
		Properties properties = new Properties();
		properties.setProperty("seed", Long.toString(seed));
		properties.setProperty("dimensions", Integer.toString(audits.size()));
		properties.setProperty("columns_per_dimension", Integer.toString(COLUMNS));
		properties.setProperty("spring", spring.toString());
		for (Map.Entry<String, Audit> entry : audits.entrySet()) entry.getValue().put(properties, entry.getKey());
		return properties;
	}

	private static Properties read(Path path) {
		Properties properties = new Properties();
		try (BufferedReader reader = Files.newBufferedReader(path)) { properties.load(reader); }
		catch (IOException exception) { throw new IllegalStateException("Could not read " + path, exception); }
		return properties;
	}

	private static void write(Path path, Properties properties) {
		try {
			Files.createDirectories(path.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(path)) {
				properties.store(writer, "OreSpawn Forge 1.10 surface integration");
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not write " + path, exception);
		}
	}

	private static final class ProbeGenerator implements IWorldGenerator {
		@Override
		public void generate(Random random, int chunkX, int chunkZ, World world,
				IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
			if ((world.provider.getDimension() != 1 && world.provider.getDimension() != -1)
					|| chunkX < MIN_CHUNK || chunkX > MAX_CHUNK
					|| chunkZ < MIN_CHUNK || chunkZ > MAX_CHUNK) return;
			placeSentinels(world, chunkX << 4, chunkZ << 4);
		}

		private static void placeTerrain(Chunk chunk, int minX, int minZ, boolean roofed) {
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int localZ = 0; localZ < 16; localZ++) {
				for (int localX = 0; localX < 16; localX++) {
					int x = minX + localX, z = minZ + localZ;
					for (int y = GROUND_Y + 1; y < 256; y++)
						chunk.setBlockState(pos.setPos(x, y, z), Blocks.AIR.getDefaultState());
					chunk.setBlockState(pos.setPos(x, MARKER_Y, z), Blocks.OBSIDIAN.getDefaultState());
					if (!roofed) for (int y = GEOLOGY_MIN_Y; y <= GEOLOGY_MAX_Y; y++)
						chunk.setBlockState(pos.setPos(x, y, z), Blocks.END_STONE.getDefaultState());
					chunk.setBlockState(pos.setPos(x, GROUND_Y - 4, z), Blocks.STONE.getDefaultState());
					for (int y = GROUND_Y - 3; y < GROUND_Y; y++)
						chunk.setBlockState(pos.setPos(x, y, z), Blocks.DIRT.getDefaultState());
					chunk.setBlockState(pos.setPos(x, GROUND_Y, z), Blocks.GRASS.getDefaultState());
					if (localX == 1 && localZ == 1)
						chunk.setBlockState(pos.setPos(x, GROUND_Y + 1, z), Blocks.WATER.getDefaultState());
					if (roofed) for (int y = ROOF_UNDERSIDE_Y; y <= ROOF_TOP_Y; y++)
						chunk.setBlockState(pos.setPos(x, y, z), Blocks.STONE.getDefaultState());
				}
			}
			chunk.setChunkModified();
		}

		private static void placeSentinels(World world, int minX, int minZ) {
			world.setBlockState(new BlockPos(minX + 4, GROUND_Y + 1, minZ + 4), Blocks.LOG.getDefaultState(), 2);
			world.setBlockState(new BlockPos(minX + 4, GROUND_Y + 2, minZ + 4), Blocks.LOG.getDefaultState(), 2);
			world.setBlockState(new BlockPos(minX + 4, GROUND_Y + 3, minZ + 4), Blocks.LOG.getDefaultState(), 2);
			world.setBlockState(new BlockPos(minX + 4, GROUND_Y + 4, minZ + 4), Blocks.LEAVES.getDefaultState(), 2);
			world.setBlockState(new BlockPos(minX + 6, GROUND_Y + 1, minZ + 6), Blocks.DIRT.getDefaultState(), 2);
			world.setBlockState(new BlockPos(minX + 6, GROUND_Y + 2, minZ + 6), Blocks.SAPLING.getDefaultState(), 2);
			world.setBlockState(new BlockPos(minX + 8, GROUND_Y + 1, minZ + 8), Blocks.BRICK_BLOCK.getDefaultState(), 2);
			BlockPos chestPos = new BlockPos(minX + 10, GROUND_Y + 1, minZ + 10);
			world.setBlockState(chestPos, Blocks.CHEST.getDefaultState(), 2);
			if (!(world.getTileEntity(chestPos) instanceof TileEntityChest))
				throw new IllegalStateException("Fixture chest failed at " + chestPos);
			ItemStack stack = new ItemStack(Items.DIAMOND);
			stack.setStackDisplayName(CHEST_ITEM_NAME);
			((TileEntityChest) world.getTileEntity(chestPos)).setInventorySlotContents(0, stack);
		}
	}

	private static final class Material {
		final Block top, filler, underwater, ceiling;
		Material(Block top, Block filler, Block underwater, Block ceiling) {
			this.top = top; this.filler = filler; this.underwater = underwater; this.ceiling = ceiling;
		}
	}

	private static final class ProbeDecorator extends BiomeDecorator {
		@Override
		public void decorate(World world, Random random, Biome biome, BlockPos pos) {
			MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Pre(world, random, pos));
			MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Post(world, random, pos));
		}
	}

	private static final class Audit {
		final long dry, wet, filler, geology, ceiling, roof;
		final int biomeA, biomeB, edges, sentinels;
		Audit(long dry, long wet, long filler, long geology, long ceiling, long roof,
				int biomeA, int biomeB, int edges, int sentinels) {
			this.dry = dry; this.wet = wet; this.filler = filler; this.geology = geology;
			this.ceiling = ceiling; this.roof = roof; this.biomeA = biomeA;
			this.biomeB = biomeB; this.edges = edges; this.sentinels = sentinels;
		}
		void put(Properties properties, String prefix) {
			properties.setProperty(prefix + ".dry", Long.toString(dry));
			properties.setProperty(prefix + ".wet", Long.toString(wet));
			properties.setProperty(prefix + ".filler", Long.toString(filler));
			properties.setProperty(prefix + ".geology", Long.toString(geology));
			properties.setProperty(prefix + ".ceiling", Long.toString(ceiling));
			properties.setProperty(prefix + ".roof", Long.toString(roof));
			properties.setProperty(prefix + ".biome_a", Integer.toString(biomeA));
			properties.setProperty(prefix + ".biome_b", Integer.toString(biomeB));
			properties.setProperty(prefix + ".edges", Integer.toString(edges));
			properties.setProperty(prefix + ".sentinels", Integer.toString(sentinels));
		}
		@Override public String toString() {
			return "Audit{dry=" + dry + ", wet=" + wet + ", filler=" + filler
					+ ", geology=" + geology + ", ceiling=" + ceiling + ", sentinels=" + sentinels + "}";
		}
	}
}
