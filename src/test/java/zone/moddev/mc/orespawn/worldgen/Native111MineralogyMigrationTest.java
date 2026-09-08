package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.moddev.mc.orespawn.test.Forge13TestBootstrap;

/** Exact native Mineralogy 1.11 lineage and ambiguous-target fallback contract. */
class Native111MineralogyMigrationTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge13TestBootstrap.registerVanilla();
	}

	@Test
	void mineralogy330UsesExactNative111Defaults(@TempDir Path root) throws Exception {
		Path world = existingWorld(root, "3.3.0");
		Path config = Files.createDirectories(root.resolve("config"));

		WorldGeologyProfile migrated = LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false));
		JsonObject cyano = migrated.toJson().getAsJsonObject("cyano");

		assertEquals("Mineralogy 1.11", cyano.get("legacy_lineage").getAsString());
		assertTrue(migrated.cyanoEnabled());
		assertFalse(migrated.cyanoRealisticCoalLayers());
		assertEquals(100, migrated.cyanoGeomeSize());
		assertEquals(32.0D, migrated.cyanoRockLayerNoise());
		assertEquals(8, migrated.cyanoLayerThickness());
		assertEquals(Arrays.asList(
				"mineralogy:andesite", "mineralogy:basalt", "mineralogy:diorite",
				"mineralogy:granite", "mineralogy:rhyolite", "mineralogy:pegmatite",
				"mineralogy:pumice"), strings(cyano, "igneous_rocks"));
		assertEquals(Arrays.asList(
				"mineralogy:shale", "mineralogy:conglomerate", "mineralogy:dolomite",
				"mineralogy:limestone", "mineralogy:marble", "minecraft:sandstone",
				"mineralogy:chert", "mineralogy:gypsum"), strings(cyano, "sedimentary_rocks"));
		assertEquals(Arrays.asList(
				"mineralogy:slate", "mineralogy:schist", "mineralogy:gneiss",
				"mineralogy:phyllite", "mineralogy:amphibolite"),
				strings(cyano, "metamorphic_rocks"));

		assertAmbiguousMetadataUsesNative111(root.resolve("ambiguous"));
	}

	private void assertAmbiguousMetadataUsesNative111(Path root)
			throws Exception {
		Path world = existingWorld(root, "");
		Path config = Files.createDirectories(root.resolve("config"));

		WorldGeologyProfile migrated = LegacyMineralogyProfileMigration.migrateIfNeeded(
				world, config, WorldGeologyProfile.recommended(false));

		assertEquals("Mineralogy 1.11", migrated.toJson().getAsJsonObject("cyano")
				.get("legacy_lineage").getAsString());
		String report = new String(Files.readAllBytes(
				world.resolve("serverconfig/orespawn-upgrade-report.txt")), StandardCharsets.UTF_8);
		assertTrue(report.contains("Ambiguous lineage fallback: true"));
		assertTrue(report.contains("using the native Minecraft 1.11 Mineralogy lineage"));
	}

	private static Path existingWorld(Path root, String version) throws Exception {
		Path world = root.resolve("world");
		Files.createDirectories(world.resolve("region"));
		Files.write(world.resolve("region/r.0.0.mca"), new byte[] { 0 });
		NBTTagCompound data = new NBTTagCompound();
		NBTTagCompound fml = new NBTTagCompound();
		NBTTagList mods = new NBTTagList();
		NBTTagCompound mod = new NBTTagCompound();
		mod.setString("ModId", "mineralogy");
		mod.setString("ModVersion", version);
		mods.appendTag(mod);
		fml.setTag("ModList", mods);
		data.setTag("FML", fml);
		try (FileOutputStream output = new FileOutputStream(world.resolve("level.dat").toFile())) {
			CompressedStreamTools.writeCompressed(data, output);
		}
		return world;
	}

	private static List<String> strings(JsonObject parent, String key) {
		List<String> values = new ArrayList<>();
		for (JsonElement value : parent.getAsJsonArray(key)) values.add(value.getAsString());
		return values;
	}
}
