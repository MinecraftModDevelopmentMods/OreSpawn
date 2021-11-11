package com.mcmoddev.orespawn.world.gen.configs;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.utils.codecs.BiomeMatcherConfig;
import com.mcmoddev.orespawn.utils.codecs.DefaultFeatureParametersConfig;
import com.mcmoddev.orespawn.utils.codecs.DimensionMatcherConfig;
import com.mcmoddev.orespawn.world.features.DefaultFeature;
import com.mcmoddev.orespawn.world.features.Features;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.WeightedList;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.template.IRuleTestType;
import net.minecraft.world.gen.feature.template.RuleTest;

import java.util.*;
import java.util.stream.Stream;

public class DefaultFeatureConfig implements IFeatureConfig {
	public static final Codec<DefaultFeatureConfig> CODEC = RecordCodecBuilder.create((codec) -> {
		return codec.group(
			Codec.STRING.fieldOf("name").forGetter((config) -> config.feature),
			RuleTest.CODEC.fieldOf("replaces").forGetter((config) -> config.replacer),
			DefaultFeatureParametersConfig.CODEC.fieldOf("parameters").forGetter((config) -> config.parameters),
			BiomeMatcherConfig.CODEC.fieldOf("biomes").forGetter((config) -> config.biomeMatch),
			DimensionMatcherConfig.CODEC.fieldOf("dimensions").forGetter((config) -> config.dimensionMatch),
			WeightedList.getCodec(BlockState.CODEC).fieldOf("blocks").forGetter((config) -> config.blocks)
		).apply(codec, DefaultFeatureConfig::generateFeature);
	});

	public final WeightedList<BlockState> blocks;
	public final BiomeMatcherConfig biomeMatch;
	public final DimensionMatcherConfig dimensionMatch;
	public final DefaultFeatureParametersConfig parameters;
	public final String feature;
	public final RuleTest replacer;

	private static final Map<String, ConfiguredFeature<?,?>> myFeatures = new TreeMap<>();

	private static DefaultFeatureConfig generateFeature(String featureName, RuleTest replacement,
														DefaultFeatureParametersConfig parameters, BiomeMatcherConfig biomes,
														DimensionMatcherConfig dimensions, WeightedList<BlockState> blocks ) {
		DefaultFeatureConfig z = new DefaultFeatureConfig(featureName, replacement, parameters, biomes, dimensions, blocks);
		if (!myFeatures.containsKey(featureName)) {
			myFeatures.put(featureName, Features.DEFAULT.withConfiguration(z));
			Registry.register(WorldGenRegistries.CONFIGURED_FEATURE, featureName, Features.DEFAULT.withConfiguration(z));
		}
		return z;
	}

	public DefaultFeatureConfig(String featureName, RuleTest replacement,
								DefaultFeatureParametersConfig parameters, BiomeMatcherConfig biomes,
								DimensionMatcherConfig dimensions, WeightedList<BlockState> blocks ) {
		this.replacer = replacement;
		this.blocks = blocks;
		this.biomeMatch = biomes;
		this.dimensionMatch = dimensions;
		this.parameters = parameters;
		this.feature = featureName;
		OreSpawn.LOGGER.info("Feature %s configured", featureName);
	}

	public Stream<ConfiguredFeature<?, ?>> getConfiguredFeatures() {
		return myFeatures.values().stream();
	}

	public static Map<String, ConfiguredFeature<?, ?>> getMyFeatures() {
		return Collections.unmodifiableMap(myFeatures);
	}
}
