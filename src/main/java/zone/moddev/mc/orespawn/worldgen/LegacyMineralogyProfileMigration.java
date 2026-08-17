package zone.moddev.mc.orespawn.worldgen;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;

/**
 * Snapshots the exact legacy Mineralogy geology contract before OreSpawn owns
 * geology for an existing world. Mineralogy 1.10 and 1.12 used related but
 * distinct configs and rock orders, so their lineages are deliberately kept
 * separate instead of being treated as one generic Cyano preset.
 */
final class LegacyMineralogyProfileMigration {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String MINERALOGY_CONFIG = "mineralogy.cfg";

	private static final List<String> IGNEOUS_110 = Arrays.asList(
			"mineralogy:diabase", "mineralogy:gabbro", "mineralogy:peridotite",
			"mineralogy:basaltic_glass", "mineralogy:scoria", "mineralogy:tuff",
			"mineralogy:andesite", "mineralogy:basalt", "mineralogy:diorite",
			"mineralogy:granite", "mineralogy:rhyolite", "mineralogy:pegmatite",
			"mineralogy:pumice");
	private static final List<String> METAMORPHIC_110 = Arrays.asList(
			"mineralogy:hornfels", "mineralogy:quartzite", "mineralogy:novaculite",
			"mineralogy:slate", "mineralogy:schist", "mineralogy:gneiss",
			"mineralogy:phyllite", "mineralogy:amphibolite");
	private static final List<String> SEDIMENTARY_110_BEFORE_COAL = Arrays.asList(
			"mineralogy:siltstone", "mineralogy:shale", "mineralogy:conglomerate",
			"mineralogy:dolomite", "mineralogy:limestone", "mineralogy:marble",
			"minecraft:sandstone");
	private static final List<String> SEDIMENTARY_110_AFTER_COAL = Arrays.asList(
			"mineralogy:chert", "mineralogy:gypsum", "mineralogy:chalk",
			"mineralogy:rock_salt");

	private static final List<String> IGNEOUS_112 = Arrays.asList(
			"mineralogy:andesite", "mineralogy:basalt", "mineralogy:diorite",
			"mineralogy:granite", "mineralogy:rhyolite", "mineralogy:pegmatite",
			"mineralogy:diabase", "mineralogy:gabbro", "mineralogy:peridotite",
			"mineralogy:basaltic_glass", "mineralogy:scoria", "mineralogy:tuff",
			"mineralogy:pumice");
	private static final List<String> METAMORPHIC_112 = Arrays.asList(
			"mineralogy:slate", "mineralogy:schist", "mineralogy:gneiss",
			"mineralogy:phyllite", "mineralogy:amphibolite", "mineralogy:hornfels",
			"mineralogy:quartzite", "mineralogy:novaculite");
	private static final List<String> SEDIMENTARY_112 = Arrays.asList(
			"mineralogy:shale", "mineralogy:conglomerate", "mineralogy:dolomite",
			"mineralogy:limestone", "mineralogy:siltstone", "mineralogy:marble",
			"minecraft:sandstone", "mineralogy:chert", "mineralogy:gypsum",
			"mineralogy:chalk", "mineralogy:rock_salt", "mineralogy:rock_salt");

	private LegacyMineralogyProfileMigration() {
	}

