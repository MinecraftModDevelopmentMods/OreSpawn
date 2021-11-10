package com.mcmoddev.orespawn.data;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.utils.codecs.BiomeMatcherConfig;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static com.mcmoddev.orespawn.utils.Helpers.makeBlockResourceLocation;

public class BiomeMatcher {
	private final List<RegistryKey<Biome>> allowedBiomes = new LinkedList<>();
	private final BiomeMatcherConfig myConfig;
	private final ResourceLocation type;

	private static final ResourceLocation whitelist = new ResourceLocation("orespawn4", "allowlist");
	private static final ResourceLocation blacklist = new ResourceLocation("orespawn4", "denylist");
	private static final ResourceLocation denyall = new ResourceLocation("orespawn4", "denyall");
	private static final ResourceLocation allowall = new ResourceLocation("orespawn4", "allowall");

	public BiomeMatcher(final BiomeMatcherConfig config) {
		myConfig = config;
		type = config.type;
		allowedBiomes.addAll(config.data.stream().map( rl -> RegistryKey.getOrCreateKey(Registry.BIOME_KEY, rl)).collect(Collectors.toList()));
	}

	public boolean matches(final String biomeName) {
		if (type.equals(denyall) || (allowedBiomes.isEmpty() && type.equals(whitelist))) return false;
		else if (type.equals(allowall) || (allowedBiomes.isEmpty() && type.equals(blacklist))) return true;
		else return matches(makeResourceLocation(biomeName));
	}

	private ResourceLocation makeResourceLocation(final String name) {
		String namespace = "minecraft";
		String biomeId = name;

		if (name.indexOf(':') > -1) {
			String bits[] = name.split(":");
			namespace = bits[0];
			biomeId = bits[1];
		}

		return new ResourceLocation(namespace, biomeId);
	}

	public boolean matches(final ResourceLocation biomeName) {
		return matches(RegistryKey.getOrCreateKey(Registry.BIOME_KEY, biomeName));
	}

	private boolean matches(final RegistryKey<Biome> biome) {
		if (type == blacklist && allowedBiomes.contains(biome)) return false;
		else if (type == whitelist && allowedBiomes.contains(biome)) return true;

		return false;
	}

	public BiomeMatcherConfig getConfig() {
		return myConfig;
	}
}
