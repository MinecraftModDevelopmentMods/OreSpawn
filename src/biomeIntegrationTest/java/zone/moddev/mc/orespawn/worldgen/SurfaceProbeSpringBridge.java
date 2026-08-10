package zone.moddev.mc.orespawn.worldgen;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.LiquidsConfig;
import net.minecraft.world.gen.feature.RandomDefaultFeatureListConfig;
import net.minecraft.world.gen.feature.RandomFeatureListConfig;
import net.minecraft.world.gen.feature.RandomFeatureWithConfigConfig;
import net.minecraft.world.gen.feature.TwoFeatureChoiceConfig;

/** Test-only package bridge for the Forge 25 spring wrapper. */
public final class SurfaceProbeSpringBridge {
	private SurfaceProbeSpringBridge() {
	}

	public static LiquidsConfig findRewrittenSpring(Iterable<Biome> biomes) {
		Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Biome biome : biomes) {
			for (GenerationStage.Decoration stage : GenerationStage.Decoration.values()) {
				for (CompositeFeature<?, ?> feature : biome.getFeatures(stage)) {
					LiquidsConfig result = find(feature.getFeature(),
							ConfiguredFeatureInspector.featureConfig(feature), visited);
					if (result != null) return result;
				}
			}
		}
		return null;
	}

	public static boolean recognizesProviderRock(Block block) {
		return VanillaSpringCompatibility.isHost(block);
	}

	public static boolean place(WorldServer world, BlockPos pos, LiquidsConfig config) {
		return VanillaSpringCompatibility.FEATURE.func_212245_a(world,
				world.getChunkProvider().getChunkGenerator(), world.getRandom(), pos, config);
	}

	/** Rebuilds the frozen worldgen heightmap after controlled fixture writes. */
	public static void rebuildWorldSurfaceHeight(IChunk chunk) {
		Class<?> type = chunk.getClass();
		while (type != null) {
			for (Field field : type.getDeclaredFields()) {
				if (!Map.class.isAssignableFrom(field.getType())) continue;
				try {
					field.setAccessible(true);
					Object value = field.get(chunk);
					if (!(value instanceof Map)) continue;
					Map<?, ?> map = (Map<?, ?>) value;
					if (!map.containsKey(Heightmap.Type.WORLD_SURFACE_WG)) continue;
					@SuppressWarnings("unchecked")
					Map<Object, Object> heightmaps = (Map<Object, Object>) map;
					heightmaps.remove(Heightmap.Type.WORLD_SURFACE_WG);
					chunk.createHeightMap(Heightmap.Type.WORLD_SURFACE_WG);
					return;
				} catch (ReflectiveOperationException exception) {
					throw new IllegalStateException("Could not rebuild Forge 25 fixture heightmap", exception);
				}
			}
			type = type.getSuperclass();
		}
		throw new IllegalStateException("Forge 25 fixture heightmap map was not found");
	}

	private static LiquidsConfig find(Feature<?> feature, IFeatureConfig config,
			Set<Object> visited) {
		if (feature == null || config == null || !visited.add(config)) return null;
		if (feature == VanillaSpringCompatibility.FEATURE && config instanceof LiquidsConfig) {
			return (LiquidsConfig) config;
		}
		if (feature instanceof CompositeFeature) {
			CompositeFeature<?, ?> nested = (CompositeFeature<?, ?>) feature;
			return find(nested.getFeature(), ConfiguredFeatureInspector.featureConfig(nested), visited);
		}
		if (config instanceof RandomDefaultFeatureListConfig) {
			RandomDefaultFeatureListConfig random = (RandomDefaultFeatureListConfig) config;
			for (int index = 0; index < random.field_202449_a.length; index++) {
				LiquidsConfig result = find(random.field_202449_a[index],
						random.field_202450_b[index], visited);
				if (result != null) return result;
			}
			return find(random.field_202452_d, random.field_202453_f, visited);
		}
		if (config instanceof RandomFeatureListConfig) {
			RandomFeatureListConfig random = (RandomFeatureListConfig) config;
			for (int index = 0; index < random.field_202454_a.length; index++) {
				LiquidsConfig result = find(random.field_202454_a[index],
						random.field_202455_b[index], visited);
				if (result != null) return result;
			}
		}
		if (config instanceof RandomFeatureWithConfigConfig) {
			RandomFeatureWithConfigConfig random = (RandomFeatureWithConfigConfig) config;
			for (int index = 0; index < random.features.length; index++) {
				LiquidsConfig result = find(random.features[index], random.configs[index], visited);
				if (result != null) return result;
			}
		}
		if (config instanceof TwoFeatureChoiceConfig) {
			TwoFeatureChoiceConfig choice = (TwoFeatureChoiceConfig) config;
			LiquidsConfig result = find(choice.field_202445_a, choice.field_202446_b, visited);
			return result != null ? result
					: find(choice.field_202447_c, choice.field_202448_d, visited);
		}
		return null;
	}
}
