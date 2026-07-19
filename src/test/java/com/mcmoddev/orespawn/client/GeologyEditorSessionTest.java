package com.mcmoddev.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.worldgen.WorldGeologyProfile;

class GeologyEditorSessionTest {
	@Test
	void availableDimensionsPutVanillaFirstAndIncludeInstalledAndConfiguredIds() {
		GeologyEditorSession session = new GeologyEditorSession(
				WorldGeologyProfile.recommended(false),
				Arrays.asList("zeta:moon", "alpha:void", "not a valid id"));

		JsonObject dimensions = new JsonObject();
		dimensions.add("example:caverns", new JsonObject());
		session.ore("example:test_ore").add("dimensions", dimensions);

		assertEquals(Arrays.asList(
				"minecraft:overworld",
				"minecraft:the_nether",
				"minecraft:the_end",
				"alpha:void",
				"example:caverns",
				"zeta:moon"), session.availableDimensionIds());
	}
}
