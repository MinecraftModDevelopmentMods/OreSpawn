package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SurfaceFixturePositionTest {
	@Test
	void rawFixtureWritesUseImmutablePositions() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get(
				"src/biomeIntegrationTest/java/zone/moddev/mc/orespawn/testmod/SurfaceProbeTestMod.java")),
				StandardCharsets.UTF_8);
		int start = source.indexOf("private static void placeRawNaturalSources(");
		assertTrue(start >= 0);
		int end = source.indexOf("\n\tprivate static ", start + 1);
		String setup = source.substring(start, end);
		assertFalse(Pattern.compile("chunk\\.setBlockState\\(pos\\.setPos\\([^)]*\\),").matcher(setup).find(),
				"Falling blocks may schedule a tick as soon as the fixture places them");
		assertEquals(3, setup.split("\\.toImmutable\\(\\)", -1).length - 1);
	}
}
