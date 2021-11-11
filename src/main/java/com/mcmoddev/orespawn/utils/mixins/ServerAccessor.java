package com.mcmoddev.orespawn.utils.mixins;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.registry.DynamicRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface ServerAccessor {
	@Accessor
	DynamicRegistries.Impl getDynamicRegistries();
}
