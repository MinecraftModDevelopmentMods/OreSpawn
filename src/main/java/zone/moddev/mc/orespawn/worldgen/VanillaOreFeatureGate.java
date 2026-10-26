package zone.moddev.mc.orespawn.worldgen;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps vanilla ore features intact until a world profile has a valid managed
 * replacement. Wrapping at the original list position makes the decision
 * world-specific without mutating biome feature lists after loading.
 */
public final class VanillaOreFeatureGate {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Definition[] DEFINITIONS = definitions();
	private static final Map<Identifier, Holder<PlacedFeature>> VANILLA_GATES =
			new LinkedHashMap<>();
	private static final Map<PlacedFeature, Holder<PlacedFeature>> SUPPRESSIBLE_GATES =
			new IdentityHashMap<>();

	private VanillaOreFeatureGate() {
	}

	public static void registerFeatures(
			DeferredRegister<MapCodec<? extends Feature>> registry) {
		registry.register("vanilla_ore_gate", () -> GateFeature.CODEC);
		registry.register("suppressible_ore_gate", () -> SuppressibleGateFeature.CODEC);
	}

	static synchronized boolean wrapFeatureList(List<Holder<PlacedFeature>> features) {
		boolean changed = false;
		for (int featureIndex = 0; featureIndex < features.size(); featureIndex++) {
			Holder<PlacedFeature> feature = features.get(featureIndex);
			Identifier id = feature.unwrapKey().map(key -> key.identifier()).orElse(null);
			if (id != null && OreSpawn.MODID.equals(id.getNamespace())) continue;

			int vanillaIndex = definitionIndex(id);
			Holder<PlacedFeature> replacement = vanillaIndex >= 0
					? VANILLA_GATES.computeIfAbsent(id,
							ignored -> vanillaGate(feature, vanillaIndex))
					: isStandardOreFeature(feature.value())
							? SUPPRESSIBLE_GATES.computeIfAbsent(feature.value(),
									ignored -> suppressibleGate(feature))
							: null;
			if (replacement != null) {
				features.set(featureIndex, replacement);
				changed = true;
			}
		}
		return changed;
	}

	static synchronized void clearGates() {
		// The codecs are permanent, but these holders belong to one server's registries.
		VANILLA_GATES.clear();
		SUPPRESSIBLE_GATES.clear();
	}

	private static Holder<PlacedFeature> vanillaGate(Holder<PlacedFeature> original,
			int definitionIndex) {
		Definition definition = DEFINITIONS[definitionIndex];
		Block output = ForgeRegistries.BLOCKS.getValue(definition.oreBlockId);
		if (output == null) {
			LOGGER.warn("Could not create OreSpawn gate for vanilla ore feature '{}'",
					definition.placedFeatureId);
			return null;
		}
		GateFeature feature = new GateFeature(original.value().feature(), output);
		return Holder.direct(new PlacedFeature(Holder.direct(feature),
				original.value().placement()));
	}

	private static Holder<PlacedFeature> suppressibleGate(Holder<PlacedFeature> original) {
		return Holder.direct(new PlacedFeature(
				Holder.direct(new SuppressibleGateFeature(original.value().feature())),
				original.value().placement()));
	}

	private static int definitionIndex(Identifier id) {
		if (id == null) return -1;
		for (int i = 0; i < DEFINITIONS.length; i++) {
			if (DEFINITIONS[i].placedFeatureId.equals(id)) return i;
		}
		return -1;
	}

	private static boolean isStandardOreFeature(PlacedFeature placed) {
		return placed.getFeatures().anyMatch(feature -> feature.value() instanceof OreFeature
				|| feature.value() instanceof ScatteredOreFeature);
	}

	static Block nativeOutput(Feature feature) {
		Block output = null;
		java.util.stream.Stream<Feature> features = java.util.stream.Stream.concat(
				java.util.stream.Stream.of(feature), feature.getSubFeatures().map(Holder::value));
		for (Feature child : (Iterable<Feature>) features::iterator) {
			if (!(child instanceof OreFeature) && !(child instanceof ScatteredOreFeature)) return null;
			AbstractOreFeature ore = (AbstractOreFeature) child;
			if (ore.targetStates().isEmpty()) return null;
			for (BlockReplacement target : ore.targetStates()) {
				Block candidate = vanillaOreFamily(target.state().getBlock());
				if (candidate == null || (output != null && output != candidate)) return null;
				output = candidate;
			}
		}
		return output;
	}

