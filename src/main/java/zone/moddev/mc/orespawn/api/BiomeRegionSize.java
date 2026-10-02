package zone.moddev.mc.orespawn.api;

import java.util.Locale;

/** Named sizes for biome-overlay regions, measured in blocks. */
public enum BiomeRegionSize {
	TINY(128),
	SMALL(256),
	AVERAGE(512),
	LARGE(1024),
	HUGE(2048);

	private final int blocks;

	BiomeRegionSize(int blocks) {
		this.blocks = blocks;
	}

	public int blocks() {
		return blocks;
	}

	public String configName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
