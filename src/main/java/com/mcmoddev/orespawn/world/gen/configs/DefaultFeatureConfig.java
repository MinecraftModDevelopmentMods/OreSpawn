package com.mcmoddev.orespawn.world.gen.configs;

import com.mcmoddev.orespawn.utils.codecs.BiomeMatcherConfig;
import com.mcmoddev.orespawn.utils.codecs.DefaultFeatureParametersConfig;
import com.mcmoddev.orespawn.utils.codecs.DimensionMatcherConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.util.WeightedList;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.template.IRuleTestType;
import net.minecraft.world.gen.feature.template.RuleTest;

import java.util.Collections;
import java.util.Random;

public class DefaultFeatureConfig implements IFeatureConfig {
	public static final Codec<DefaultFeatureConfig> CODEC = RecordCodecBuilder.create((codec) -> {
		return codec.group(
			Codec.STRING.fieldOf("name").forGetter((config) -> config.feature),
			RuleTest.CODEC.fieldOf("replaces").forGetter((config) -> config.replacer),
			DefaultFeatureParametersConfig.CODEC.fieldOf("parameters").forGetter((config) -> config.parameters),
			BiomeMatcherConfig.CODEC.fieldOf("biomes").forGetter((config) -> config.biomeMatch),
			DimensionMatcherConfig.CODEC.fieldOf("dimensions").forGetter((config) -> config.dimensionMatch),
			WeightedList.getCodec(BlockState.CODEC).fieldOf("blocks").forGetter((config) -> config.blocks)
		).apply(codec, DefaultFeatureConfig::new);
	});

	public final WeightedList<BlockState> blocks;
	public final BiomeMatcherConfig biomeMatch;
	public final DimensionMatcherConfig dimensionMatch;
	public final DefaultFeatureParametersConfig parameters;
	public final String feature;
	public final RuleTest replacer;

	public DefaultFeatureConfig(String featureName, RuleTest replacement,
								DefaultFeatureParametersConfig parameters, BiomeMatcherConfig biomes,
								DimensionMatcherConfig dimensions, WeightedList<BlockState> blocks ) {
		this.replacer = replacement;
		this.blocks = blocks;
		this.biomeMatch = biomes;
		this.dimensionMatch = dimensions;
		this.parameters = parameters;
		this.feature = featureName;
	}
}
