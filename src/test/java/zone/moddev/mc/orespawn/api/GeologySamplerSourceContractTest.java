package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class GeologySamplerSourceContractTest {
	@Test
	void samplingUsesTheBiomeProviderWithoutLoadingAChunk() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get(
				"src/main/java/zone/moddev/mc/orespawn/api/OreSpawnGeologySampler.java")),
				StandardCharsets.UTF_8);
		assertTrue(source.contains("level.provider.getBiomeProvider().getBiome(position)"));
		assertFalse(source.contains("level.getBiome(position)"));
	}
}
