package com.mcmoddev.orespawn.impl.location;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.Objects;
import java.util.List;
import java.util.LinkedList;

public final class BiomeLocationComposition implements BiomeLocation {
    private final ImmutableSet<BiomeLocation> inclusions;

    private final ImmutableSet<BiomeLocation> exclusions;

    private final int hash;

    public BiomeLocationComposition(ImmutableSet<BiomeLocation> inclusions, ImmutableSet<BiomeLocation> exclusions) {
        this.inclusions = inclusions;
        this.exclusions = exclusions;
		this.hash = Objects.hash(inclusions, exclusions);
    }

    private boolean matchBiome(Biome biome, BiomeLocation loc ) {
    	return (loc.getBiomes().stream().filter( b -> b.equals ( biome ) ).distinct().count() > 0);
    }

    @Override
    public boolean matches(Biome biome) {
        boolean inWhite = this.inclusions.asList().stream().anyMatch( bl -> matchBiome(biome, bl) );
        boolean inBlack = this.exclusions.asList().stream().anyMatch( bl -> matchBiome(biome, bl) );

        return !inBlack && inWhite;
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

    @Override
    public ImmutableList<Biome> getBiomes() {
    	List<Biome> temp = new LinkedList<>();
    	this.inclusions.stream ().forEach ( bl -> temp.addAll( bl.getBiomes () ) );
	    this.exclusions.stream ().forEach ( bl -> temp.addAll( bl.getBiomes () ) );
    	return ImmutableList.copyOf ( temp );
    }

	public ImmutableSet<BiomeLocation> getInclusions() {
		return this.inclusions;
	}
	
	public ImmutableSet<BiomeLocation> getExclusions() {
		return this.exclusions;
	}

}
