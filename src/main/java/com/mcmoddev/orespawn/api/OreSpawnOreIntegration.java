package com.mcmoddev.orespawn.api;

import java.util.Set;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.integration.WorldgenIntegrationManager;

/**
 * Compatibility facade for the initial ore-provider status API.
 *
 * @deprecated Use {@link OreSpawnApi}. This class remains available so
 * existing provider mods do not need an immediate source change.
 */
@Deprecated
public final class OreSpawnOreIntegration {
	/** @deprecated Use {@link ProviderStatus}. */
	@Deprecated
	public enum ProviderStatus {
		PENDING,
		ACTIVE,
		INACTIVE
	}

	private OreSpawnOreIntegration() {
	}

	public static void initialize() {
		WorldgenIntegrationManager.initialize();
	}

	public static ProviderStatus getProviderStatus(String providerModId) {
		return ProviderStatus.valueOf(OreSpawnApi.getProviderStatus(providerModId).name());
	}

	public static boolean isProviderActive(String providerModId) {
		return OreSpawnApi.isOreTakeoverActive(providerModId);
	}

	public static void markFeatureReady() {
		WorldgenIntegrationManager.markFeatureReady();
	}

	/** Merge all provider contributions while retaining the historical method name. */
	public static boolean mergeProviderOres(JsonObject target) {
		return WorldgenIntegrationManager.mergeProviderDefinitions(target);
	}

	public static Set<String> activeProviderIds() {
		return WorldgenIntegrationManager.activeProviderIds();
	}
}
