package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.api.ProviderStatus;
import zone.moddev.mc.orespawn.api.client.WorldSettingsExtension;
import zone.moddev.mc.orespawn.api.client.WorldSettingsExtensionRegistry;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager.ProviderIntegrationInfo;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

/** Builds the immutable, loaded-only model rendered by the OreSpawn Mods screen. */
final class OreSpawnModDirectoryModel {
	private OreSpawnModDirectoryModel() {
	}

	static List<Entry> snapshot() {
		List<LoadedMod> loaded = new ArrayList<>();
		for (ModContainer mod : Loader.instance().getActiveModList()) {
			loaded.add(new LoadedMod(mod.getModId(), mod.getName(), mod.getVersion()));
		}
		return assemble(loaded, WorldgenIntegrationManager.providerIntegrations(),
				WorldSettingsExtensionRegistry.extensions());
	}

	static List<Entry> assemble(List<LoadedMod> loadedMods,
			List<ProviderIntegrationInfo> providers, List<WorldSettingsExtension> extensions) {
		Map<String, LoadedMod> loaded = new LinkedHashMap<>();
		for (LoadedMod mod : loadedMods) {
			if (mod != null && mod.modId != null) loaded.put(mod.modId, mod);
		}
		Map<String, MutableEntry> integrations = new LinkedHashMap<>();
		for (ProviderIntegrationInfo provider : providers) {
			if (isVisible(provider.modId(), loaded)) {
				integrations.computeIfAbsent(provider.modId(), ignored -> new MutableEntry())
						.provider = provider;
			}
		}
		for (WorldSettingsExtension extension : extensions) {
			String modId = extension.id().getResourceDomain();
			if (isVisible(modId, loaded)) {
				integrations.computeIfAbsent(modId, ignored -> new MutableEntry())
						.extension = extension;
			}
		}

		List<Entry> result = new ArrayList<>();
		for (Map.Entry<String, MutableEntry> integration : integrations.entrySet()) {
			LoadedMod mod = loaded.get(integration.getKey());
			result.add(new Entry(mod.modId, nonBlank(mod.displayName, mod.modId),
					nonBlank(mod.version, "?"), integration.getValue().provider,
					integration.getValue().extension));
		}
		result.sort(Comparator.comparing((Entry entry) -> entry.displayName.toLowerCase(Locale.ROOT))
				.thenComparing(entry -> entry.modId));
		return Collections.unmodifiableList(result);
	}

	private static boolean isVisible(String modId, Map<String, LoadedMod> loaded) {
		return modId != null && !OreSpawn.MODID.equals(modId) && loaded.containsKey(modId);
	}

	private static String nonBlank(String value, String fallback) {
		return value == null || value.trim().isEmpty() ? fallback : value;
	}

	static final class LoadedMod {
		final String modId;
		final String displayName;
		final String version;

		LoadedMod(String modId, String displayName, String version) {
			this.modId = modId;
			this.displayName = displayName;
			this.version = version;
		}
	}

	static final class Entry {
		final String modId;
		final String displayName;
		final String version;
		final ProviderIntegrationInfo provider;
		final WorldSettingsExtension extension;

		Entry(String modId, String displayName, String version,
				ProviderIntegrationInfo provider, WorldSettingsExtension extension) {
			this.modId = modId;
			this.displayName = displayName;
			this.version = version;
			this.provider = provider;
			this.extension = extension;
		}

		boolean configurable() { return extension != null; }
		boolean nativeOs4() { return provider != null && provider.nativeOs4(); }
		int schemaVersion() { return provider == null ? -1 : provider.schemaVersion(); }
		int providerRevision() { return provider == null ? -1 : provider.providerRevision(); }
		List<Integer> legacyLineages() {
			return provider == null ? Collections.emptyList() : provider.legacyLineages();
		}
		String statusKey() {
			if (provider == null) return "config_only";
			if (provider.rejected()) return "rejected";
			ProviderStatus status = provider.status();
			return status == null ? "inactive" : status.name().toLowerCase(Locale.ROOT);
		}
	}

	private static final class MutableEntry {
		ProviderIntegrationInfo provider;
		WorldSettingsExtension extension;
	}
}
