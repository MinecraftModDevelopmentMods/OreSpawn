package zone.moddev.mc.orespawn.api;

import java.util.Locale;

/** Controls whether a palette supplements or replaces matching source biomes. */
public enum BiomePlacementMode {
	AUGMENT,
	REPLACE;

	public String configName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
