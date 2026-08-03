package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

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
	private static final GateFeature[] FEATURES = features();
	private static final SuppressibleGateFeature SUPPRESSIBLE_FEATURE = new SuppressibleGateFeature();
	private static final Map<Identifier, Holder<PlacedFeature>> VANILLA_GATES =
			new LinkedHashMap<>();
	private static final Map<PlacedFeature, Holder<PlacedFeature>> SUPPRESSIBLE_GATES =
			new IdentityHashMap<>();

	private VanillaOreFeatureGate() {
	}

	public static void registerFeatures(DeferredRegister<Feature<?>> registry) {
		for (int i = 0; i < FEATURES.length; i++) {
			final int index = i;
			registry.register("vanilla_ore_gate_" + DEFINITIONS[i].placedFeatureId.getPath(),
					() -> FEATURES[index]);
		}
		registry.register("suppressible_ore_gate", () -> SUPPRESSIBLE_FEATURE);
	}

	static boolean wrapFeatureList(List<Holder<PlacedFeature>> features) {
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

	private static Holder<PlacedFeature> vanillaGate(Holder<PlacedFeature> original,
			int definitionIndex) {
		Definition definition = DEFINITIONS[definitionIndex];
		Block output = ForgeRegistries.BLOCKS.getValue(definition.oreBlockId);
		if (output == null) {
			LOGGER.warn("Could not create OreSpawn gate for vanilla ore feature '{}'",
					definition.placedFeatureId);
			return null;
		}
		GateFeature feature = FEATURES[definitionIndex];
		feature.initialize(original.value().feature(), output);
		Holder<ConfiguredFeature<?, ?>> configured = Holder.direct(
				new ConfiguredFeature<NoneFeatureConfiguration, GateFeature>(
						feature, NoneFeatureConfiguration.INSTANCE));
		return Holder.direct(new PlacedFeature(configured, original.value().placement()));
	}

	private static Holder<PlacedFeature> suppressibleGate(Holder<PlacedFeature> original) {
		Holder<ConfiguredFeature<?, ?>> configured = Holder.direct(
				new ConfiguredFeature<SuppressibleConfig, SuppressibleGateFeature>(
						SUPPRESSIBLE_FEATURE, new SuppressibleConfig(original.value().feature())));
		return Holder.direct(new PlacedFeature(configured, original.value().placement()));
	}

	private static int definitionIndex(Identifier id) {
		if (id == null) return -1;
		for (int i = 0; i < DEFINITIONS.length; i++) {
			if (DEFINITIONS[i].placedFeatureId.equals(id)) return i;
		}
		return -1;
	}

	private static boolean isStandardOreFeature(PlacedFeature placed) {
		return placed.getFeatures().anyMatch(configured -> configured.value().feature() == Feature.ORE
				|| configured.value().feature() == Feature.SCATTERED_ORE);
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

	private static GateFeature[] features() {
		GateFeature[] result = new GateFeature[DEFINITIONS.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = new GateFeature();
		}
		return result;
	}

	private static Definition definition(String placedFeature, String block) {
		return new Definition(Identifier.fromNamespaceAndPath("minecraft", placedFeature),
				Identifier.fromNamespaceAndPath("minecraft", block));
	}

	private static final class GateFeature extends Feature<NoneFeatureConfiguration> {
		private Holder<ConfiguredFeature<?, ?>> original;
		private Block output;

		GateFeature() {
			super(NoneFeatureConfiguration.CODEC);
		}

		void initialize(Holder<ConfiguredFeature<?, ?>> original, Block output) {
			this.original = original;
			this.output = output;
		}

		@Override
		public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
			if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) {
				return false;
			}
			if (OreSpawnOreGeneration.takesOverVanillaOre(
					context.level().getLevel().dimension(), output)) {
				return false;
			}
			return original.value().place(context.level(), context.chunkGenerator(),
					context.random(), context.origin());
		}
	}

	private static final class SuppressibleConfig implements FeatureConfiguration {
		static final Codec<SuppressibleConfig> CODEC = ConfiguredFeature.CODEC
				.fieldOf("delegate")
				.xmap(SuppressibleConfig::new, value -> value.delegate)
				.codec();
		final Holder<ConfiguredFeature<?, ?>> delegate;

		SuppressibleConfig(Holder<ConfiguredFeature<?, ?>> delegate) {
			this.delegate = delegate;
		}
	}

	private static final class SuppressibleGateFeature extends Feature<SuppressibleConfig> {
		SuppressibleGateFeature() {
			super(SuppressibleConfig.CODEC);
		}

		@Override
		public boolean place(FeaturePlaceContext<SuppressibleConfig> context) {
			if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) return false;
			return context.config().delegate.value().place(context.level(), context.chunkGenerator(),
					context.random(), context.origin());
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
