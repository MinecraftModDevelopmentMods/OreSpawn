package com.mcmoddev.orespawn.utils.codecs;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ResourceLocation;

import java.util.List;

public class DimensionMatcherConfig {
	public final List<ResourceLocation> data;
	public final ResourceLocation type;

	public static final Codec<DimensionMatcherConfig> CODEC = RecordCodecBuilder.create((base) -> {
		return base.group(ResourceLocation.CODEC.listOf().fieldOf("data").forGetter((config) -> {
				return config.data;
			}),
			ResourceLocation.CODEC.fieldOf("type").forGetter((config) -> {
				return config.type;
			})).apply(base, DimensionMatcherConfig::new);
	});

	public DimensionMatcherConfig(final List<ResourceLocation> entryList, final ResourceLocation type) {
		this.data = ImmutableList.copyOf(entryList);
		this.type = type;
	}
}
