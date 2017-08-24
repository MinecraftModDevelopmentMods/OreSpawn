package com.mcmoddev.orespawn.impl.location;

import com.google.common.collect.ImmutableSet;
import com.mcmoddev.orespawn.api.BiomeLocation;
import net.minecraft.world.biome.Biome;

import java.util.Objects;
import java.util.Set;

public final class BiomeLocationComposition implements BiomeLocation {
    private final ImmutableSet<BiomeLocation> inclusions;

    private final ImmutableSet<BiomeLocation> exclusions;

    private final int hash;

    public BiomeLocationComposition(ImmutableSet<BiomeLocation> inclusions, ImmutableSet<BiomeLocation> exclusions) {
        this.inclusions = inclusions;
        this.exclusions = exclusions;
		this.hash = Objects.hash(inclusions, exclusions);
    }

    @Override
    public boolean matches(Biome biome) {
        return checkTags(biome, this.inclusions, false) && checkTags(biome, this.exclusions, true);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof BiomeLocationComposition) {
            BiomeLocationComposition other = (BiomeLocationComposition) obj;
            return this.inclusions.equals(other.inclusions) && this.exclusions.equals(other.exclusions);
        }
        return false;
    }

    private static boolean checkTags(Biome biome, Set<BiomeLocation> locations, boolean failValue) {
        for (BiomeLocation location : locations) {
            if (location.matches(biome) == failValue) {
                return false;
            }
        }
        return true;
    }

	public ImmutableSet<BiomeLocation> getInclusions() {
		return this.inclusions;
	}
	
	public ImmutableSet<BiomeLocation> getExclusions() {
		return this.exclusions;
	}

}