	static WorldGeologyProfile migrateIfNeeded(Path worldRoot, Path configDirectory,
			WorldGeologyProfile installedPackProfile) {
		if (!hasGeneratedOverworldChunks(worldRoot)) return null;

		MineralogyIdentity identity = legacyMineralogyIdentity(worldRoot);
		if (identity == null) return null;

		Path configPath = configDirectory.resolve(MINERALOGY_CONFIG);
		Map<String, String> values = readConfig(configPath);
		boolean configFound = Files.isRegularFile(configPath);
		Lineage lineage = lineage(identity.version, values);
		boolean hybridConfig = values.containsKey("place_mineralogy_rock")
				&& values.containsKey("realistic_coal_layers");
		boolean enabled = lineage == Lineage.MINERALOGY_112
				? bool(values, "place_mineralogy_rock", true) : true;
		boolean realisticCoal = lineage == Lineage.MINERALOGY_110
				&& bool(values, "realistic_coal_layers", false);
		int geomeSize = integer(values, "geome_size", 100, 4, Short.MAX_VALUE);
		double rockLayerNoise = decimal(values, "rock_layer_noise", 32.0D, 1.0D, Short.MAX_VALUE);
		int layerThickness = integer(values, "rock_layer_thickness", 8, 1, 255);

		List<String> igneous = legacyList(lineage == Lineage.MINERALOGY_110
				? IGNEOUS_110 : IGNEOUS_112, values,
				"igneous_whitelist", "igneous_blacklist");
		List<String> metamorphic = legacyList(lineage == Lineage.MINERALOGY_110
				? METAMORPHIC_110 : METAMORPHIC_112, values,
				"metamorphic_whitelist", "metamorphic_blacklist");
		List<String> sedimentaryBase;
		if (lineage == Lineage.MINERALOGY_110) {
			sedimentaryBase = new ArrayList<>(SEDIMENTARY_110_BEFORE_COAL);
			if (realisticCoal) sedimentaryBase.add("minecraft:coal_ore");
			sedimentaryBase.addAll(SEDIMENTARY_110_AFTER_COAL);
		} else {
			sedimentaryBase = new ArrayList<>(SEDIMENTARY_112);
		}
		List<String> sedimentary = legacyList(sedimentaryBase, values,
				"sedimentary_whitelist", "sedimentary_blacklist");

		JsonObject root = installedPackProfile.rootCopy();
		root.addProperty("geology_mode", GeologyMode.LEGACY.name().toLowerCase(Locale.ROOT));
		JsonObject cyano = root.has("cyano") && root.get("cyano").isJsonObject()
				? root.getAsJsonObject("cyano") : new JsonObject();
		cyano.addProperty("enabled", enabled);
		cyano.addProperty("geome_size", geomeSize);
		cyano.addProperty("rock_layer_noise", rockLayerNoise);
		cyano.addProperty("rock_layer_thickness", layerThickness);
		cyano.addProperty("realistic_coal_layers", realisticCoal);
		cyano.addProperty("migrated_from", "mineralogy-" + identity.version);
		cyano.addProperty("legacy_lineage", lineage.label);
		cyano.addProperty("legacy_metadata_source", identity.sourceFile);
		cyano.addProperty("legacy_config_found", configFound);
		cyano.addProperty("hybrid_config", hybridConfig);
		cyano.add("igneous_rocks", array(igneous));
		cyano.add("metamorphic_rocks", array(metamorphic));
		cyano.add("sedimentary_rocks", array(sedimentary));
		root.add("cyano", cyano);

		writeUpgradeReport(worldRoot, configDirectory, identity, lineage,
				configFound, hybridConfig, enabled, geomeSize, rockLayerNoise,
				layerThickness, realisticCoal, igneous, metamorphic, sedimentary);

		LOGGER.info("Existing Mineralogy {} world detected from {}; pinned OreSpawn to {} "
				+ "Cyano behavior (enabled={}, geomeSize={}, layerNoise={}, layerThickness={}, "
				+ "realisticCoal={}, hybridConfig={}, configFound={})",
				identity.version, identity.sourceFile, lineage.label, enabled, geomeSize,
				rockLayerNoise, layerThickness, realisticCoal, hybridConfig, configFound);
		return WorldGeologyProfile.fromJson(root, installedPackProfile);
	}

