package zone.moddev.mc.orespawn.worldgen;

public enum RockFamily {
	SEDIMENTARY("sedimentary"),
	METAMORPHIC("metamorphic"),
	IGNEOUS_INTRUSIVE("igneous_intrusive"),
	IGNEOUS_VOLCANIC("igneous_volcanic");

	public final String configName;

	private RockFamily(String configName) {
		this.configName = configName;
	}

	public static RockFamily fromConfigName(String name) {
		for (RockFamily family : values()) {
			if (family.configName.equalsIgnoreCase(name) || family.name().equalsIgnoreCase(name)) {
				return family;
			}
		}

		throw new IllegalArgumentException("Unknown rock family: " + name);
	}
}
