package zone.moddev.mc.orespawn.api;

import java.util.Optional;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;
import zone.moddev.mc.orespawn.worldgen.GeomeConfig;
import zone.moddev.mc.orespawn.worldgen.WorldIds;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/** Entry point for OreSpawn API version 1. */
public final class OreSpawnApi {
	public static final int API_VERSION = 1;
	public static final String IMC_WORLDGEN_PROVIDER = "worldgen_provider_v1";

	private OreSpawnApi() {
	}

	/**
	 * Enqueues a provider before OreSpawn freezes discovery. Call this during
	 * the provider mod's Forge initialization phase.
	 */
	public static boolean enqueue(WorldgenProvider provider) {
		if (provider == null) {
			throw new IllegalArgumentException("provider cannot be null");
		}
		return WorldgenIntegrationManager.submitApiProvider(provider);
	}

	public static ProviderStatus getProviderStatus(String providerModId) {
		return WorldgenIntegrationManager.getProviderStatus(providerModId);
	}

	public static boolean isOreTakeoverActive(String providerModId) {
		return WorldgenIntegrationManager.isOreTakeoverActive(providerModId);
	}

	public static Optional<GeologyProfileView> getActiveProfile(MinecraftServer server) {
		if (server == null || WorldGeologyProfileManager.activeServer() != server) {
			return Optional.empty();
		}
		return Optional.of(new GeologyProfileView(WorldGeologyProfileManager.activeProfile().toJson()));
	}

	public static Optional<GeologySampler> createSampler(WorldServer level) {
		if (level == null || WorldGeologyProfileManager.activeServer() != level.getMinecraftServer()
				|| GeomeConfig.baked(WorldIds.dimension(level)) == null) {
			return Optional.empty();
		}
		return Optional.of(OreSpawnGeologySampler.create(level));
	}
}
