package zone.moddev.mc.orespawn.api;

import zone.moddev.mc.orespawn.util.JsonCopies;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.util.ResourceLocation;

/** Immutable view of the effective geology profile for the active server. */
public final class GeologyProfileView {
	private final JsonObject root;

	GeologyProfileView(JsonObject root) {
		this.root = JsonCopies.copy(root);
	}

	public int schemaVersion() {
		return root.has("schema_version") ? root.get("schema_version").getAsInt() : 0;
	}

	public String geologyMode() {
		return root.has("geology_mode") ? root.get("geology_mode").getAsString() : "geome";
	}

	public Optional<ResourceLocation> selectedTemplate() {
		if (!root.has("selected_template")) {
			return Optional.empty();
		}
		try {
			return Optional.of(new ResourceLocation(root.get("selected_template").getAsString()));
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	public Set<ResourceLocation> rockIds() {
		return keys("rocks");
	}

	public Set<ResourceLocation> oreIds() {
		return keys("ores");
	}

	public Set<ResourceLocation> fluidDepositIds() {
		return keys("fluid_deposits");
	}

	public Set<ResourceLocation> geomeIds() {
		return keys("geomes");
	}

	public Set<ResourceLocation> terrainDimensions() {
		return keys("terrain_dimensions");
	}

	public Set<ResourceLocation> biomePaletteIds() {
		return keys("biome_palettes");
	}

	public Set<ResourceLocation> dimensionMaterialIds() {
		return keys("dimension_materials");
	}

	/** Returns a defensive copy suitable for diagnostics or tooling. */
	public JsonObject toJson() {
		return JsonCopies.copy(root);
	}

	private Set<ResourceLocation> keys(String section) {
		if (!root.has(section) || !root.get(section).isJsonObject()) {
			return Collections.emptySet();
		}
		Set<ResourceLocation> values = new LinkedHashSet<>();
		for (String value : JsonCopies.keys(root.getAsJsonObject(section))) {
			try {
				values.add(new ResourceLocation(value));
			} catch (RuntimeException ignored) {
				// Invalid user data is omitted from the typed view.
			}
		}
		return Collections.unmodifiableSet(values);
	}
}
