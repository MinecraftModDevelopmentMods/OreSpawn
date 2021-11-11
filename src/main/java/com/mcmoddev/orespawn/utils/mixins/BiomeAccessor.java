package com.mcmoddev.orespawn.utils.mixins;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeGenerationSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Biome.class)
public interface BiomeAccessor {
	@Accessor
	BiomeGenerationSettings getGenerationSettings();
}
