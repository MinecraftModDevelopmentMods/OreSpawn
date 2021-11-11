package com.mcmoddev.orespawn.utils.codecs;

import com.google.common.collect.ImmutableList;

import com.mcmoddev.orespawn.data.BiomeMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

import java.util.Collections;
import java.util.List;

public class BiomeMatcherConfig {
	public final List<ResourceLocation> data;
	public final ResourceLocation type;

	public static final Codec<BiomeMatcherConfig> CODEC = RecordCodecBuilder.create((base) -> {
		return base.group(ResourceLocation.CODEC.listOf().fieldOf("data").forGetter((config) -> {
				return config.data;
			}),
			ResourceLocation.CODEC.fieldOf("type").forGetter((config) -> {
				return config.type;
			})).apply(base, BiomeMatcherConfig::new);
	});

	public BiomeMatcherConfig(final List<ResourceLocation> entryList, final ResourceLocation type) {
		this.data = ImmutableList.copyOf(entryList);
		this.type = type;
	}

	public BiomeMatcherConfig() {
		type = AllowDenyListBase.denyall;
		data = Collections.emptyList();
	}
}
