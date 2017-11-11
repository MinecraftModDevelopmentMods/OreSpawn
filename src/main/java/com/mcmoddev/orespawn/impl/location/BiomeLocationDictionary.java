package com.mcmoddev.orespawn.impl.location;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.api.BiomeLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

public final class BiomeLocationDictionary implements BiomeLocation {
    private final BiomeDictionary.Type type;

    private final int hash;

    public BiomeLocationDictionary(BiomeDictionary.Type type) {
        this.type = type;
		this.hash = type.hashCode();
    }

    @Override
    public boolean matches(Biome biome) {
        return BiomeDictionary.hasType(biome, this.type);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj == this) || ((obj instanceof BiomeLocationDictionary) && this.type.equals ( ((BiomeLocationDictionary) obj).type ));
    }

	public BiomeDictionary.Type getType() {
		return this.type;
	}

	@Override
    public ImmutableList<Biome> getBiomes() {
        return ImmutableList.copyOf (BiomeDictionary.getBiomes ( this.type ) );
  }
}
