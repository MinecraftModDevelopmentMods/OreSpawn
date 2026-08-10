package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class LegacyOs3ProfileMigrationTest {
	@TempDir Path temporary;

	@Test
	void writesOnceAndPreservesTheExactSecondLaunch() throws Exception {
		assertEquals(LegacyOs3ProfileMigration.Result.WRITTEN,
				LegacyOs3ProfileMigration.apply(temporary, true, true, true, true, true, false, 3));
		Path profile = temporary.resolve("orespawn-worldgen.json"); byte[] first = Files.readAllBytes(profile);
		JsonObject root = new JsonParser().parse(new String(first, StandardCharsets.UTF_8)).getAsJsonObject();
		assertTrue(root.get("manage_vanilla_ores").getAsBoolean());
		assertTrue(root.get("suppress_all_ore_features").getAsBoolean());
		assertEquals(1, root.getAsJsonObject("retrogen").get("revision").getAsInt());
		assertEquals(3, root.getAsJsonObject("flat_bedrock").get("layers").getAsInt());
		assertEquals(LegacyOs3ProfileMigration.Result.ALREADY_MIGRATED,
				LegacyOs3ProfileMigration.apply(temporary, false, false, false, false, false, false, 1));
		assertArrayEquals(first, Files.readAllBytes(profile));
	}

	@Test
	void atomicFailureLeavesExistingProfileUntouched() throws Exception {
		Path profile = temporary.resolve("orespawn-worldgen.json"); byte[] original = "{\"schema_version\":6}\n".getBytes(StandardCharsets.UTF_8);
		Files.write(profile, original); Files.createDirectory(temporary.resolve("orespawn-worldgen.json.tmp"));
		assertThrows(java.io.IOException.class,
				() -> LegacyOs3ProfileMigration.apply(temporary, true, false, false, false, false, false, 1));
		assertArrayEquals(original, Files.readAllBytes(profile));
	}
}
