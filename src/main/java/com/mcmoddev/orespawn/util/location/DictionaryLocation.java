package com.mcmoddev.orespawn.util.location;

import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

public class DictionaryLocation implements ILocation {

	private final BiomeDictionary.Type type;

	private final int hash;

	public DictionaryLocation(BiomeDictionary.Type type) {
		this.type = type;
		hash = type.hashCode();
	}

	@Override
	public boolean matches(Biome biome) {
		return BiomeDictionary.getBiomes(type).contains(biome);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof DictionaryLocation && type == ((DictionaryLocation) obj).type;
}
}
