package com.mcmoddev.orespawn;

import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.api.plugin.IOreSpawnPlugin;
import com.mcmoddev.orespawn.api.plugin.OreSpawnPlugin;

/** Exact Forge 1.11 Base Metals OreSpawn plugin contract from commit f6ceb967. */
@OreSpawnPlugin(modid = "basemetals", resourcePath = "orespawn")
public class BaseMetalsOreSpawn implements IOreSpawnPlugin {
	@Override
	public void register(OS3API apiInterface) {
		// The historical provider is declarative and is read from the jar.
	}
}