	private static void writeUpgradeReport(Path worldRoot, Path configDirectory,
			MineralogyIdentity identity, Lineage lineage, boolean configFound,
			boolean hybridConfig, boolean enabled, int geomeSize,
			double rockLayerNoise, int layerThickness, boolean realisticCoal,
			List<String> igneous, List<String> metamorphic, List<String> sedimentary) {
		Path report = worldRoot.resolve("serverconfig/orespawn-upgrade-report.txt");
		List<String> missing = missingBlocks(igneous, metamorphic, sedimentary);
		List<String> lines = new ArrayList<>();
		lines.add("OreSpawn 4.0.6.112021 Upgrade Report");
		lines.add("================================");
		lines.add("");
		lines.add("RESULT: Existing Mineralogy " + identity.version + " world detected.");
		lines.add(enabled
				? "Geology remains on the Cyano engine using " + lineage.label + " behavior."
				: "Legacy Mineralogy geology was disabled and remains disabled for this world.");
		lines.add("This prevents an automatic worldgen change between old and newly generated chunks.");
		lines.add("");
		lines.add("Legacy world detection");
		lines.add("- Saved mod metadata: " + identity.sourceFile);
		lines.add("- Saved Mineralogy version: " + identity.version);
		lines.add("- Selected config lineage: " + lineage.label);
		lines.add("- Hybrid 1.10/1.12 keys found: " + hybridConfig);
		if (hybridConfig) {
			lines.add("- Hybrid precedence: saved Mineralogy version and the native 1.12 enable flag "
					+ "select behavior; 1.10 realistic coal is used only for the 1.10 lineage.");
		}
		lines.add("");
		lines.add("Legacy Mineralogy configuration");
		lines.add("- Source: " + configDirectory.resolve(MINERALOGY_CONFIG).toAbsolutePath());
		lines.add("- Source file found: " + (configFound
				? "yes" : "no; published " + lineage.label + " defaults used"));
		lines.add("- Geology enabled: " + enabled);
		lines.add("- Geome size: " + geomeSize);
		lines.add("- Rock layer noise: " + rockLayerNoise);
		lines.add("- Rock layer thickness: " + layerThickness);
		lines.add("- Realistic coal layers: " + realisticCoal
				+ (lineage == Lineage.MINERALOGY_112 ? " (not supported by Mineralogy 1.12)" : ""));
		lines.add("- Igneous rock order (" + igneous.size() + "): " + String.join(", ", igneous));
		lines.add("- Metamorphic rock order (" + metamorphic.size() + "): " + String.join(", ", metamorphic));
		lines.add("- Sedimentary rock order (" + sedimentary.size() + "): " + String.join(", ", sedimentary));
		lines.add("");
		Path os3Report = configDirectory.resolve("orespawn-os3-migration-report.json");
		lines.add("Legacy OreSpawn rules");
		lines.add("- Detailed OS3 rule report: " + os3Report.toAbsolutePath());
		lines.add("- Rule report found: " + (Files.isRegularFile(os3Report)
				? "yes" : "no (no legacy rule conversion was recorded here)"));
		lines.add("");
		if (missing.isEmpty()) {
			lines.add("WARNINGS: None. Every preserved Mineralogy rock ID is registered.");
		} else {
			lines.add("WARNINGS: These preserved rock IDs are not currently registered and need review:");
			for (String id : missing) lines.add("- " + id);
		}
		lines.add("");
		lines.add("OreSpawn did not rewrite the source Mineralogy configuration or existing chunks.");
		lines.add("The installed Mineralogy version may still normalize its own Forge configuration file.");
		lines.add("To change this world's geology later, make that choice explicitly and expect a generation seam.");
		writeTextAtomically(report, lines);
	}

	private static List<String> missingBlocks(List<String>... families) {
		List<String> missing = new ArrayList<>();
		for (List<String> family : families) {
			for (String idText : family) {
				try {
					ResourceLocation id = new ResourceLocation(idText);
					if (!ForgeRegistries.BLOCKS.containsKey(id)) missing.add(id.toString());
				} catch (RuntimeException e) {
					missing.add(idText + " (invalid registry name)");
				}
			}
		}
		return missing;
	}

