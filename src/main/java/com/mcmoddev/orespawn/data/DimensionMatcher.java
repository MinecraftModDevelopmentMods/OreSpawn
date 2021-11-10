package com.mcmoddev.orespawn.data;

import com.mcmoddev.orespawn.utils.codecs.DimensionMatcherConfig;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.Dimension;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static com.mcmoddev.orespawn.utils.Helpers.makeBlockResourceLocation;

public class DimensionMatcher {
	private final List<RegistryKey<Dimension>> allowedDimensions = new LinkedList<>();
	private final DimensionMatcherConfig myConfig;
	private boolean whitelistEmpty = false;
	private boolean blacklistEmpty = false;
	private boolean overworlds = false;
	private boolean voids = false;
	private boolean hells = false;

	private static final class internal {
		public static final ResourceLocation OVERWORLDS = new ResourceLocation("orespawn4", "overworlds");
		public static final ResourceLocation NETHERS = new ResourceLocation("orespawn4", "nethers");
		public static final ResourceLocation ENDS = new ResourceLocation("orespawn4", "ends");
	}

	public DimensionMatcher(final DimensionMatcherConfig config) {
		myConfig = config;

		if (config.whitelist.isEmpty()) whitelistEmpty = true;
		if (config.blacklist.isEmpty()) blacklistEmpty = true;

		if (whitelistEmpty && blacklistEmpty) return;

		if (!whitelistEmpty)
			if (config.whitelist.contains(internal.OVERWORLDS))
				overworlds = true;
			else if (config.whitelist.contains(internal.NETHERS))
				hells = true;
			else if (config.whitelist.contains(internal.ENDS))
				voids = true;
			else
				allowedDimensions.addAll(config.whitelist.stream().map(rl -> RegistryKey.getOrCreateKey(Registry.DIMENSION_KEY, rl)).collect(Collectors.toList()));
		else
			if (!blacklistEmpty)
				if (config.blacklist.contains(internal.OVERWORLDS))
					overworlds = true;
				else if (config.blacklist.contains(internal.NETHERS))
					hells = true;
				else if (config.blacklist.contains(internal.ENDS))
					voids = true;
				else
					allowedDimensions.addAll(config.blacklist.stream().map(rl -> RegistryKey.getOrCreateKey(Registry.DIMENSION_KEY, rl)).collect(Collectors.toList()));
	}

	public boolean matches(final String dimensionName) {
		return false;
	}

	public boolean matches(final ResourceLocation dimensionName) {
		return false;
	}

	private boolean matches(final RegistryKey<Dimension> dimensionName) {
		return false;
	}

	public DimensionMatcherConfig getConfig() {
		return myConfig;
	}
}
