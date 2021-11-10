package com.mcmoddev.orespawn.utils.codecs;

import com.electronwill.nightconfig.core.conversion.InvalidValueException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class DefaultFeatureParametersConfig {
	public static final Codec<DefaultFeatureParametersConfig> CODEC = RecordCodecBuilder.create((base) -> {
		return base.group(Codec.intRange(0, 255).fieldOf("size").forGetter((config) -> config.size),
			Codec.intRange(0, 255).fieldOf("minHeight").forGetter((config) -> config.minHeight),
			Codec.intRange(0, 255).fieldOf("maxHeight").forGetter((config) -> config.minHeight),
			Codec.floatRange(0, 1).fieldOf("frequency").forGetter((config) -> config.frequency)).apply(base, DefaultFeatureParametersConfig::new);
	});

	public final int size;
	public final float frequency;
	public final int minHeight;
	public final int maxHeight;

	public DefaultFeatureParametersConfig(final int size, final int minHeight, final int maxHeight, final float frequency) {
		if (minHeight > maxHeight)
			throw new InvalidValueException("Minimum Height must be less than or equal to Maximum Height");
		this.size = size;
		this.minHeight = minHeight;
		this.maxHeight = maxHeight;
		this.frequency = frequency;
	}
}
