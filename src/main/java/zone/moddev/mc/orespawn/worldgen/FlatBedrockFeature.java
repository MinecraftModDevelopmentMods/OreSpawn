package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.Biome;

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
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional flat bedrock generation compatible with OreSpawn 3 profiles. */
public final class FlatBedrockFeature extends ContextFeature<NoFeatureConfig> {
	public static final FlatBedrockFeature FEATURE = new FlatBedrockFeature();
	private static final int BEDROCK_NOISE_DEPTH = 5;

	private static ConfiguredFeature<?, ?> configuredFeature;
	private static volatile Settings settings = Settings.DISABLED;

	private FlatBedrockFeature() {
		super(NoFeatureConfig.CODEC);
		setRegistryName(OreSpawn.MODID, "flat_bedrock");
	}

	public static void registerConfiguredFeature() {
		ResourceLocation id = new ResourceLocation(OreSpawn.MODID, "flat_bedrock");
		configuredFeature = Registry.register(WorldGenRegistries.CONFIGURED_FEATURE, id,
				FEATURE.configured(NoFeatureConfig.INSTANCE)
						.decorated(Placement.NOPE.configured(NoPlacementConfig.INSTANCE)));
		refreshWorldConfig();
	}

	public static void onBiomeLoading(BiomeLoadingEvent event) {
		if (!WorldgenBenchmark.isVanillaBaseline() && event.getCategory() != Biome.Category.NONE
				&& configuredFeature != null) {
			event.getGeneration().getFeatures(GenerationStage.Decoration.TOP_LAYER_MODIFICATION)
					.add(() -> configuredFeature);
		}
	}

	static ConfiguredFeature<?, ?> configuredFeature() {
		return configuredFeature;
	}

	public static void refreshWorldConfig() {
		settings = readSettings(WorldGeologyProfileManager.activeProfile().rootCopy());
	}

	@Override
	boolean place(FeaturePlaceContext<NoFeatureConfig> context) {
		return flatten(context.level(), context.level().getChunk(context.origin()));
	}

	static boolean flatten(ISeedReader world, IChunk chunk) {
		return flattenChunk(world.getLevel(), chunk);
	}

	static boolean flattenChunk(ServerWorld level, IChunk chunk) {
		Settings current = settings;
		if (!current.enabled || level.isFlat() || !current.dimensions.contains(level.dimension())) {
			return false;
		}

		BlockState replacement = World.NETHER.equals(level.dimension())
				? current.netherReplacement : current.bottomReplacement;
		boolean changed = flattenBottom(chunk, current.layers, replacement);
		if (World.NETHER.equals(level.dimension())) {
			changed |= flattenTop(chunk, current.layers, current.netherReplacement);
		}
		if (changed) chunk.setUnsaved(true);
		return changed;
	}

	private static boolean flattenBottom(IChunk chunk, int layers, BlockState replacement) {
		int minY = 0;
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		boolean changed = false;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int offset = 0; offset < BEDROCK_NOISE_DEPTH; offset++) {
					cursor.set(chunk.getPos().getMinBlockX() + x, minY + offset,
							chunk.getPos().getMinBlockZ() + z);
					BlockState old = chunk.getBlockState(cursor);
					BlockState next = offset < layers ? Blocks.BEDROCK.defaultBlockState()
							: old.is(Blocks.BEDROCK) ? replacement : old;
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
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		boolean changed = false;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int offset = 0; offset < BEDROCK_NOISE_DEPTH; offset++) {
					cursor.set(chunk.getPos().getMinBlockX() + x, maxY - offset,
							chunk.getPos().getMinBlockZ() + z);
					BlockState old = chunk.getBlockState(cursor);
					BlockState next = offset < layers ? Blocks.BEDROCK.defaultBlockState()
							: old.is(Blocks.BEDROCK) ? replacement : old;
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
		Set<RegistryKey<World>> dimensions = new HashSet<>();
		if (json.has("dimensions") && json.get("dimensions").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("dimensions")) {
				try {
					dimensions.add(RegistryKey.create(Registry.DIMENSION_REGISTRY,
							new ResourceLocation(element.getAsString())));
				} catch (RuntimeException ignored) {
				}
			}
		}
		if (dimensions.isEmpty()) {
			dimensions.add(World.OVERWORLD);
			dimensions.add(World.NETHER);
		}
		return new Settings(enabled, layers, dimensions,
				blockState(json, "bottom_replacement", Blocks.STONE.defaultBlockState()),
				blockState(json, "nether_replacement", Blocks.NETHERRACK.defaultBlockState()));
	}

	private static BlockState blockState(JsonObject json, String key, BlockState fallback) {
		try {
			Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(
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
				Blocks.STONE.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState());
		final boolean enabled;
		final int layers;
		final Set<RegistryKey<World>> dimensions;
		final BlockState bottomReplacement;
		final BlockState netherReplacement;

		Settings(boolean enabled, int layers, Set<RegistryKey<World>> dimensions,
				BlockState bottomReplacement, BlockState netherReplacement) {
			this.enabled = enabled;
			this.layers = layers;
			this.dimensions = Collections.unmodifiableSet(new HashSet<>(dimensions));
			this.bottomReplacement = bottomReplacement;
			this.netherReplacement = netherReplacement;
		}
	}
}
