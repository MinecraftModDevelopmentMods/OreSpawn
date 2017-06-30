package com.mcmoddev.orespawn.util.location;

import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import net.minecraft.world.biome.Biome;

public class CompositionLocation implements ILocation {

	private final ImmutableSet<ILocation> inclusions;

	private final ImmutableSet<ILocation> exclusions;

	private final int hash;

	public CompositionLocation(ImmutableSet<ILocation> inclusions, ImmutableSet<ILocation> exclusions) {
		this.inclusions = inclusions;
		this.exclusions = exclusions;
		hash = Objects.hash(inclusions, exclusions);
	}

	@Override
	public boolean matches(Biome biome) {
		return checkTags(biome, inclusions, false) && checkTags(biome, exclusions, true);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj instanceof CompositionLocation) {
			CompositionLocation other = (CompositionLocation) obj;
			return inclusions.equals(other.inclusions) && exclusions.equals(other.exclusions);
		}
		return false;
	}

	private boolean checkTags(Biome biome, Set<ILocation> locations, boolean failValue) {
		for (ILocation location : locations) {
			if (location.matches(biome) == failValue) {
				return false;
			}
		}
		return true;
	}

}
