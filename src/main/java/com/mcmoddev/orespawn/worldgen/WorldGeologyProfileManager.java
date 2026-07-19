package com.mcmoddev.orespawn.worldgen;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mcmoddev.orespawn.OreSpawnConfig;
import com.mcmoddev.orespawn.OreSpawnConfig.GeologyMode;
import com.mcmoddev.orespawn.api.OreSpawnOreIntegration;
import com.mcmoddev.orespawn.integration.WorldgenIntegrationManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class WorldGeologyProfileManager {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String PROFILE_FILE_NAME = "orespawn-worldgen.json";

	private static volatile WorldGeologyProfile pendingNewWorldProfile;
	private static volatile Object pendingNewWorldSession;
	private static volatile WorldGeologyProfile activeProfile;
	private static volatile MinecraftServer activeServer;

	private WorldGeologyProfileManager() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	public static synchronized WorldGeologyProfile beginNewWorldCreation(Object session) {
		if (pendingNewWorldSession != session) {
			pendingNewWorldProfile = globalProfile().copy();
			pendingNewWorldSession = session;
		}
		return pendingNewWorldProfile;
	}

	public static synchronized void setPendingNewWorldProfile(WorldGeologyProfile profile) {
		pendingNewWorldProfile = profile;
	}

	public static synchronized void clearPendingNewWorldProfile() {
		pendingNewWorldProfile = null;
		pendingNewWorldSession = null;
	}

	public static synchronized WorldGeologyProfile pendingNewWorldProfile() {
		WorldGeologyProfile pending = pendingNewWorldProfile;
		if (pending == null) {
			pending = globalProfile().copy();
			pendingNewWorldProfile = pending;
		}
		return pending;
	}

	public static GeologyMode geologyMode() {
		WorldGeologyProfile profile = activeProfile;
		return profile == null ? globalProfile().geologyMode() : profile.geologyMode();
	}

	public static boolean placeCrudeOil() {
		WorldGeologyProfile profile = activeProfile;
		return profile == null ? globalProfile().placeCrudeOil() : profile.placeCrudeOil();
	}

	public static WorldGeologyProfile activeProfile() {
		WorldGeologyProfile profile = activeProfile;
		return profile == null ? globalProfile() : profile;
	}

	public static MinecraftServer activeServer() {
		return activeServer;
	}

	public static synchronized boolean reloadActiveProfile() {
		MinecraftServer server = activeServer;
		if (server == null) return false;
		WorldgenIntegrationManager.initialize();
		WorldgenIntegrationManager.freeze();
		WorldgenIntegrationManager.markFeatureReady();
		GeomeConfig.bake();
		Path profilePath = server.getWorldPath(LevelResource.ROOT).normalize()
				.resolve("serverconfig").resolve(PROFILE_FILE_NAME);
		WorldGeologyProfile profile = readProfile(profilePath, globalProfile());
		JsonObject merged = profile.rootCopy();
		if (OreSpawnOreIntegration.mergeProviderOres(merged)) {
			profile = profile.withRoot(merged);
			writeProfile(profilePath, profile);
		}
		activateProfile(profile);
		return true;
	}

	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		activeServer = event.getServer();
		Path worldRoot = event.getServer().getWorldPath(LevelResource.ROOT).normalize();
		Path profilePath = worldRoot.resolve("serverconfig").resolve(PROFILE_FILE_NAME);
		WorldGeologyProfile fallback = globalProfile();
		WorldGeologyProfile pending = null;
		WorldGeologyProfile profile;

		if (Files.exists(profilePath)) {
			clearPendingNewWorldProfile();
			profile = readProfile(profilePath, fallback);
			JsonObject merged = profile.rootCopy();
			String beforeMerge = merged.toString();
			OreSpawnOreIntegration.mergeProviderOres(merged);
			if (!beforeMerge.equals(merged.toString())) {
				profile = profile.withRoot(merged);
				writeProfile(profilePath, profile);
				LOGGER.info("Merged new OreSpawn worldgen-provider definitions into '{}'", profilePath);
			}
		} else {
			pending = consumePendingProfile();
			boolean generatedWorld = hasGeneratedOverworldChunks(worldRoot);
			String source;
			if (pending != null) {
				profile = pending;
				source = "Create World";
			} else if (generatedWorld) {
				profile = fallback;
				source = "instance (existing world)";
			} else {
				profile = fallback.copy();
				source = "installed-pack fresh-world";
			}
			writeProfile(profilePath, profile);
			LOGGER.info("Created OreSpawn world geology profile '{}' from {} settings",
					profilePath, source);
		}

		activateProfile(profile);
		LOGGER.info("Activated OreSpawn world geology profile: mode={}, formations={}, horizontal={}, thickness={}, waviness={}, edge={}, continuity={}, oil={}",
				profile.geologyMode(), profile.algorithm().configName(), profile.horizontalSize().configName(),
				profile.verticalThickness().configName(), profile.waviness().configName(),
				profile.edgeIrregularity().configName(), profile.formationContinuity().configName(),
				profile.placeCrudeOil());
	}

	public static void onServerStopped(ServerStoppedEvent event) {
		activeServer = null;
		activeProfile = null;
		GeomeConfig.applyWorldProfile(globalProfile());
		StoneReplacer.refreshWorldConfig();
		OreSpawnOreGeneration.refreshWorldConfig();
		OilDepositFeature.refreshWorldConfig();
		FlatBedrockFeature.refreshWorldConfig();
		OreRetrogenManager.clear();
	}

	static void applyBenchmarkProfile(WorldGeologyProfile profile) {
		activateProfile(profile);
	}

	private static void activateProfile(WorldGeologyProfile profile) {
		activeProfile = profile;
		GeomeConfig.applyWorldProfile(profile);
		StoneReplacer.refreshWorldConfig();
		OreSpawnOreGeneration.refreshWorldConfig();
		OilDepositFeature.refreshWorldConfig();
		FlatBedrockFeature.refreshWorldConfig();
		OreRetrogenManager.refreshWorldConfig();
	}

	private static WorldGeologyProfile globalProfile() {
		WorldGeologyProfile profile = GeomeConfig.globalProfile();
		return profile == null
				? WorldGeologyProfile.recommended(OreSpawnConfig.placeCrudeOil())
				: profile;
	}

	private static synchronized WorldGeologyProfile consumePendingProfile() {
		WorldGeologyProfile profile = pendingNewWorldProfile;
		pendingNewWorldProfile = null;
		pendingNewWorldSession = null;
		return profile;
	}

	private static boolean hasGeneratedOverworldChunks(Path worldRoot) {
		Path regionDirectory = worldRoot.resolve("region");
		if (!Files.isDirectory(regionDirectory)) {
			return false;
		}

		try (DirectoryStream<Path> regions = Files.newDirectoryStream(regionDirectory, "r.*.*.mca")) {
			return regions.iterator().hasNext();
		} catch (IOException e) {
			LOGGER.warn("Could not inspect existing chunks in '{}'; preserving instance geology settings",
					regionDirectory, e);
			return true;
		}
	}

	private static WorldGeologyProfile readProfile(Path path, WorldGeologyProfile fallback) {
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement element = new JsonParser().parse(reader);
			if (!element.isJsonObject()) {
				LOGGER.warn("OreSpawn world geology profile '{}' is not a JSON object; using instance settings", path);
				return fallback;
			}
			JsonObject root = element.getAsJsonObject();
			int schema = root.has("schema_version") ? root.get("schema_version").getAsInt() : 1;
			if (schema > WorldGeologyProfile.SCHEMA_VERSION) {
				LOGGER.warn("OreSpawn world geology profile '{}' uses newer schema {}; reading known fields only",
						path, schema);
			}
			WorldGeologyProfile profile = WorldGeologyProfile.fromJson(root, fallback);
			if (schema < WorldGeologyProfile.SCHEMA_VERSION) {
				writeProfile(path, profile);
				LOGGER.info("Migrated OreSpawn world geology profile '{}' to schema {}",
						path, WorldGeologyProfile.SCHEMA_VERSION);
			}
			return profile;
		} catch (IOException | JsonSyntaxException | IllegalStateException e) {
			LOGGER.warn("Could not read OreSpawn world geology profile '{}'; using instance settings", path, e);
			return fallback;
		}
	}

	private static void writeProfile(Path path, WorldGeologyProfile profile) {
		Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				GSON.toJson(profile.toJson(), writer);
			}
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException ignored) {
				// The write failure is the useful diagnostic.
			}
			LOGGER.warn("Could not persist OreSpawn world geology profile '{}'", path, e);
		}
	}
}
