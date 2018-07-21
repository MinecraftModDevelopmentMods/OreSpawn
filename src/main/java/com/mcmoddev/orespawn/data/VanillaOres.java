package com.mcmoddev.orespawn.data;

import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.api.plugin.IOreSpawnPlugin;
import com.mcmoddev.orespawn.api.plugin.OreSpawnPlugin;

@OreSpawnPlugin(modid = "orespawn", resourcePath = "configs")
public class VanillaOres implements IOreSpawnPlugin {

	@Override
	public void register(OS3API apiInterface) {
		// nothing for us to do - all of our ores are in the
		// jar and the code handles that
	}

}
