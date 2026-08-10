package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Optional flat bedrock generation compatible with OreSpawn 3 profiles. */
public final class FlatBedrockFeature {
	public static final FlatBedrockFeature FEATURE = new FlatBedrockFeature();
	private static final int BEDROCK_NOISE_DEPTH = 5;

	private static volatile Settings settings = Settings.DISABLED;

	private FlatBedrockFeature() {
	}

	public static void registerConfiguredFeature() {
		refreshWorldConfig();
	}

	public static void refreshWorldConfig() {
		settings = readSettings(WorldGeologyProfileManager.activeProfile().rootCopy());
	}

	static boolean enabledFor(ResourceLocation dimension) {
		Settings current = settings;
		return current.enabled && current.dimensions.contains(dimension);
	}

	boolean generate(World world, Chunk chunk, Random random) {
		return flatten(world, chunk);
	}

	static boolean flatten(World world, Chunk chunk) {
		return flattenChunk((WorldServer) world, chunk);
	}

	static boolean flattenChunk(WorldServer level, Chunk chunk) {
		Settings current = settings;
		ResourceLocation dimension = WorldIds.dimension(level);
		if (!current.enabled || !current.dimensions.contains(dimension)) {
			return false;
		}

		IBlockState replacement = WorldIds.NETHER.equals(dimension)
				? current.netherReplacement : current.bottomReplacement;
		boolean changed = flattenBottom(chunk, current.layers, replacement);
		if (WorldIds.NETHER.equals(dimension)) {
			changed |= flattenTop(chunk, current.layers, current.netherReplacement);
		}
		if (changed && chunk instanceof net.minecraft.world.chunk.Chunk) {
			((net.minecraft.world.chunk.Chunk) chunk).markDirty();
		}
		return changed;
	}

	private static boolean flattenBottom(Chunk chunk, int layers, IBlockState replacement) {
		int minY = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int offset = 0; offset < BEDROCK_NOISE_DEPTH; offset++) {
					cursor.setPos(chunk.getPos().getXStart() + x, minY + offset,
							chunk.getPos().getZStart() + z);
					IBlockState old = chunk.getBlockState(cursor);
					IBlockState next = offset < layers ? Blocks.BEDROCK.getDefaultState()
							: old.getBlock() == Blocks.BEDROCK ? replacement : old;
					if (old != next) {
						chunk.setBlockState(cursor, next);
						changed = true;
					}
				}
			}
		}
		return changed;
	}

	private static boolean flattenTop(Chunk chunk, int layers, IBlockState replacement) {
		int maxY = 256 - 1;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int offset = 0; offset < BEDROCK_NOISE_DEPTH; offset++) {
					cursor.setPos(chunk.getPos().getXStart() + x, maxY - offset,
							chunk.getPos().getZStart() + z);
					IBlockState old = chunk.getBlockState(cursor);
					IBlockState next = offset < layers ? Blocks.BEDROCK.getDefaultState()
							: old.getBlock() == Blocks.BEDROCK ? replacement : old;
					if (old != next) {
						chunk.setBlockState(cursor, next);
						changed = true;
					}
				}
			}
		}
		return changed;
	}

	private static Settings readSettings(JsonObject root) {
		JsonObject json = root.has("flat_bedrock") && root.get("flat_bedrock").isJsonObject()
				? root.getAsJsonObject("flat_bedrock") : new JsonObject();
		boolean enabled = bool(json, "enabled", false);
		int layers = Math.max(1, Math.min(BEDROCK_NOISE_DEPTH, integer(json, "layers", 1)));
		Set<ResourceLocation> dimensions = new HashSet<>();
		if (json.has("dimensions") && json.get("dimensions").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("dimensions")) {
				try {
					dimensions.add(new ResourceLocation(element.getAsString()));
				} catch (RuntimeException ignored) {
				}
			}
		}
		if (dimensions.isEmpty()) {
			dimensions.add(WorldIds.OVERWORLD);
			dimensions.add(WorldIds.NETHER);
		}
		return new Settings(enabled, layers, dimensions,
				blockState(json, "bottom_replacement", Blocks.STONE.getDefaultState()),
				blockState(json, "nether_replacement", Blocks.NETHERRACK.getDefaultState()));
	}

	private static IBlockState blockState(JsonObject json, String key, IBlockState fallback) {
		try {
			Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(
					json.has(key) ? json.get(key).getAsString() : ""));
			if (block == null || block == Blocks.AIR) return fallback;
			int metadata = integer(json, key + "_metadata", 0);
			try { return block.getStateFromMeta(Math.max(0, Math.min(15, metadata))); }
			catch (RuntimeException ignored) { return block.getDefaultState(); }
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		try { return json.has(key) ? json.get(key).getAsBoolean() : fallback; }
		catch (RuntimeException e) { return fallback; }
	}

	private static int integer(JsonObject json, String key, int fallback) {
		try { return json.has(key) ? json.get(key).getAsInt() : fallback; }
		catch (RuntimeException e) { return fallback; }
	}

	private static final class Settings {
		static final Settings DISABLED = new Settings(false, 1, Collections.emptySet(),
				Blocks.STONE.getDefaultState(), Blocks.NETHERRACK.getDefaultState());
		final boolean enabled;
		final int layers;
		final Set<ResourceLocation> dimensions;
		final IBlockState bottomReplacement;
		final IBlockState netherReplacement;

		Settings(boolean enabled, int layers, Set<ResourceLocation> dimensions,
				IBlockState bottomReplacement, IBlockState netherReplacement) {
			this.enabled = enabled;
			this.layers = layers;
			this.dimensions = Collections.unmodifiableSet(new HashSet<>(dimensions));
			this.bottomReplacement = bottomReplacement;
			this.netherReplacement = netherReplacement;
		}
	}
}
