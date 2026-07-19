package com.mcmoddev.orespawn.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Shared bounded settings understood by OreSpawn's six built-in patterns. */
public final class StandardPatternSettings {
	public static final Codec<StandardPatternSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("spread", 8).forGetter(StandardPatternSettings::spread),
			Codec.INT.optionalFieldOf("vertical_spread", 4).forGetter(StandardPatternSettings::verticalSpread),
			Codec.INT.optionalFieldOf("node_size", 4).forGetter(StandardPatternSettings::nodeSize),
			Codec.INT.optionalFieldOf("length", 16).forGetter(StandardPatternSettings::length),
			Codec.STRING.optionalFieldOf("fluid", "minecraft:water").forGetter(StandardPatternSettings::fluid))
			.apply(instance, StandardPatternSettings::new));

	private final int spread;
	private final int verticalSpread;
	private final int nodeSize;
	private final int length;
	private final String fluid;

	public StandardPatternSettings(int spread, int verticalSpread, int nodeSize, int length, String fluid) {
		this.spread = bounded(spread, 0, 64);
		this.verticalSpread = bounded(verticalSpread, 0, 64);
		this.nodeSize = bounded(nodeSize, 1, 32);
		this.length = bounded(length, 1, 64);
		this.fluid = fluid;
	}

	public int spread() { return spread; }
	public int verticalSpread() { return verticalSpread; }
	public int nodeSize() { return nodeSize; }
	public int length() { return length; }
	public String fluid() { return fluid; }

	private static int bounded(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
