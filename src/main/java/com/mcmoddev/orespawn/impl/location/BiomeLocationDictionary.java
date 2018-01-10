package com.mcmoddev.orespawn.impl.location;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.api.BiomeLocation;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;

public final class BiomeLocationDictionary implements BiomeLocation {
	private final BiomeDictionary.Type type;

	private final int hash;

	public BiomeLocationDictionary(BiomeDictionary.Type type) {
		this.type = type;
		this.hash = type.hashCode();
	}

	@Override
	public boolean matches(BiomeGenBase biome) {
		return BiomeDictionary.isBiomeOfType(biome, this.type);
	}

	@Override
	public int hashCode() {
		return this.hash;
	}

	@Override
	public boolean equals(Object obj) {
		return (obj == this) || ((obj instanceof BiomeLocationDictionary) && this.type.equals(((BiomeLocationDictionary) obj).type));
	}

	public BiomeDictionary.Type getType() {
		return this.type;
	}

	@Override
	public ImmutableList<BiomeGenBase> getBiomes() {
		return ImmutableList.copyOf(BiomeDictionary.getBiomesForType(this.type));
	}
}