	static Block vanillaOreFamily(Block block) {
		// Stone and deepslate variants share one managed placement rule.
		if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return Blocks.COAL_ORE;
		if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return Blocks.IRON_ORE;
		if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return Blocks.COPPER_ORE;
		if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return Blocks.GOLD_ORE;
		if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return Blocks.REDSTONE_ORE;
		if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return Blocks.DIAMOND_ORE;
		if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return Blocks.LAPIS_ORE;
		if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return Blocks.EMERALD_ORE;
		if (block == Blocks.NETHER_GOLD_ORE || block == Blocks.NETHER_QUARTZ_ORE
				|| block == Blocks.ANCIENT_DEBRIS) return block;
		return null;
	}

	private static Definition[] definitions() {
		return new Definition[] {
				definition("ore_coal_upper", "coal_ore"),
				definition("ore_coal_lower", "coal_ore"),
				definition("ore_iron_upper", "iron_ore"),
				definition("ore_iron_middle", "iron_ore"),
				definition("ore_iron_small", "iron_ore"),
				definition("ore_gold_extra", "gold_ore"),
				definition("ore_gold", "gold_ore"),
				definition("ore_gold_lower", "gold_ore"),
				definition("ore_redstone", "redstone_ore"),
				definition("ore_redstone_lower", "redstone_ore"),
				definition("ore_diamond", "diamond_ore"),
				definition("ore_diamond_large", "diamond_ore"),
				definition("ore_diamond_buried", "diamond_ore"),
				definition("ore_lapis", "lapis_ore"),
				definition("ore_lapis_buried", "lapis_ore"),
				definition("ore_emerald", "emerald_ore"),
				definition("ore_copper", "copper_ore"),
				definition("ore_copper_large", "copper_ore"),
				definition("ore_gold_deltas", "nether_gold_ore"),
				definition("ore_gold_nether", "nether_gold_ore"),
				definition("ore_quartz_deltas", "nether_quartz_ore"),
				definition("ore_quartz_nether", "nether_quartz_ore"),
				definition("ore_ancient_debris_large", "ancient_debris"),
				definition("ore_debris_small", "ancient_debris")
		};
	}

	private static Definition definition(String placedFeature, String block) {
		return new Definition(Identifier.fromNamespaceAndPath("minecraft", placedFeature),
				Identifier.fromNamespaceAndPath("minecraft", block));
	}

	private static final class GateFeature implements Feature {
		static final MapCodec<GateFeature> CODEC = RecordCodecBuilder.mapCodec(instance ->
				instance.group(
						Feature.CODEC.fieldOf("delegate").forGetter(value -> value.original),
						BuiltInRegistries.BLOCK.byNameCodec().fieldOf("output")
								.forGetter(value -> value.output))
						.apply(instance, GateFeature::new));
		private final Holder<Feature> original;
		private final Block output;

		GateFeature(Holder<Feature> original, Block output) {
			this.original = original;
			this.output = output;
		}

		@Override
		public MapCodec<GateFeature> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel world, ChunkGenerator chunkGenerator,
				RandomSource random, BlockPos origin) {
			WorldGeologyProfileManager.resolveFreshPoliciesBeforeGeneration();
			if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) {
				return false;
			}
			if (OreSpawnOreGeneration.takesOverVanillaOre(
					world.getLevel().dimension(), output)) {
				return false;
			}
			return original.value().place(world, chunkGenerator, random, origin);
		}
	}

	private static final class SuppressibleGateFeature implements Feature {
		static final MapCodec<SuppressibleGateFeature> CODEC = Feature.CODEC
				.fieldOf("delegate")
				.xmap(SuppressibleGateFeature::new, value -> value.delegate);
		final Holder<Feature> delegate;
		final Block nativeOutput;

		SuppressibleGateFeature(Holder<Feature> delegate) {
			this.delegate = delegate;
			this.nativeOutput = nativeOutput(delegate.value());
		}

		@Override
		public MapCodec<SuppressibleGateFeature> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel world, ChunkGenerator chunkGenerator,
				RandomSource random, BlockPos origin) {
			WorldGeologyProfileManager.resolveFreshPoliciesBeforeGeneration();
			if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) return false;
			if (nativeOutput != null && OreSpawnOreGeneration.takesOverVanillaOre(
					world.getLevel().dimension(), nativeOutput)) return false;
			return delegate.value().place(world, chunkGenerator, random, origin);
		}
	}

	private static final class Definition {
		final Identifier placedFeatureId;
		final Identifier oreBlockId;

		Definition(Identifier placedFeatureId, Identifier oreBlockId) {
			this.placedFeatureId = placedFeatureId;
			this.oreBlockId = oreBlockId;
		}
	}

}
