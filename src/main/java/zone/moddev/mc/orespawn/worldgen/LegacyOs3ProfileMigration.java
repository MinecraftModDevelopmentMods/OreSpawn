package zone.moddev.mc.orespawn.worldgen;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Atomic, one-time mapping of the OS3 general switches into schema 6. */
public final class LegacyOs3ProfileMigration {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private LegacyOs3ProfileMigration() { }

	public static Result apply(Path configDirectory, boolean manageVanilla, boolean suppressAll,
			boolean retrogen, boolean forceRetrogen, boolean flatBedrock,
			boolean retrogenBedrock, int bedrockLayers) throws IOException {
		Path destination = configDirectory.resolve("orespawn-worldgen.json");
		Path marker = configDirectory.resolve(".orespawn-os3-profile-migrated");
		if (Files.isRegularFile(marker)) return Result.ALREADY_MIGRATED;

		JsonObject root = readExisting(destination);
		if (root == null) root = GeomeConfig.legacyMigrationDefaults();
		root.addProperty("manage_vanilla_ores", manageVanilla);
		root.addProperty("suppress_all_ore_features", suppressAll);

		JsonObject retro = object(root, "retrogen");
		retro.addProperty("enabled", retrogen);
		retro.addProperty("force", forceRetrogen);
		retro.addProperty("revision", retrogen ? 1 : 0);
		if (!retro.has("chunks_per_tick")) retro.addProperty("chunks_per_tick", 1);
		root.add("retrogen", retro);

		JsonObject bedrock = object(root, "flat_bedrock");
		bedrock.addProperty("enabled", flatBedrock);
		bedrock.addProperty("retrogen", retrogenBedrock);
		bedrock.addProperty("layers", Math.max(1, Math.min(4, bedrockLayers)));
		root.add("flat_bedrock", bedrock);

		byte[] bytes = (GSON.toJson(root) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
		Files.createDirectories(configDirectory);
		if (Files.isRegularFile(destination) && !Arrays.equals(Files.readAllBytes(destination), bytes)) {
			Path backup = destination.resolveSibling("orespawn-worldgen.pre-os3-migration.json");
			if (!Files.exists(backup)) Files.copy(destination, backup);
		}
		atomicWrite(destination, bytes);
		writeInitialUpgradeReport(configDirectory, manageVanilla, suppressAll,
				retrogen, forceRetrogen, flatBedrock, retrogenBedrock,
				Math.max(1, Math.min(4, bedrockLayers)));
		atomicWrite(marker, ("profile_schema=6" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
		return Result.WRITTEN;
	}

	private static void writeInitialUpgradeReport(Path configDirectory,
			boolean manageVanilla, boolean suppressAll, boolean retrogen,
			boolean forceRetrogen, boolean flatBedrock, boolean retrogenBedrock,
			int bedrockLayers) throws IOException {
		String newline = System.lineSeparator();
		String text = "OreSpawn 4.0.6 Upgrade Report" + newline
				+ "================================" + newline + newline
				+ "RESULT: Legacy OreSpawn settings were imported into the OS4 profile." + newline
				+ "- Manage vanilla ores: " + manageVanilla + newline
				+ "- Suppress all ore features: " + suppressAll + newline
				+ "- Retrogen enabled: " + retrogen + newline
				+ "- Force retrogen: " + forceRetrogen + newline
				+ "- Flat bedrock enabled: " + flatBedrock + newline
				+ "- Flat bedrock retrogen: " + retrogenBedrock + newline
				+ "- Flat bedrock layers: " + bedrockLayers + newline + newline
				+ "The detailed OS3 rule translation is recorded in "
				+ "orespawn-os3-migration-report.json." + newline
				+ "Original legacy configuration files are retained unchanged." + newline;
		atomicWrite(configDirectory.resolve("orespawn-upgrade-report.txt"),
				text.getBytes(StandardCharsets.UTF_8));
	}

	private static JsonObject readExisting(Path path) throws IOException {
		if (!Files.isRegularFile(path)) return null;
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement value = new JsonParser().parse(reader);
			return value.isJsonObject() ? value.getAsJsonObject() : null;
		}
	}

	private static JsonObject object(JsonObject root, String key) {
		return root.has(key) && root.get(key).isJsonObject()
				? root.getAsJsonObject(key) : new JsonObject();
	}

	private static void atomicWrite(Path destination, byte[] bytes) throws IOException {
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
		Files.write(temporary, bytes);
		try {
			Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException failure) {
			Files.deleteIfExists(temporary);
			throw failure;
		}
	}

	public enum Result { WRITTEN, ALREADY_MIGRATED }
}
