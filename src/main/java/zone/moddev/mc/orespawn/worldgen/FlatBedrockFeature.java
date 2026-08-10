package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.IWorld;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional flat bedrock generation compatible with OreSpawn 3 profiles. */
public final class FlatBedrockFeature extends ContextFeature<NoFeatureConfig> {
	public static final FlatBedrockFeature FEATURE = new FlatBedrockFeature();
	private static final int BEDROCK_NOISE_DEPTH = 5;

	private static ConfiguredFeature<?> configuredFeature;
	private static volatile Settings settings = Settings.DISABLED;

	private FlatBedrockFeature() {
		super(NoFeatureConfig::deserialize);
		setRegistryName(OreSpawn.MODID, "flat_bedrock");
	}

	public static void registerConfiguredFeature() {
		ResourceLocation id = new ResourceLocation(OreSpawn.MODID, "flat_bedrock");
		configuredFeature = net.minecraft.world.biome.Biome.createDecoratedFeature(
				FEATURE, new NoFeatureConfig(), Placement.NOPE, new NoPlacementConfig());
		refreshWorldConfig();
	}

	static ConfiguredFeature<?> configuredFeature() {
		return configuredFeature;
	}

	public static void refreshWorldConfig() {
		settings = readSettings(WorldGeologyProfileManager.activeProfile().rootCopy());
	}

	static boolean enabledFor(ResourceLocation dimension) {
		Settings current = settings;
		return current.enabled && current.dimensions.contains(dimension);
	}

	@Override
	boolean place(FeaturePlaceContext<NoFeatureConfig> context) {
		return flatten(context.level(), context.level().getChunk(context.origin()));
	}

	static boolean flatten(IWorld world, IChunk chunk) {
		return flattenChunk((ServerWorld) world.getWorld(), chunk);
	}

	static boolean flattenChunk(ServerWorld level, IChunk chunk) {
		Settings current = settings;
		ResourceLocation dimension = WorldIds.dimension(level);
		if (!current.enabled || !current.dimensions.contains(dimension)) {
			return false;
		}

		BlockState replacement = WorldIds.NETHER.equals(dimension)
				? current.netherReplacement : current.bottomReplacement;
		boolean changed = flattenBottom(chunk, current.layers, replacement);
		if (WorldIds.NETHER.equals(dimension)) {
			changed |= flattenTop(chunk, current.layers, current.netherReplacement);
		}
		if (changed) chunk.setModified(true);
		return changed;
	}

	private static boolean flattenBottom(IChunk chunk, int layers, BlockState replacement) {
		int minY = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int offset = 0; offset < BEDROCK_NOISE_DEPTH; offset++) {
					cursor.setPos(chunk.getPos().getXStart() + x, minY + offset,
							chunk.getPos().getZStart() + z);
					BlockState old = chunk.getBlockState(cursor);
					BlockState next = offset < layers ? Blocks.BEDROCK.getDefaultState()
							: old.getBlock() == Blocks.BEDROCK ? replacement : old;
					if (old != next) {
						chunk.setBlockState(cursor, next, false);
						changed = true;
					}
				}
			}
		}
		return changed;
	}

	private static boolean flattenTop(IChunk chunk, int layers, BlockState replacement) {
		int maxY = 256 - 1;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int offset = 0; offset < BEDROCK_NOISE_DEPTH; offset++) {
					cursor.setPos(chunk.getPos().getXStart() + x, maxY - offset,
							chunk.getPos().getZStart() + z);
					BlockState old = chunk.getBlockState(cursor);
					BlockState next = offset < layers ? Blocks.BEDROCK.getDefaultState()
							: old.getBlock() == Blocks.BEDROCK ? replacement : old;
					if (old != next) {
						chunk.setBlockState(cursor, next, false);
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

	private static BlockState blockState(JsonObject json, String key, BlockState fallback) {
		try {
			Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(
					json.has(key) ? json.get(key).getAsString() : ""));
			return block == null || block == Blocks.AIR ? fallback : block.getDefaultState();
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
		final BlockState bottomReplacement;
		final BlockState netherReplacement;

		Settings(boolean enabled, int layers, Set<ResourceLocation> dimensions,
				BlockState bottomReplacement, BlockState netherReplacement) {
			this.enabled = enabled;
			this.layers = layers;
			this.dimensions = Collections.unmodifiableSet(new HashSet<>(dimensions));
			this.bottomReplacement = bottomReplacement;
			this.netherReplacement = netherReplacement;
		}
	}
}
