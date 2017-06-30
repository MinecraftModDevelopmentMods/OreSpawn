package com.mcmoddev.orespawn.api.plugin;

import com.mcmoddev.orespawn.api.OreSpawnAPI;

@FunctionalInterface
public interface IOreSpawnPlugin {
	void register(OreSpawnAPI apiInterface);
}
