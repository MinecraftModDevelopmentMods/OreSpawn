package com.mcmoddev.orespawn.api;

import java.util.Locale;

import net.minecraft.resources.ResourceLocation;

/** Built-in dimension policies available to declarative ore providers. */
public enum OreDimensionSelector {
	ALL_EXCEPT_NETHER_AND_END("all_except_nether_end");

	private final ResourceLocation id;

	OreDimensionSelector(String path) {
		this.id = new ResourceLocation("orespawn", path);
	}

	public ResourceLocation id() {
		return id;
	}

	public static OreDimensionSelector fromId(ResourceLocation id) {
		for (OreDimensionSelector selector : values()) {
			if (selector.id.equals(id)) return selector;
		}
		throw new IllegalArgumentException("Unknown ore dimension selector: " + id);
	}

	public static OreDimensionSelector fromName(String value) {
		ResourceLocation id = value.indexOf(':') >= 0
				? new ResourceLocation(value)
				: new ResourceLocation("orespawn", value.toLowerCase(Locale.ROOT));
		return fromId(id);
	}
}
