package zone.moddev.mc.orespawn.migrationtest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;

import zone.moddev.mc.orespawn.worldgen.OreRetrogenManager;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.event.world.ChunkDataEvent;

/** Build-only OS3 migration driver; never packaged with OreSpawn. */
@Mod(modid = MigrationProbeTestMod.MODID, name = "OreSpawn Migration Probe", version = "1")
public final class MigrationProbeTestMod {
	static final String MODID = "migrationprobe";
	private static final int MIN_SOURCE_CHUNK = -4;
	private static final int MAX_SOURCE_CHUNK = 4;
	private static final int MIN_RESERVED_CHUNK = 16;
	private static final int MAX_RESERVED_CHUNK = 20;

	private MinecraftServer server;
	private String phase;
	private String family;
	private int queuedAtStart;
	private int settledTicks;
	private boolean complete;
	private final Set<String> markedSourceLoads = new HashSet<>();
	private final Set<String> unmarkedSourceLoads = new HashSet<>();
	private final List<Ticket> tickets = new ArrayList<>();

	public MigrationProbeTestMod() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		ForgeChunkManager.setForcedChunkLoadingCallback(this, (loaded, world) -> { });
	}

	@EventHandler
	public void serverStarted(FMLServerStartedEvent event) {
		server = FMLCommonHandler.instance().getMinecraftServerInstance();
		phase = System.getProperty("orespawn.migrationPhase", "").trim();
		family = System.getProperty("orespawn.migrationFamily", "").trim();
		if (( !"fresh".equals(phase) && !"reload".equals(phase)) || family.isEmpty()) {
			throw new IllegalStateException("Missing migration family/phase");
		}
		for (int dimension : new int[] { 0, -1, 1 }) {
			if (dimension != 0) DimensionManager.keepDimensionLoaded(dimension, true);
			WorldServer world = requireWorld(server, dimension);
			forceRegion(world, MIN_SOURCE_CHUNK, MAX_SOURCE_CHUNK);
			forceRegion(world, MIN_RESERVED_CHUNK - 1, MAX_RESERVED_CHUNK + 1);
			loadRegion(world, MIN_SOURCE_CHUNK, MAX_SOURCE_CHUNK);
		}
		if ("fresh".equals(phase) && !isRetrogenFixture()) {
			try {
				compareSourceAudit(worldRoot(), auditSourceWorld());
			} catch (IOException failure) {
				throw new IllegalStateException("Immediate OS3 source audit failed", failure);
			}
		}
		queuedAtStart = OreRetrogenManager.queuedCount();
		for (int dimension : new int[] { 0, -1, 1 }) {
			loadRegion(requireWorld(server, dimension), MIN_RESERVED_CHUNK - 1,
					MAX_RESERVED_CHUNK + 1);
		}
	}

	@SubscribeEvent
	public void chunkDataLoad(ChunkDataEvent.Load event) {
		if (!(event.getWorld() instanceof WorldServer)) return;
		int chunkX = event.getChunk().getPos().x;
		int chunkZ = event.getChunk().getPos().z;
		if (chunkX < MIN_SOURCE_CHUNK || chunkX > MAX_SOURCE_CHUNK
				|| chunkZ < MIN_SOURCE_CHUNK || chunkZ > MAX_SOURCE_CHUNK) return;
		String key = ((WorldServer) event.getWorld()).provider.getDimension() + ":" + chunkX + ":" + chunkZ;
		NBTTagCompound marker = event.getData().getCompoundTag("OreSpawn");
		if (marker.getInteger("generation_revision") == 1) markedSourceLoads.add(key);
		else unmarkedSourceLoads.add(key);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void serverTick(TickEvent.ServerTickEvent event) {
		if (complete || server == null || event.phase != TickEvent.Phase.END) return;
		if (OreRetrogenManager.queuedCount() != 0 || ++settledTicks < 100) return;
		complete = true;
		try {
			finish();
		} catch (IOException failure) {
			throw new IllegalStateException("Migration audit failed", failure);
		}
	}

	private void finish() throws IOException {
		boolean retrogenFixture = isRetrogenFixture();
		boolean unexpectedQueue = "reload".equals(phase) ? queuedAtStart != 0
				: retrogenFixture ? queuedAtStart == 0 : queuedAtStart != 0;
		if (unexpectedQueue) {
			throw new IllegalStateException("Unexpected retrogen queue for " + family + ": " + queuedAtStart);
		}
		Path root = worldRoot();
		Path marker = root.resolve("orespawn4-migration-probe.properties");
		Properties values = Files.isRegularFile(marker) ? read(marker) : new Properties();
		if ("fresh".equals(phase) && values.containsKey("fresh_complete")) {
			throw new IllegalStateException("Fresh migration phase reused an already migrated world");
		}

		Properties audit = auditWorld();
		Path freshAudit = root.resolve("orespawn4-migration-fresh-audit.properties");
		if ("fresh".equals(phase)) {
			write(freshAudit, audit, "OreSpawn 4 migration fresh semantic audit");
		} else {
			Properties expected = read(freshAudit);
			if (!expected.equals(audit)) {
				write(root.resolve("orespawn4-migration-reload-failed-audit.properties"), audit,
						"OreSpawn 4 failed reload semantic audit");
				throw new IllegalStateException("Fresh/reload semantic migration audit differs: "
						+ firstDifference(expected, audit) + "; marked=" + markedSourceLoads.size()
						+ "; unmarked=" + unmarkedSourceLoads.size() + "; queued=" + queuedAtStart);
			}
		}

		values.setProperty("family", family);
		values.setProperty("seed", Long.toString(server.getWorld(0).getSeed()));
		values.setProperty("source_chunks", MIN_SOURCE_CHUNK + ".." + MAX_SOURCE_CHUNK);
		values.setProperty("reserved_chunks", MIN_RESERVED_CHUNK + ".." + MAX_RESERVED_CHUNK);
		values.setProperty("retrogen_queued_" + phase, Integer.toString(queuedAtStart));
		values.setProperty("source_marked_loads_" + phase, Integer.toString(markedSourceLoads.size()));
		values.setProperty("source_unmarked_loads_" + phase, Integer.toString(unmarkedSourceLoads.size()));
		values.setProperty(phase + "_complete", "true");
		write(marker, values, "OreSpawn 4 migration qualification");
		server.saveAllWorlds(false);
		for (Ticket ticket : tickets) ForgeChunkManager.releaseTicket(ticket);
		tickets.clear();
		DimensionManager.keepDimensionLoaded(-1, false);
		DimensionManager.keepDimensionLoaded(1, false);
		server.saveAllWorlds(false);
		server.initiateShutdown();
	}

	private boolean isRetrogenFixture() {
		return "os3-322-custom".equals(family);
	}

	private Path worldRoot() {
		return server.getActiveAnvilConverter().getFile(server.getFolderName(), "level.dat")
				.toPath().toAbsolutePath().normalize().getParent();
	}

	private static String firstDifference(Properties expected, Properties actual) {
		List<String> keys = new ArrayList<>();
		keys.addAll(expected.stringPropertyNames());
		for (String key : actual.stringPropertyNames()) if (!keys.contains(key)) keys.add(key);
		Collections.sort(keys);
		for (String key : keys) {
			String left = expected.getProperty(key);
			String right = actual.getProperty(key);
			if (left == null ? right != null : !left.equals(right)) {
				return key + " expected=" + left + " actual=" + right;
			}
		}
		return "unknown";
	}

	private Properties auditWorld() {
		Properties result = new Properties();
		result.setProperty("format", "1");
		result.setProperty("family", family);
		result.setProperty("seed", Long.toString(server.getWorld(0).getSeed()));
		for (int dimension : new int[] { 0, -1, 1 }) {
			WorldServer world = requireWorld(server, dimension);
			auditRegion(result, "source." + dimension, world, MIN_SOURCE_CHUNK, MAX_SOURCE_CHUNK);
			auditRegion(result, "reserved." + dimension, world, MIN_RESERVED_CHUNK, MAX_RESERVED_CHUNK);
		}
		return result;
	}

	private Properties auditSourceWorld() {
		Properties result = new Properties();
		for (int dimension : new int[] { 0, -1, 1 }) {
			auditRegion(result, "source." + dimension, requireWorld(server, dimension),
					MIN_SOURCE_CHUNK, MAX_SOURCE_CHUNK);
		}
		return result;
	}

	private static void auditRegion(Properties result, String prefix, WorldServer world,
			int minimumChunk, int maximumChunk) {
		Map<String, Long> blocks = new TreeMap<>();
		Map<String, Long> biomes = new TreeMap<>();
		Map<String, Integer> minimumY = new TreeMap<>();
		Map<String, Integer> maximumY = new TreeMap<>();
		List<String> tileEntities = new ArrayList<>();
		long blockHash = 0xcbf29ce484222325L;
		long biomeHash = 0xcbf29ce484222325L;
		for (int chunkX = minimumChunk; chunkX <= maximumChunk; chunkX++) {
			for (int chunkZ = minimumChunk; chunkZ <= maximumChunk; chunkZ++) {
				Chunk chunk = world.getChunkProvider().provideChunk(chunkX, chunkZ);
				for (int localX = 0; localX < 16; localX++) {
					for (int localZ = 0; localZ < 16; localZ++) {
						ResourceLocation biome = world.getBiome(new BlockPos(chunkX * 16 + localX,
								64, chunkZ * 16 + localZ)).getRegistryName();
						String biomeKey = biome == null ? "unknown" : biome.toString();
						increment(biomes, biomeKey);
						biomeHash = hash(biomeHash, biomeKey.hashCode());
						for (int y = 0; y < world.getHeight(); y++) {
							IBlockState state = chunk.getBlockState(localX, y, localZ);
							Block block = state.getBlock();
							// Air, fire, and liquid flow legitimately advance between launches.
							// They are not generated or managed by OreSpawn, so the migration
							// comparison hashes stable terrain and authored state instead.
							if (block == Blocks.AIR || block == Blocks.FIRE
									|| state.getMaterial().isLiquid()
									|| state.getMaterial() == net.minecraft.block.material.Material.PLANTS
									|| state.getMaterial() == net.minecraft.block.material.Material.VINE) continue;
							ResourceLocation id = block.getRegistryName();
							int metadata = block.getMetaFromState(state);
							int hashBlock = Block.getIdFromBlock(block);
							String blockName = id == null ? "unknown" : id.toString();
							if (block == Blocks.DIRT || block == Blocks.GRASS) {
								blockName = "minecraft:surface_soil";
								metadata = 0;
								hashBlock = Block.getIdFromBlock(Blocks.DIRT);
							} else if (block == Blocks.LEAVES || block == Blocks.LEAVES2) {
								metadata &= 3; // decay/check-decay bits advance during ordinary ticks
							}
							String key = blockName + "@" + metadata;
							increment(blocks, key);
							blockHash = hash(hash(blockHash, hashBlock), metadata);
							if (key.contains("ore")) {
								minimumY.put(key, Math.min(minimumY.containsKey(key) ? minimumY.get(key) : y, y));
								maximumY.put(key, Math.max(maximumY.containsKey(key) ? maximumY.get(key) : y, y));
							}
						}
					}
				}
				for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet()) {
					tileEntities.add(tileIdentity(entry.getKey(), entry.getValue()));
				}
			}
		}
		Collections.sort(tileEntities);
		long tileHash = 0xcbf29ce484222325L;
		for (String identity : tileEntities) tileHash = hash(tileHash, identity.hashCode());
		result.setProperty(prefix + ".block_hash", Long.toUnsignedString(blockHash));
		result.setProperty(prefix + ".biome_hash", Long.toUnsignedString(biomeHash));
		result.setProperty(prefix + ".tile_hash", Long.toUnsignedString(tileHash));
		result.setProperty(prefix + ".tile_count", Integer.toString(tileEntities.size()));
		for (Map.Entry<String, Long> entry : blocks.entrySet()) {
			result.setProperty(prefix + ".block." + entry.getKey(), Long.toString(entry.getValue()));
		}
		for (Map.Entry<String, Long> entry : biomes.entrySet()) {
			result.setProperty(prefix + ".biome." + entry.getKey(), Long.toString(entry.getValue()));
		}
		for (Map.Entry<String, Integer> entry : minimumY.entrySet()) {
			result.setProperty(prefix + ".ore_min_y." + entry.getKey(), Integer.toString(entry.getValue()));
			result.setProperty(prefix + ".ore_max_y." + entry.getKey(), Integer.toString(maximumY.get(entry.getKey())));
		}
	}

	private static String tileIdentity(BlockPos position, TileEntity tile) {
		StringBuilder value = new StringBuilder(tile.getClass().getName()).append('@')
				.append(position.getX()).append(',').append(position.getY()).append(',').append(position.getZ());
		if (tile instanceof TileEntityChest) {
			TileEntityChest chest = (TileEntityChest) tile;
			for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
				ItemStack stack = chest.getStackInSlot(slot);
				if (stack.isEmpty()) continue;
				ResourceLocation id = stack.getItem().getRegistryName();
				value.append('|').append(slot).append(':').append(id).append('@')
						.append(stack.getMetadata()).append('x').append(stack.getCount());
			}
		}
		return value.toString();
	}

	private static void compareSourceAudit(Path root, Properties actual) throws IOException {
		Path sourcePath = root.resolve("orespawn-migration-source.properties");
		if (!Files.isRegularFile(sourcePath)) return;
		Properties source = read(sourcePath);
		for (String key : source.stringPropertyNames()) {
			if (!key.startsWith("dimension.")) continue;
			String[] parts = key.split("\\.", 4);
			if (parts.length < 4 || (!"block".equals(parts[2]) && !"biome".equals(parts[2]))) continue;
			if ("block".equals(parts[2]) && (parts[3].startsWith("minecraft:air@")
					|| parts[3].startsWith("minecraft:fire@")
					|| parts[3].startsWith("minecraft:water@")
					|| parts[3].startsWith("minecraft:flowing_water@")
					|| parts[3].startsWith("minecraft:lava@")
					|| parts[3].startsWith("minecraft:flowing_lava@")
					|| parts[3].startsWith("minecraft:dirt@")
					|| parts[3].startsWith("minecraft:grass@")
					|| parts[3].startsWith("minecraft:leaves@")
					|| parts[3].startsWith("minecraft:leaves2@")
					|| isTransientSourceBlock(parts[3]))) continue;
			String migrated = "source." + parts[1] + "." + parts[2] + "." + parts[3];
			if (!source.getProperty(key).equals(actual.getProperty(migrated))) {
				throw new IllegalStateException("Generated OS3 source data changed: " + key);
			}
		}
		for (String key : source.stringPropertyNames()) {
			if (!key.startsWith("registry.block.") && !key.startsWith("registry.item.")) continue;
			String registryName = key.substring(key.indexOf('.', 9) + 1);
			ResourceLocation id = new ResourceLocation(registryName);
			if (!"minecraft".equals(id.getNamespace()) && !"basemetals".equals(id.getNamespace())
					&& !"mineralogy".equals(id.getNamespace())) continue;
			int numeric = key.startsWith("registry.block.")
					? Block.getIdFromBlock(ForgeRegistries.BLOCKS.getValue(id))
					: Item.getIdFromItem(ForgeRegistries.ITEMS.getValue(id));
			if (!Integer.toString(numeric).equals(source.getProperty(key))) {
				throw new IllegalStateException("Registry mapping changed: " + key);
			}
		}
		for (int dimension : new int[] { 0, -1, 1 }) {
			compareNormalizedSourceBlock(source, actual, dimension, "minecraft:surface_soil", 0,
					new String[] { "minecraft:dirt@0", "minecraft:grass@0" });
			for (String leaves : new String[] { "minecraft:leaves", "minecraft:leaves2" }) {
				for (int metadata = 0; metadata < 4; metadata++) {
					List<String> variants = new ArrayList<>();
					for (int flags : new int[] { 0, 4, 8, 12 }) variants.add(leaves + "@" + (metadata + flags));
					compareNormalizedSourceBlock(source, actual, dimension, leaves, metadata,
							variants.toArray(new String[variants.size()]));
				}
			}
		}
		for (int dimension : new int[] { 0, -1, 1 }) {
			BlockPos chestPosition = new BlockPos(0, dimension == -1 ? 64 : 100, 0);
			TileEntity tile = requireWorld(FMLCommonHandler.instance().getMinecraftServerInstance(), dimension)
					.getTileEntity(chestPosition);
			if (!(tile instanceof TileEntityChest)) throw new IllegalStateException("Missing fixture chest " + dimension);
			ItemStack stack = ((TileEntityChest) tile).getStackInSlot(0);
			if (stack.getItem() != Item.getItemFromBlock(Blocks.STONE) || stack.getCount() != 32
					|| stack.getMetadata() != 1) {
				throw new IllegalStateException("Fixture inventory changed in dimension " + dimension);
			}
		}
	}

	private static boolean isTransientSourceBlock(String stateName) {
		int metadataSeparator = stateName.lastIndexOf('@');
		String blockName = metadataSeparator < 0 ? stateName : stateName.substring(0, metadataSeparator);
		Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
		if (block == null) return false;
		net.minecraft.block.material.Material material = block.getDefaultState().getMaterial();
		return material == net.minecraft.block.material.Material.PLANTS
				|| material == net.minecraft.block.material.Material.VINE;
	}

	private static void compareNormalizedSourceBlock(Properties source, Properties actual,
			int dimension, String normalizedBlock, int metadata, String[] sourceBlocks) {
		long expected = 0L;
		for (String block : sourceBlocks) {
			expected += Long.parseLong(source.getProperty(
					"dimension." + dimension + ".block." + block, "0"));
		}
		String key = "source." + dimension + ".block." + normalizedBlock + "@" + metadata;
		long found = Long.parseLong(actual.getProperty(key, "0"));
		if (expected != found) {
			throw new IllegalStateException("Generated OS3 source data changed: " + key
					+ " expected=" + expected + " actual=" + found);
		}
	}

	private static WorldServer requireWorld(MinecraftServer server, int dimension) {
		WorldServer world = server.getWorld(dimension);
		if (world == null) {
			DimensionManager.initDimension(dimension);
			world = DimensionManager.getWorld(dimension);
		}
		if (world == null) throw new IllegalStateException("Missing migration dimension " + dimension);
		return world;
	}

	private static void loadRegion(WorldServer world, int minimum, int maximum) {
		for (int chunkZ = minimum; chunkZ <= maximum; chunkZ++) {
			for (int chunkX = minimum; chunkX <= maximum; chunkX++) {
				world.getChunkProvider().provideChunk(chunkX, chunkZ);
			}
		}
	}

	private void forceRegion(WorldServer world, int minimum, int maximum) {
		int maximumDepth = Math.max(1, ForgeChunkManager.getMaxChunkDepthFor(MODID));
		Ticket ticket = null;
		int used = maximumDepth;
		for (int chunkZ = minimum; chunkZ <= maximum; chunkZ++) {
			for (int chunkX = minimum; chunkX <= maximum; chunkX++) {
				if (used >= maximumDepth) {
					ticket = ForgeChunkManager.requestTicket(this, world, ForgeChunkManager.Type.NORMAL);
					if (ticket == null) throw new IllegalStateException("Could not obtain migration chunk ticket");
					tickets.add(ticket);
					used = 0;
				}
				ForgeChunkManager.forceChunk(ticket, new net.minecraft.util.math.ChunkPos(chunkX, chunkZ));
				used++;
			}
		}
	}

	private static long hash(long current, int value) {
		current ^= value;
		return current * 0x100000001b3L;
	}

	private static void increment(Map<String, Long> values, String key) {
		values.put(key, values.containsKey(key) ? values.get(key) + 1L : 1L);
	}

	private static Properties read(Path path) throws IOException {
		Properties values = new Properties();
		try (InputStream input = Files.newInputStream(path)) { values.load(input); }
		return values;
	}

	private static void write(Path path, Properties values, String comment) throws IOException {
		try (OutputStream output = Files.newOutputStream(path)) { values.store(output, comment); }
	}
}
