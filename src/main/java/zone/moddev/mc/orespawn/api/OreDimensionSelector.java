package zone.moddev.mc.orespawn.api;

import java.util.Locale;

import net.minecraft.resources.Identifier;

/** Built-in dimension policies available to declarative ore providers. */
public enum OreDimensionSelector {
	ALL_EXCEPT_NETHER_AND_END("all_except_nether_end");

	private final Identifier id;

	OreDimensionSelector(String path) {
		this.id = Identifier.fromNamespaceAndPath("orespawn", path);
	}

	public Identifier id() {
		return id;
	}

	public static OreDimensionSelector fromId(Identifier id) {
		for (OreDimensionSelector selector : values()) {
			if (selector.id.equals(id)) return selector;
		}
		throw new IllegalArgumentException("Unknown ore dimension selector: " + id);
	}

	public static OreDimensionSelector fromName(String value) {
		Identifier id = value.indexOf(':') >= 0
				? Identifier.parse(value)
				: Identifier.fromNamespaceAndPath("orespawn", value.toLowerCase(Locale.ROOT));
		return fromId(id);
	}
}
