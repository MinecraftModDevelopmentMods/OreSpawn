package com.mcmoddev.orespawn.api.plugin;

import com.mcmoddev.orespawn.compat.LegacyOs3Bridge;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Deprecated OS3 loader facade. OreSpawn 4 owns discovery and scheduling, so
 * the historical two-step entry points safely converge on the same idempotent
 * bridge initialization.
 */
@Deprecated
public enum PluginLoader {
	INSTANCE;

	public final class PluginData {
		public final String modId;
		public final String resourcePath;
		public final IOreSpawnPlugin plugin;

		public PluginData(String modId, String resourcePath, IOreSpawnPlugin plugin) {
			this.modId = modId;
			this.resourcePath = resourcePath;
			this.plugin = plugin;
		}
	}

	public void load(FMLPreInitializationEvent event) {
		LegacyOs3Bridge.initialize(event);
	}

	public void register() {
		// Discovery, resource translation and registration are one atomic bridge step.
	}

	public void scanResources(PluginData data) {
		// Resources are scanned by LegacyOs3Bridge before profile baking.
	}
}
