package com.mcmoddev.orespawn.data;

import com.mcmoddev.orespawn.utils.codecs.AllowDenyListBase;
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

public class DimensionMatcher extends AllowDenyListBase<RegistryKey<Registry<Dimension>>> {
	private final DimensionMatcherConfig myConfig;

	public DimensionMatcher(final DimensionMatcherConfig config) {
		super(config.type, Registry.DIMENSION_KEY, config.data);
		myConfig = config;
	}

	public DimensionMatcherConfig getConfig() {
		return myConfig;
	}
}