	private static void writeTextAtomically(Path report, List<String> lines) {
		Path temporary = report.resolveSibling(report.getFileName().toString() + ".tmp");
		try {
			Files.createDirectories(report.getParent());
			byte[] data = (String.join(System.lineSeparator(), lines) + System.lineSeparator())
					.getBytes(StandardCharsets.UTF_8);
			if (Files.isRegularFile(report) && Arrays.equals(Files.readAllBytes(report), data)) return;
			Files.write(temporary, data);
			try {
				Files.move(temporary, report, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
			LOGGER.warn("Could not write legacy Mineralogy upgrade report '{}'", report, e);
		}
	}

	private static boolean hasGeneratedOverworldChunks(Path worldRoot) {
		Path regions = worldRoot.resolve("region");
		if (!Files.isDirectory(regions)) return false;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(regions, "r.*.*.mca")) {
			return files.iterator().hasNext();
		} catch (IOException e) {
			LOGGER.warn("Could not inspect existing world regions in '{}'", regions, e);
			return false;
		}
	}

	private static MineralogyIdentity legacyMineralogyIdentity(Path worldRoot) {
		for (String fileName : new String[] { "level.dat", "level.dat_old" }) {
			Path levelDat = worldRoot.resolve(fileName);
			if (!Files.isRegularFile(levelDat)) continue;
			try (FileInputStream input = new FileInputStream(levelDat.toFile())) {
				NBTTagCompound root = CompressedStreamTools.readCompressed(input);
				NBTTagCompound fml = root.getCompoundTag("FML");
				NBTTagList mods = fml.getTagList("ModList", 10);
				for (int i = 0; i < mods.tagCount(); i++) {
					NBTTagCompound mod = mods.getCompoundTagAt(i);
					if (!"mineralogy".equalsIgnoreCase(mod.getString("ModId"))) continue;
					String version = mod.getString("ModVersion").trim();
					if (isLegacyVersion(version)) {
						return new MineralogyIdentity(version.isEmpty() ? "legacy" : version, fileName);
					}
					return null;
				}
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("Could not inspect '{}' for legacy Mineralogy metadata", levelDat, e);
			}
		}
		return null;
	}

	private static boolean isLegacyVersion(String version) {
		if (version == null || version.trim().isEmpty()) return true;
		List<Integer> parts = versionParts(version);
		return !parts.isEmpty() && parts.get(0) <= 3;
	}

	private static Lineage lineage(String version, Map<String, String> values) {
		String lower = version == null ? "" : version.toLowerCase(Locale.ROOT);
		if (lower.contains("1.10")) return Lineage.MINERALOGY_110;
		if (lower.contains("1.12")) return Lineage.MINERALOGY_112;
		List<Integer> parts = versionParts(version);
		if (parts.size() >= 2 && parts.get(0) == 3) {
			if (parts.get(1) >= 8) return Lineage.MINERALOGY_112;
			if (parts.get(1) <= 3) return Lineage.MINERALOGY_110;
		}
		if (values.containsKey("place_mineralogy_rock")) return Lineage.MINERALOGY_112;
		if (values.containsKey("realistic_coal_layers")) return Lineage.MINERALOGY_110;
		return Lineage.MINERALOGY_112;
	}

	private static List<Integer> versionParts(String version) {
		List<Integer> result = new ArrayList<>();
		if (version == null) return result;
		for (String text : version.split("[^0-9]+")) {
			if (text.isEmpty()) continue;
			try { result.add(Integer.parseInt(text)); }
			catch (NumberFormatException ignored) { }
		}
		return result;
	}

	private static Map<String, String> readConfig(Path configPath) {
		Map<String, String> values = new LinkedHashMap<>();
		if (!Files.isRegularFile(configPath)) return values;
		try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.length() < 4 || trimmed.charAt(1) != ':') continue;
				char type = Character.toUpperCase(trimmed.charAt(0));
				if (type != 'B' && type != 'I' && type != 'D' && type != 'S') continue;
				int equals = trimmed.indexOf('=', 2);
				if (equals <= 2) continue;
				String key = trimmed.substring(2, equals).trim().toLowerCase(Locale.ROOT);
				values.put(key, trimmed.substring(equals + 1).trim());
			}
		} catch (IOException e) {
			LOGGER.warn("Could not read legacy Mineralogy configuration '{}'; using published defaults",
					configPath, e);
			values.clear();
		}
		return values;
	}

	private static List<String> legacyList(List<String> defaults, Map<String, String> values,
			String whitelistKey, String blacklistKey) {
		List<String> result = new ArrayList<>(defaults);
		for (String id : splitIds(values.get(whitelistKey))) result.add(id);
		for (String id : splitIds(values.get(blacklistKey))) result.remove(id);
		return result;
	}

	private static List<String> splitIds(String configured) {
		List<String> result = new ArrayList<>();
		if (configured == null) return result;
		for (String raw : configured.split(";")) {
			String value = raw.trim();
			if (value.isEmpty()) continue;
			try { result.add(new ResourceLocation(value).toString()); }
			catch (RuntimeException e) {
				LOGGER.warn("Ignoring invalid legacy Mineralogy rock registry name '{}'", value);
			}
		}
		return result;
	}

	private static int integer(Map<String, String> values, String key, int fallback, int min, int max) {
		try {
			int value = values.containsKey(key) ? Integer.parseInt(values.get(key)) : fallback;
			return Math.max(min, Math.min(max, value));
		} catch (RuntimeException e) { return fallback; }
	}

	private static double decimal(Map<String, String> values, String key,
			double fallback, double min, double max) {
		try {
			double value = values.containsKey(key) ? Double.parseDouble(values.get(key)) : fallback;
			return Math.max(min, Math.min(max, value));
		} catch (RuntimeException e) { return fallback; }
	}

	private static boolean bool(Map<String, String> values, String key, boolean fallback) {
		if (!values.containsKey(key)) return fallback;
		String value = values.get(key);
		return "true".equalsIgnoreCase(value) ? true
				: "false".equalsIgnoreCase(value) ? false : fallback;
	}

	private static JsonArray array(List<String> values) {
		JsonArray result = new JsonArray();
		for (String value : values) result.add(new JsonPrimitive(value));
		return result;
	}

	private enum Lineage {
		MINERALOGY_110("Mineralogy 1.10"),
		MINERALOGY_112("Mineralogy 1.12");

		final String label;
		Lineage(String label) { this.label = label; }
	}

	private static final class MineralogyIdentity {
		final String version;
		final String sourceFile;

		MineralogyIdentity(String version, String sourceFile) {
			this.version = version;
			this.sourceFile = sourceFile;
		}
	}
}
