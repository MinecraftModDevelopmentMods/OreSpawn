package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/** Locks the explicit Forge 1.10 terrain-event ordering used instead of feature stages. */
class BiomeSurfaceFeatureOrderTest {
	@Test
	void coordinatorRunsSurfacesBeforeManagedOresAndLeavesStaticBiomeListsAlone() throws Exception {
		String source = source("OreSpawnWorldGenerator.java");
		int stone = source.indexOf("StoneReplacer.FEATURE.generate");
		int surface = source.indexOf("BiomeSurfaceFeature.FEATURE.generate");
		int fluid = source.indexOf("FluidDepositFeature.FEATURE.generate");
		int bedrock = source.indexOf("FlatBedrockFeature.FEATURE.generate");
		int ores = source.indexOf("OreSpawnOreGeneration.FEATURE.generate");
		assertTrue(stone >= 0 && stone < surface && surface < fluid && fluid < bedrock,
				"early terrain work must preserve geology, surface, fluid and bedrock order");
		assertTrue(ores > bedrock, "managed ores must run through the later OreGen event path");
		assertTrue(source.contains("beforeBiomeDecoration(DecorateBiomeEvent.Pre event)"));
		assertTrue(source.contains("beforeVanillaOres(OreGenEvent.Pre event)"));
		assertTrue(source.contains("@SubscribeEvent(priority = EventPriority.LOWEST)\r\n\tpublic void filterVanillaOre")
				|| source.contains("@SubscribeEvent(priority = EventPriority.LOWEST)\n\tpublic void filterVanillaOre"),
				"vanilla-ore suppression must make its decision at Forge's final standard event priority");
		assertTrue(source.contains("OreSpawnOreGeneration.takesOverVanillaOre(dimension, output)"));
		assertTrue(source.contains("event.setResult(Event.Result.DENY)"));
		assertTrue(source.contains("earlyComplete.add(key)") && source.contains("oreComplete.add(key)"),
				"terrain and ore callbacks must be independently deduplicated");
		assertTrue(source("BiomeFeatureInstaller.java").contains("ordered terrain-event"),
				"the target must not mutate static biome feature lists");
	}

	private static String source(String name) throws java.io.IOException {
		return new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "worldgen", name)), StandardCharsets.UTF_8);
	}
}
