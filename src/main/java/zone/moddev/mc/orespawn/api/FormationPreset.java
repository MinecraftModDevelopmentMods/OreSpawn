package zone.moddev.mc.orespawn.api;

/** Named formation scales accepted by the world profile. */
public enum FormationPreset {
	TINY("tiny"),
	SMALL("small"),
	AVERAGE("average"),
	LARGE("large"),
	HUGE("huge"),
	CUSTOM("custom");

	private final String configName;

	FormationPreset(String configName) {
		this.configName = configName;
	}

	public String configName() {
		return configName;
	}
}
