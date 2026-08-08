package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.registries.IForgeRegistry;
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
	private static final GateFeature[] FEATURES = features();
	private static final SuppressibleGateFeature SUPPRESSIBLE_FEATURE = new SuppressibleGateFeature();
	private static Gate[] gates = new Gate[0];
	private static Map<ResourceLocation, ConfiguredFeature<?, ?>> suppressibleGates = new LinkedHashMap<>();

	private VanillaOreFeatureGate() {
	}

	public static void registerFeatures(IForgeRegistry<Feature<?>> registry) {
		for (GateFeature feature : FEATURES) {
			registry.register(feature);
		}
		registry.register(SUPPRESSIBLE_FEATURE);
	}

	static void register() {
		List<Gate> registered = new ArrayList<>();
		for (int definitionIndex = 0; definitionIndex < DEFINITIONS.length; definitionIndex++) {
			Definition definition = DEFINITIONS[definitionIndex];
			ConfiguredFeature<?, ?> original = BuiltinRegistries.CONFIGURED_FEATURE
					.get(definition.placedFeatureId);
			Block output = ForgeRegistries.BLOCKS.getValue(definition.oreBlockId);
			if (original == null || output == null) {
				LOGGER.warn("Could not create OreSpawn gate for vanilla ore feature '{}'",
						definition.placedFeatureId);
				continue;
			}

			ResourceLocation wrapperId = new ResourceLocation(OreSpawn.MODID,
					"vanilla_ore_gate/" + definition.placedFeatureId.getPath());
			GateFeature feature = FEATURES[definitionIndex];
			feature.initialize(original, output);
			ConfiguredFeature<?, ?> wrapper = Registry.register(BuiltinRegistries.CONFIGURED_FEATURE,
					wrapperId, feature.configured(NoneFeatureConfiguration.INSTANCE));
			registered.add(new Gate(definition.placedFeatureId, wrapper));
		}
		gates = registered.toArray(new Gate[registered.size()]);
		registerSuppressibleOreGates();
	}

	static void wrapVanillaOres(BiomeLoadingEvent event) {
		wrapFeatureList(event.getGeneration().getFeatures(GenerationStep.Decoration.UNDERGROUND_ORES));
		wrapFeatureList(event.getGeneration().getFeatures(GenerationStep.Decoration.UNDERGROUND_DECORATION));
	}

	static boolean wrapFeatureList(List<Supplier<ConfiguredFeature<?, ?>>> features) {
		boolean changed = false;
		for (int featureIndex = 0; featureIndex < features.size(); featureIndex++) {
			Supplier<ConfiguredFeature<?, ?>> feature = features.get(featureIndex);
			ResourceLocation featureId = BuiltinRegistries.CONFIGURED_FEATURE.getKey(feature.get());
			boolean wrapped = false;
			for (Gate gate : gates) {
				if (gate.originalId.equals(featureId)) {
					features.set(featureIndex, () -> gate.wrapper);
					wrapped = true;
					changed = true;
					break;
				}
			}
			if (!wrapped) {
				ConfiguredFeature<?, ?> replacement = featureId == null ? null
						: suppressibleGates.get(featureId);
				if (replacement != null) {
					features.set(featureIndex, () -> replacement);
					changed = true;
				}
			}
		}
		return changed;
	}

	private static void registerSuppressibleOreGates() {
		Map<ResourceLocation, ConfiguredFeature<?, ?>> registered = new LinkedHashMap<>();
		List<Map.Entry<net.minecraft.resources.ResourceKey<ConfiguredFeature<?, ?>>,
				ConfiguredFeature<?, ?>>> candidates =
				new ArrayList<>(BuiltinRegistries.CONFIGURED_FEATURE.entrySet());
		for (Map.Entry<net.minecraft.resources.ResourceKey<ConfiguredFeature<?, ?>>,
				ConfiguredFeature<?, ?>> entry : candidates) {
			ResourceLocation id = entry.getKey().location();
			ConfiguredFeature<?, ?> original = entry.getValue();
			if (OreSpawn.MODID.equals(id.getNamespace()) || isKnownVanillaGate(id)
					|| !isStandardOreFeature(original)) {
				continue;
			}
			ResourceLocation wrapperId = new ResourceLocation(OreSpawn.MODID,
					"all_ore_gate/" + id.getNamespace() + "/" + id.getPath());
			ConfiguredFeature<?, ?> wrapper = Registry.register(BuiltinRegistries.CONFIGURED_FEATURE,
					wrapperId, SUPPRESSIBLE_FEATURE.configured(new SuppressibleConfig(() -> original)));
			registered.put(id, wrapper);
		}
		suppressibleGates = registered;
		LOGGER.info("Registered {} OreSpawn gates for standard ore features", registered.size());
	}

	private static boolean isKnownVanillaGate(ResourceLocation id) {
		for (Definition definition : DEFINITIONS) if (definition.placedFeatureId.equals(id)) return true;
		return false;
	}

	private static boolean isStandardOreFeature(ConfiguredFeature<?, ?> configuredFeature) {
		return configuredFeature.getFeatures().anyMatch(configured -> configured.feature() == Feature.ORE
				|| configured.feature() == Feature.SCATTERED_ORE);
	}

	private static Definition[] definitions() {
		return new Definition[] {
				definition("ore_coal", "coal_ore"),
				definition("ore_iron", "iron_ore"),
				definition("ore_gold_extra", "gold_ore"),
				definition("ore_gold", "gold_ore"),
				definition("ore_redstone", "redstone_ore"),
				definition("ore_diamond", "diamond_ore"),
				definition("ore_lapis", "lapis_ore"),
				definition("ore_emerald", "emerald_ore"),
				definition("ore_copper", "copper_ore"),
				definition("ore_gold_deltas", "nether_gold_ore"),
				definition("ore_gold_nether", "nether_gold_ore"),
				definition("ore_quartz_deltas", "nether_quartz_ore"),
				definition("ore_quartz_nether", "nether_quartz_ore"),
				definition("ore_debris_large", "ancient_debris"),
				definition("ore_debris_small", "ancient_debris")
		};
	}

	private static GateFeature[] features() {
		GateFeature[] result = new GateFeature[DEFINITIONS.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = new GateFeature();
			result[i].setRegistryName(OreSpawn.MODID,
					"vanilla_ore_gate_" + DEFINITIONS[i].placedFeatureId.getPath());
		}
		return result;
	}

	private static Definition definition(String placedFeature, String block) {
		return new Definition(new ResourceLocation("minecraft", placedFeature),
				new ResourceLocation("minecraft", block));
	}

	private static final class GateFeature extends Feature<NoneFeatureConfiguration> {
		private ConfiguredFeature<?, ?> original;
		private Block output;

		GateFeature() {
			super(NoneFeatureConfiguration.CODEC);
		}

		void initialize(ConfiguredFeature<?, ?> original, Block output) {
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
			return original.place(context.level(), context.chunkGenerator(),
					context.random(), context.origin());
		}
	}

	private static final class SuppressibleConfig implements FeatureConfiguration {
		static final Codec<SuppressibleConfig> CODEC = ConfiguredFeature.CODEC
				.fieldOf("delegate")
				.xmap(SuppressibleConfig::new, value -> value.delegate)
				.codec();
		final Supplier<ConfiguredFeature<?, ?>> delegate;

		SuppressibleConfig(Supplier<ConfiguredFeature<?, ?>> delegate) {
			this.delegate = delegate;
		}
	}

	private static final class SuppressibleGateFeature extends Feature<SuppressibleConfig> {
		SuppressibleGateFeature() {
			super(SuppressibleConfig.CODEC);
			setRegistryName(OreSpawn.MODID, "suppressible_ore_gate");
		}

		@Override
		public boolean place(FeaturePlaceContext<SuppressibleConfig> context) {
			if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) return false;
			return context.config().delegate.get().place(context.level(), context.chunkGenerator(),
					context.random(), context.origin());
		}
	}

	private static final class Definition {
		final ResourceLocation placedFeatureId;
		final ResourceLocation oreBlockId;

		Definition(ResourceLocation placedFeatureId, ResourceLocation oreBlockId) {
			this.placedFeatureId = placedFeatureId;
			this.oreBlockId = oreBlockId;
		}
	}

	private static final class Gate {
		final ResourceLocation originalId;
		final ConfiguredFeature<?, ?> wrapper;

		Gate(ResourceLocation originalId, ConfiguredFeature<?, ?> wrapper) {
			this.originalId = originalId;
			this.wrapper = wrapper;
		}
	}
}
