package zone.moddev.mc.orespawn.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

/** Immutable view of the effective geology profile for the active server. */
public final class GeologyProfileView {
	private final JsonObject root;

	GeologyProfileView(JsonObject root) {
		this.root = root.deepCopy();
	}

	public int schemaVersion() {
		return root.has("schema_version") ? root.get("schema_version").getAsInt() : 0;
	}

	public String geologyMode() {
		return root.has("geology_mode") ? root.get("geology_mode").getAsString() : "geome";
	}

	public Optional<Identifier> selectedTemplate() {
		if (!root.has("selected_template")) {
			return Optional.empty();
		}
		try {
			return Optional.of(Identifier.parse(root.get("selected_template").getAsString()));
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	public Set<Identifier> rockIds() {
		return keys("rocks");
	}

	public Set<Identifier> oreIds() {
		return keys("ores");
	}

	public Set<Identifier> fluidDepositIds() {
		return keys("fluid_deposits");
	}

	public Set<Identifier> geomeIds() {
		return keys("geomes");
	}

	public Set<Identifier> terrainDimensions() {
		return keys("terrain_dimensions");
	}

	public Set<Identifier> biomePaletteIds() {
		return keys("biome_palettes");
	}

	public Set<Identifier> dimensionMaterialIds() {
		return keys("dimension_materials");
	}

	/** Returns a defensive copy suitable for diagnostics or tooling. */
	public JsonObject toJson() {
		return root.deepCopy();
	}

	private Set<Identifier> keys(String section) {
		if (!root.has(section) || !root.get(section).isJsonObject()) {
			return Collections.emptySet();
		}
		Set<Identifier> values = new LinkedHashSet<>();
		for (String value : root.getAsJsonObject(section).keySet()) {
			try {
				values.add(Identifier.parse(value));
			} catch (RuntimeException ignored) {
				// Invalid user data is omitted from the typed view.
			}
		}
		return Collections.unmodifiableSet(values);
	}
}
