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
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class LegacyMineralogyProfileMigrationTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void existingMineralogyThreeWorldSnapshotsCyanoConfigAndExactRockOrder(
			@TempDir Path temporaryDirectory) throws IOException {
		Path world = existingWorld(temporaryDirectory, "mineralogy", "3.3.8");
		Path config = temporaryDirectory.resolve("config");
		Files.createDirectories(config);
		Files.write(config.resolve("mineralogy.cfg"), (
				"world-gen {\n"
				+ "    I:GEOME_SIZE=144\n"
				+ "    B:REALISTIC_COAL_LAYERS=true\n"
				+ "    S:ROCK_LAYER_NOISE=41.5\n"
				+ "    I:ROCK_LAYER_THICKNESS=11\n"
				+ "    S:igneous_whitelist=minecraft:obsidian;mineralogy:diabase\n"
				+ "    S:igneous_blacklist=mineralogy:gabbro\n"
				+ "    S:metamorphic_whitelist=minecraft:cobblestone\n"
				+ "    S:metamorphic_blacklist=mineralogy:slate\n"
				+ "    S:sedimentary_whitelist=minecraft:gravel\n"
				+ "    S:sedimentary_blacklist=mineralogy:gypsum\n"
				+ "}\n").getBytes(StandardCharsets.UTF_8));

		WorldGeologyProfile migrated = LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false));

		assertEquals(GeologyMode.LEGACY, migrated.geologyMode());
		assertEquals(144, migrated.cyanoGeomeSize());
		assertEquals(41.5D, migrated.cyanoRockLayerNoise());
		assertEquals(11, migrated.cyanoLayerThickness());
		assertTrue(migrated.cyanoRealisticCoalLayers());

		JsonObject cyano = migrated.toJson().getAsJsonObject("cyano");
		assertEquals("mineralogy-3.3.8", cyano.get("migrated_from").getAsString());
		assertTrue(cyano.get("legacy_config_found").getAsBoolean());
		assertEquals(list(
				"mineralogy:diabase", "mineralogy:peridotite", "mineralogy:basaltic_glass",
				"mineralogy:scoria", "mineralogy:tuff", "mineralogy:andesite",
				"mineralogy:basalt", "mineralogy:diorite", "mineralogy:granite",
				"mineralogy:rhyolite", "mineralogy:pegmatite", "mineralogy:pumice",
				"minecraft:obsidian", "mineralogy:diabase"), strings(cyano, "igneous_rocks"));
		assertEquals(list(
				"mineralogy:hornfels", "mineralogy:quartzite", "mineralogy:novaculite",
				"mineralogy:schist", "mineralogy:gneiss", "mineralogy:phyllite",
				"mineralogy:amphibolite", "minecraft:cobblestone"),
				strings(cyano, "metamorphic_rocks"));
		assertEquals(list(
				"mineralogy:siltstone", "mineralogy:shale", "mineralogy:conglomerate",
				"mineralogy:dolomite", "mineralogy:limestone", "mineralogy:marble",
				"minecraft:sandstone", "minecraft:coal_ore", "mineralogy:chert",
				"mineralogy:chalk", "mineralogy:rock_salt", "minecraft:gravel"),
				strings(cyano, "sedimentary_rocks"));
		String report = new String(Files.readAllBytes(world.resolve(
				"serverconfig/orespawn-upgrade-report.txt")), StandardCharsets.UTF_8);
		assertTrue(report.contains("Existing Mineralogy 3.3.8 world detected"));
		assertTrue(report.contains("Geology remains on the Cyano engine"));
		assertTrue(report.contains("Geome size: 144"));
		assertTrue(report.contains("Realistic coal layers: true"));
		assertTrue(report.contains("original legacy configuration and existing chunks were left unchanged"));
	}

	@Test
	void existingMineralogyWorldWithoutCopiedConfigUsesPublishedDefaults(
			@TempDir Path temporaryDirectory) throws IOException {
		Path world = existingWorld(temporaryDirectory, "mineralogy", "3.3.8");
		Path config = temporaryDirectory.resolve("empty-config");
		Files.createDirectories(config);

		WorldGeologyProfile migrated = LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false));

		assertEquals(GeologyMode.LEGACY, migrated.geologyMode());
		assertEquals(100, migrated.cyanoGeomeSize());
		assertEquals(32.0D, migrated.cyanoRockLayerNoise());
		assertEquals(8, migrated.cyanoLayerThickness());
		assertFalse(migrated.cyanoRealisticCoalLayers());
		JsonObject cyano = migrated.toJson().getAsJsonObject("cyano");
		assertFalse(cyano.get("legacy_config_found").getAsBoolean());
		assertFalse(strings(cyano, "sedimentary_rocks").contains("minecraft:coal_ore"));
	}

	@Test
	void freshWorldNeverSelectsLegacyModeFromAnInstalledConfig(
			@TempDir Path temporaryDirectory) throws IOException {
		Path world = temporaryDirectory.resolve("fresh-world");
		Files.createDirectories(world);
		writeLevelDat(world, "mineralogy", "3.3.8");
		Path config = temporaryDirectory.resolve("config");
		Files.createDirectories(config);
		Files.write(config.resolve("mineralogy.cfg"),
				"I:GEOME_SIZE=100\n".getBytes(StandardCharsets.UTF_8));

		assertNull(LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false)));
	}

	@Test
	void existingNonMineralogyWorldIsNotReclassified(@TempDir Path temporaryDirectory)
			throws IOException {
		Path world = existingWorld(temporaryDirectory, "examplemod", "1.0.0");
		Path config = temporaryDirectory.resolve("config");
		Files.createDirectories(config);
		Files.write(config.resolve("mineralogy.cfg"),
				"I:GEOME_SIZE=100\n".getBytes(StandardCharsets.UTF_8));

		assertNull(LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false)));
	}

	@Test
	void aModernMineralogyWorldIsNotReclassifiedAsCyano(@TempDir Path temporaryDirectory)
			throws IOException {
		Path world = existingWorld(temporaryDirectory, "mineralogy", "6.0.0");
		Path config = temporaryDirectory.resolve("config");
		Files.createDirectories(config);
		Files.write(config.resolve("mineralogy.cfg"),
				"I:GEOME_SIZE=100\n".getBytes(StandardCharsets.UTF_8));

		assertNull(LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false)));
	}

	@Test
	void geologyResolvesTheSnapshottedOrderInsteadOfProviderOrder() {
		WorldGeologyProfile base = WorldGeologyProfile.recommended(false);
		JsonObject root = base.rootCopy();
		JsonObject cyano = new JsonObject();
		cyano.add("sedimentary_rocks", array(
				"minecraft:sandstone", "minecraft:coal_ore", "minecraft:sandstone"));
		root.add("cyano", cyano);
		WorldGeologyProfile profile = base.withRoot(root);

		IBlockState[] resolved = Geology.resolveRockOrder(profile, "sedimentary_rocks",
				new IBlockState[] { Blocks.BEDROCK.getDefaultState() });

		assertEquals(3, resolved.length);
		assertEquals(Blocks.SANDSTONE, resolved[0].getBlock());
		assertEquals(Blocks.COAL_ORE, resolved[1].getBlock());
		assertEquals(Blocks.SANDSTONE, resolved[2].getBlock());
	}

	@Test
	void unavailableLegacyRockFamilyFallsBackWithoutCrashing() {
		WorldGeologyProfile base = WorldGeologyProfile.recommended(false);
		JsonObject root = base.rootCopy();
		JsonObject cyano = new JsonObject();
		cyano.add("igneous_rocks", array("missingmod:removed_rock"));
		root.add("cyano", cyano);
		IBlockState[] fallback = { Blocks.OBSIDIAN.getDefaultState() };

		IBlockState[] resolved = Geology.resolveRockOrder(base.withRoot(root),
				"igneous_rocks", fallback);

		assertEquals(1, resolved.length);
		assertEquals(Blocks.OBSIDIAN, resolved[0].getBlock());
	}

	@Test
	void repeatedMigrationProducesTheSameWorldSnapshot(@TempDir Path temporaryDirectory)
			throws IOException {
		Path world = existingWorld(temporaryDirectory, "mineralogy", "3.3.8");
		Path config = temporaryDirectory.resolve("config");
		Files.createDirectories(config);
		Files.write(config.resolve("mineralogy.cfg"), (
				"I:GEOME_SIZE=100\nS:ROCK_LAYER_NOISE=32.0\n"
				+ "I:ROCK_LAYER_THICKNESS=8\n").getBytes(StandardCharsets.UTF_8));

		WorldGeologyProfile first = LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false));
		Path report = world.resolve("serverconfig/orespawn-upgrade-report.txt");
		byte[] firstReport = Files.readAllBytes(report);
		WorldGeologyProfile second = LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false));

		assertEquals(first.toJson(), second.toJson());
		assertTrue(java.util.Arrays.equals(firstReport, Files.readAllBytes(report)));
	}

	@Test
	void unreadableCurrentMetadataFallsBackToLevelDatOld(@TempDir Path temporaryDirectory)
			throws IOException {
		Path world = existingWorld(temporaryDirectory, "mineralogy", "3.3.8");
		Files.move(world.resolve("level.dat"), world.resolve("level.dat_old"));
		Files.write(world.resolve("level.dat"), new byte[] { 1, 2, 3, 4 });
		Path config = temporaryDirectory.resolve("config");
		Files.createDirectories(config);

		WorldGeologyProfile migrated = LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false));

		assertEquals(GeologyMode.LEGACY, migrated.geologyMode());
		assertEquals("mineralogy-3.3.8",
				migrated.toJson().getAsJsonObject("cyano").get("migrated_from").getAsString());
	}

	private static Path existingWorld(Path root, String modId, String version) throws IOException {
		Path world = root.resolve("world");
		Files.createDirectories(world.resolve("region"));
		Files.write(world.resolve("region").resolve("r.0.0.mca"), new byte[] { 0 });
		writeLevelDat(world, modId, version);
		return world;
	}

	private static void writeLevelDat(Path world, String modId, String version) throws IOException {
		NBTTagCompound root = new NBTTagCompound();
		NBTTagCompound fml = new NBTTagCompound();
		NBTTagList modList = new NBTTagList();
		NBTTagCompound mod = new NBTTagCompound();
		mod.setString("ModId", modId);
		mod.setString("ModVersion", version);
		modList.appendTag(mod);
		fml.setTag("ModList", modList);
		root.setTag("FML", fml);
		try (FileOutputStream output = new FileOutputStream(world.resolve("level.dat").toFile())) {
			CompressedStreamTools.writeCompressed(root, output);
		}
	}

	private static List<String> strings(JsonObject parent, String key) {
		List<String> result = new ArrayList<>();
		JsonArray values = parent.getAsJsonArray(key);
		for (JsonElement value : values) result.add(value.getAsString());
		return result;
	}

	private static List<String> list(String... values) {
		List<String> result = new ArrayList<>();
		for (String value : values) result.add(value);
		return result;
	}

	private static JsonArray array(String... values) {
		JsonArray result = new JsonArray();
		for (String value : values) result.add(new JsonPrimitive(value));
		return result;
	}
}
