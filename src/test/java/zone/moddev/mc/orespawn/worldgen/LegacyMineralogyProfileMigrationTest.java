package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;
import zone.moddev.mc.orespawn.test.Forge14TestBootstrap;

class LegacyMineralogyProfileMigrationTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge14TestBootstrap.registerVanilla();
	}

	@Test
	void carriedMineralogy110ConfigRetainsItsExactCyanoContract(@TempDir Path root)
			throws Exception {
		Path world = existingWorld(root, "3.3.8.26");
		Path config = config(root,
				"I:GEOME_SIZE=144\n"
				+ "B:REALISTIC_COAL_LAYERS=true\n"
				+ "S:ROCK_LAYER_NOISE=41.5\n"
				+ "I:ROCK_LAYER_THICKNESS=11\n"
				+ "S:igneous_whitelist=minecraft:obsidian;mineralogy:diabase\n"
				+ "S:igneous_blacklist=mineralogy:gabbro\n"
				+ "S:metamorphic_whitelist=minecraft:cobblestone\n"
				+ "S:metamorphic_blacklist=mineralogy:slate\n"
				+ "S:sedimentary_whitelist=minecraft:gravel\n"
				+ "S:sedimentary_blacklist=mineralogy:gypsum\n");
		String originalHash = sha256(config.resolve("mineralogy.cfg"));

		WorldGeologyProfile migrated = migrate(world, config);

		assertEquals(GeologyMode.LEGACY, migrated.geologyMode());
		assertTrue(migrated.cyanoEnabled());
		assertTrue(migrated.cyanoRealisticCoalLayers());
		assertEquals(144, migrated.cyanoGeomeSize());
		assertEquals(41.5D, migrated.cyanoRockLayerNoise());
		assertEquals(11, migrated.cyanoLayerThickness());
		JsonObject cyano = migrated.toJson().getAsJsonObject("cyano");
		assertEquals("Mineralogy 1.10", cyano.get("legacy_lineage").getAsString());
		assertEquals(list(
				"mineralogy:diabase", "mineralogy:peridotite", "mineralogy:basaltic_glass",
				"mineralogy:scoria", "mineralogy:tuff", "mineralogy:andesite",
				"mineralogy:basalt", "mineralogy:diorite", "mineralogy:granite",
				"mineralogy:rhyolite", "mineralogy:pegmatite", "mineralogy:pumice",
				"minecraft:obsidian", "mineralogy:diabase"), strings(cyano, "igneous_rocks"));
		assertTrue(strings(cyano, "sedimentary_rocks").contains("minecraft:coal_ore"));
		assertEquals(originalHash, sha256(config.resolve("mineralogy.cfg")));
		assertReport(world, "Selected config lineage: Mineralogy 1.10",
				"Realistic coal layers: true", "Geology remains on the Cyano engine");
	}

	@Test
	void nativeMineralogy112ConfigRetainsItsDifferentRockOrder(@TempDir Path root)
			throws Exception {
		Path world = existingWorld(root, "3.8.0.53");
		Path config = config(root,
				"B:PLACE_MINERALOGY_ROCK=true\n"
				+ "I:GEOME_SIZE=128\n"
				+ "S:ROCK_LAYER_NOISE=37.25\n"
				+ "I:ROCK_LAYER_THICKNESS=9\n");

		WorldGeologyProfile migrated = migrate(world, config);
		JsonObject cyano = migrated.toJson().getAsJsonObject("cyano");

		assertTrue(migrated.cyanoEnabled());
		assertFalse(migrated.cyanoRealisticCoalLayers());
		assertEquals("Mineralogy 1.12", cyano.get("legacy_lineage").getAsString());
		assertEquals(list(
				"mineralogy:andesite", "mineralogy:basalt", "mineralogy:diorite",
				"mineralogy:granite", "mineralogy:rhyolite", "mineralogy:pegmatite",
				"mineralogy:diabase", "mineralogy:gabbro", "mineralogy:peridotite",
				"mineralogy:basaltic_glass", "mineralogy:scoria", "mineralogy:tuff",
				"mineralogy:pumice"), strings(cyano, "igneous_rocks"));
		List<String> sedimentary = strings(cyano, "sedimentary_rocks");
		assertEquals(12, sedimentary.size());
		assertEquals(2, count(sedimentary, "mineralogy:rock_salt"),
				"Mineralogy 1.12 registered rock salt twice and its sampler saw both entries");
		assertFalse(sedimentary.contains("minecraft:coal_ore"));
		assertReport(world, "Selected config lineage: Mineralogy 1.12",
				"Realistic coal layers: false (not supported by Mineralogy 1.12)");
	}

	@Test
	void native112DisabledGeologyRemainsDisabled(@TempDir Path root) throws Exception {
		Path world = existingWorld(root, "3.8.0.53");
		Path config = config(root, "B:PLACE_MINERALOGY_ROCK=false\n");

		WorldGeologyProfile migrated = migrate(world, config);

		assertEquals(GeologyMode.LEGACY, migrated.geologyMode());
		assertFalse(migrated.cyanoEnabled());
		assertReport(world, "Legacy Mineralogy geology was disabled and remains disabled",
				"Geology enabled: false");
	}

	@Test
	void hybridFileFrom112Uses112EnableFlagAndIgnores110Coal(@TempDir Path root)
			throws Exception {
		Path world = existingWorld(root, "3.8.0.53");
		Path config = config(root,
				"B:PLACE_MINERALOGY_ROCK=false\nB:REALISTIC_COAL_LAYERS=true\n");

		WorldGeologyProfile migrated = migrate(world, config);
		JsonObject cyano = migrated.toJson().getAsJsonObject("cyano");

		assertEquals("Mineralogy 1.12", cyano.get("legacy_lineage").getAsString());
		assertTrue(cyano.get("hybrid_config").getAsBoolean());
		assertFalse(migrated.cyanoEnabled());
		assertFalse(migrated.cyanoRealisticCoalLayers());
		assertReport(world, "Hybrid 1.10/1.12 keys found: true",
				"Hybrid precedence: saved Mineralogy version");
	}

	@Test
	void hybridFileCarriedFrom110Uses110CoalAndHasNoNonexistentEnableFlag(@TempDir Path root)
			throws Exception {
		Path world = existingWorld(root, "3.3.8.26");
		Path config = config(root,
				"B:PLACE_MINERALOGY_ROCK=false\nB:REALISTIC_COAL_LAYERS=true\n");

		WorldGeologyProfile migrated = migrate(world, config);
		JsonObject cyano = migrated.toJson().getAsJsonObject("cyano");

		assertEquals("Mineralogy 1.10", cyano.get("legacy_lineage").getAsString());
		assertTrue(migrated.cyanoEnabled());
		assertTrue(migrated.cyanoRealisticCoalLayers());
		assertTrue(strings(cyano, "sedimentary_rocks").contains("minecraft:coal_ore"));
	}

	@Test
	void configMarkersResolveAmbiguousSavedVersionWithoutBroadening(@TempDir Path root)
			throws Exception {
		Path world = existingWorld(root, "");
		Path config = config(root, "B:REALISTIC_COAL_LAYERS=true\n");

		WorldGeologyProfile migrated = migrate(world, config);

		assertEquals("Mineralogy 1.10", migrated.toJson().getAsJsonObject("cyano")
				.get("legacy_lineage").getAsString());
		assertTrue(migrated.cyanoRealisticCoalLayers());
	}

	@Test
	void existingWorldWithoutConfigUsesLineageSpecificPublishedDefaults(@TempDir Path root)
			throws Exception {
		Path world = existingWorld(root, "3.8.0.53");
		Path config = root.resolve("empty-config");
		Files.createDirectories(config);

		WorldGeologyProfile migrated = migrate(world, config);

		assertTrue(migrated.cyanoEnabled());
		assertEquals(100, migrated.cyanoGeomeSize());
		assertEquals(12, migrated.cyanoRockOrder("sedimentary_rocks").size());
		assertReport(world, "no; published Mineralogy 1.12 defaults used");
	}

	@Test
	void freshOrNonLegacyWorldIsNeverReclassifiedFromInstalledConfig(@TempDir Path root)
			throws Exception {
		Path fresh = root.resolve("fresh");
		Files.createDirectories(fresh);
		writeLevelDat(fresh, "mineralogy", "3.8.0.53");
		Path config = config(root, "B:PLACE_MINERALOGY_ROCK=true\n");
		assertNull(migrate(fresh, config));

		Path modern = existingWorld(root.resolve("modern"), "6.0.0");
		assertNull(migrate(modern, config));
		Path unrelated = existingWorld(root.resolve("other"), "examplemod", "1.0.0");
		assertNull(migrate(unrelated, config));
	}

	@Test
	void unreadableCurrentMetadataUsesLevelDatOldAndReportsIt(@TempDir Path root)
			throws Exception {
		Path world = existingWorld(root, "3.8.0.53");
		Files.move(world.resolve("level.dat"), world.resolve("level.dat_old"));
		Files.write(world.resolve("level.dat"), new byte[] { 1, 2, 3, 4 });
		Path config = config(root, "B:PLACE_MINERALOGY_ROCK=true\n");

		WorldGeologyProfile migrated = migrate(world, config);

		assertEquals("level.dat_old", migrated.toJson().getAsJsonObject("cyano")
				.get("legacy_metadata_source").getAsString());
		assertReport(world, "Saved mod metadata: level.dat_old");
	}

	@Test
	void migrationAndHumanReportAreByteStable(@TempDir Path root) throws Exception {
		Path world = existingWorld(root, "3.8.0.53");
		Path config = config(root, "B:PLACE_MINERALOGY_ROCK=true\nI:GEOME_SIZE=100\n");

		WorldGeologyProfile first = migrate(world, config);
		Path report = world.resolve("serverconfig/orespawn-upgrade-report.txt");
		byte[] firstReport = Files.readAllBytes(report);
		WorldGeologyProfile second = migrate(world, config);

		assertEquals(first.toJson(), second.toJson());
		assertTrue(Arrays.equals(firstReport, Files.readAllBytes(report)));
	}

	@Test
	void snapshottedOrderPreservesDuplicatesAndOnlyFallsBackWhenEntireFamilyIsMissing() {
		WorldGeologyProfile base = WorldGeologyProfile.recommended(false);
		JsonObject root = base.rootCopy();
		JsonObject cyano = new JsonObject();
		cyano.add("sedimentary_rocks", array(
				"minecraft:sandstone", "minecraft:coal_ore", "minecraft:sandstone"));
		root.add("cyano", cyano);
		IBlockState[] resolved = Geology.resolveRockOrder(base.withRoot(root),
				"sedimentary_rocks", new IBlockState[] { Blocks.BEDROCK.getDefaultState() });
		assertEquals(3, resolved.length);
		assertEquals(Blocks.SANDSTONE, resolved[0].getBlock());
		assertEquals(Blocks.COAL_ORE, resolved[1].getBlock());
		assertEquals(Blocks.SANDSTONE, resolved[2].getBlock());

		JsonObject missingRoot = base.rootCopy();
		JsonObject missingCyano = new JsonObject();
		missingCyano.add("igneous_rocks", array("missingmod:removed_rock"));
		missingRoot.add("cyano", missingCyano);
		IBlockState[] fallback = { Blocks.OBSIDIAN.getDefaultState() };
		assertEquals(Blocks.OBSIDIAN, Geology.resolveRockOrder(base.withRoot(missingRoot),
				"igneous_rocks", fallback)[0].getBlock());
	}

	private static WorldGeologyProfile migrate(Path world, Path config) {
		return LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false));
	}

	private static Path existingWorld(Path root, String version) throws IOException {
		return existingWorld(root, "mineralogy", version);
	}

	private static Path existingWorld(Path root, String modId, String version) throws IOException {
		Path world = root.resolve("world");
		Files.createDirectories(world.resolve("region"));
		Files.write(world.resolve("region/r.0.0.mca"), new byte[] { 0 });
		writeLevelDat(world, modId, version);
		return world;
	}

	private static Path config(Path root, String contents) throws IOException {
		Path config = root.resolve("config");
		Files.createDirectories(config);
		Files.write(config.resolve("mineralogy.cfg"), contents.getBytes(StandardCharsets.UTF_8));
		return config;
	}

	private static void writeLevelDat(Path world, String modId, String version) throws IOException {
		NBTTagCompound root = new NBTTagCompound();
		NBTTagCompound fml = new NBTTagCompound();
		NBTTagList mods = new NBTTagList();
		NBTTagCompound mod = new NBTTagCompound();
		mod.setString("ModId", modId);
		mod.setString("ModVersion", version);
		mods.appendTag(mod);
		fml.setTag("ModList", mods);
		root.setTag("FML", fml);
		try (FileOutputStream output = new FileOutputStream(world.resolve("level.dat").toFile())) {
			CompressedStreamTools.writeCompressed(root, output);
		}
	}

	private static void assertReport(Path world, String... fragments) throws IOException {
		String report = new String(Files.readAllBytes(
				world.resolve("serverconfig/orespawn-upgrade-report.txt")), StandardCharsets.UTF_8);
		for (String fragment : fragments) assertTrue(report.contains(fragment), fragment);
	}

	private static List<String> strings(JsonObject parent, String key) {
		List<String> result = new ArrayList<>();
		for (JsonElement value : parent.getAsJsonArray(key)) result.add(value.getAsString());
		return result;
	}

	private static int count(List<String> values, String expected) {
		int count = 0;
		for (String value : values) if (expected.equals(value)) count++;
		return count;
	}

	private static List<String> list(String... values) {
		return Arrays.asList(values);
	}

	private static JsonArray array(String... values) {
		JsonArray result = new JsonArray();
		for (String value : values) result.add(new JsonPrimitive(value));
		return result;
	}

	private static String sha256(Path path) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] result = digest.digest(Files.readAllBytes(path));
		StringBuilder hex = new StringBuilder();
		for (byte value : result) hex.append(String.format("%02X", value));
		return hex.toString();
	}
}
