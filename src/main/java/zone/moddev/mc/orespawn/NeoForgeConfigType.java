package zone.moddev.mc.orespawn;

import net.neoforged.fml.config.ModConfig;

final class NeoForgeConfigType {

	private NeoForgeConfigType() {
	}

	static ModConfig.Type local() {
		// NeoForge 26.3 renamed COMMON to LOCAL. Resolve the name so the same jar works on both.
		try {
			return ModConfig.Type.valueOf("LOCAL");
		} catch (IllegalArgumentException missingLocalName) {
			return ModConfig.Type.valueOf("COMMON");
		}
	}
}
