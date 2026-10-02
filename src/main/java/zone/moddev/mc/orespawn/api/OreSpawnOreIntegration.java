package zone.moddev.mc.orespawn.api;

import java.util.Set;

import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;

/**
 * Compatibility facade for the initial ore-provider status API.
 *
 * @deprecated Use {@link OreSpawnApi}. Kept for existing provider mods.
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

	/** Merges provider definitions. Kept for mods using the original integration API. */
	public static boolean mergeProviderOres(JsonObject target) {
		return WorldgenIntegrationManager.mergeProviderDefinitions(target);
	}

	public static Set<String> activeProviderIds() {
		return WorldgenIntegrationManager.activeProviderIds();
	}
}
