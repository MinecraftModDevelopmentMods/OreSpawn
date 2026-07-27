package zone.moddev.mc.orespawn.api;

/** Geological families understood by OreSpawn's declarative engines. */
public enum GeologyFamily {
	SEDIMENTARY("sedimentary"),
	METAMORPHIC("metamorphic"),
	IGNEOUS_INTRUSIVE("igneous_intrusive"),
	IGNEOUS_VOLCANIC("igneous_volcanic");

	private final String configName;

	GeologyFamily(String configName) {
		this.configName = configName;
	}

	public String configName() {
		return configName;
	}
}
