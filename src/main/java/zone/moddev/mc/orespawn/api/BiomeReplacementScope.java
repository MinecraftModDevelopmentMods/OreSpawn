package zone.moddev.mc.orespawn.api;

import java.util.Locale;

/** Selects which source-biome namespaces a palette may transform. */
public enum BiomeReplacementScope {
	ALL,
	MINECRAFT_ONLY,
	SELECTED_NAMESPACES;

	public String configName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
