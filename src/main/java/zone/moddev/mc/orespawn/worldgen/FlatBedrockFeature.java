package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.core.registries.BuiltInRegistries;

/** Optional flat bedrock generation compatible with OreSpawn 3 profiles. */
public final class FlatBedrockFeature extends Feature<NoneFeatureConfiguration> {
	public static final FlatBedrockFeature FEATURE = new FlatBedrockFeature();
	private static final int BEDROCK_NOISE_DEPTH = 5;

	private static Holder<PlacedFeature> placedFeature;
	private static volatile Settings settings = Settings.DISABLED;

	private FlatBedrockFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	public static void registerConfiguredFeature() {
		placedFeature = WorldgenFeatureHolders.direct(FEATURE);
		refreshWorldConfig();
	}

	static Holder<PlacedFeature> placedFeature() {
		return placedFeature;
	}

	public static void refreshWorldConfig() {
		settings = readSettings(WorldGeologyProfileManager.activeProfile().rootCopy());
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		return flatten(context.level(), context.level().getChunk(context.origin()));
	}

	static boolean flatten(WorldGenLevel world, ChunkAccess chunk) {
		return flattenChunk(world.getLevel(), chunk);
	}

	static boolean flattenChunk(ServerLevel level, ChunkAccess chunk) {
		Settings current = settings;
		if (!current.enabled || level.isFlat() || !current.dimensions.contains(level.dimension())) {
			return false;
		}

		BlockState replacement = Level.NETHER.equals(level.dimension())
				? current.netherReplacement : current.bottomReplacement;
		boolean changed = flattenBottom(chunk, current.layers, replacement);
		if (Level.NETHER.equals(level.dimension())) {
			changed |= flattenTop(chunk, current.layers, current.netherReplacement);
		}
		if (changed) chunk.markUnsaved();
		return changed;
	}

	private static boolean flattenBottom(ChunkAccess chunk, int layers, BlockState replacement) {
		int minY = chunk.getMinY();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int offset = 0; offset < BEDROCK_NOISE_DEPTH; offset++) {
					cursor.set(chunk.getPos().getBlockX(x), minY + offset, chunk.getPos().getBlockZ(z));
					BlockState old = chunk.getBlockState(cursor);
					BlockState next = offset < layers ? Blocks.BEDROCK.defaultBlockState()
							: old.is(Blocks.BEDROCK) ? replacement : old;
					if (old != next) {
						chunk.setBlockState(cursor, next, 0);
						changed = true;
					}
				}
			}
		}
		return changed;
	}

	private static boolean flattenTop(ChunkAccess chunk, int layers, BlockState replacement) {
		int maxY = chunk.getMaxY() - 1;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int offset = 0; offset < BEDROCK_NOISE_DEPTH; offset++) {
					cursor.set(chunk.getPos().getBlockX(x), maxY - offset, chunk.getPos().getBlockZ(z));
					BlockState old = chunk.getBlockState(cursor);
					BlockState next = offset < layers ? Blocks.BEDROCK.defaultBlockState()
							: old.is(Blocks.BEDROCK) ? replacement : old;
					if (old != next) {
						chunk.setBlockState(cursor, next, 0);
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
		Set<ResourceKey<Level>> dimensions = new HashSet<>();
		if (json.has("dimensions") && json.get("dimensions").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("dimensions")) {
				try {
					dimensions.add(ResourceKey.create(Registries.DIMENSION,
							Identifier.parse(element.getAsString())));
				} catch (RuntimeException ignored) {
				}
			}
		}
		if (dimensions.isEmpty()) {
			dimensions.add(Level.OVERWORLD);
			dimensions.add(Level.NETHER);
		}
		return new Settings(enabled, layers, dimensions,
				blockState(json, "bottom_replacement", Blocks.DEEPSLATE.defaultBlockState()),
				blockState(json, "nether_replacement", Blocks.NETHERRACK.defaultBlockState()));
	}

	private static BlockState blockState(JsonObject json, String key, BlockState fallback) {
		try {
			Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(
					json.has(key) ? json.get(key).getAsString() : ""));
			return block == null || block == Blocks.AIR ? fallback : block.defaultBlockState();
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
				Blocks.DEEPSLATE.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState());
		final boolean enabled;
		final int layers;
		final Set<ResourceKey<Level>> dimensions;
		final BlockState bottomReplacement;
		final BlockState netherReplacement;

		Settings(boolean enabled, int layers, Set<ResourceKey<Level>> dimensions,
				BlockState bottomReplacement, BlockState netherReplacement) {
			this.enabled = enabled;
			this.layers = layers;
			this.dimensions = Collections.unmodifiableSet(new HashSet<>(dimensions));
			this.bottomReplacement = bottomReplacement;
			this.netherReplacement = netherReplacement;
		}
	}
}
