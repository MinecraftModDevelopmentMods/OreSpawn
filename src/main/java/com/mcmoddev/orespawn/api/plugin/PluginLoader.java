package com.mcmoddev.orespawn.api.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.mcmoddev.orespawn.api.plugin.IOreSpawnPlugin;
import com.mcmoddev.orespawn.OreSpawn;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public enum PluginLoader {

	INSTANCE;
	
	private Map<IOreSpawnPlugin, List<String>> dataStore = new HashMap<>();
	
	private String getAnnotationItem(String item, final ASMData asmData) {
		if (asmData.getAnnotationInfo().get(item) != null) {
			return asmData.getAnnotationInfo().get(item).toString();
		} else {
			return "";
		}
	}

	public void load(FMLPreInitializationEvent event) {
		for (final ASMData asmDataItem : event.getAsmData().getAll(OreSpawnPlugin.class.getCanonicalName())) {
			final String modId = getAnnotationItem("modid", asmDataItem);
			final String resourceBase = getAnnotationItem("resourcePath", asmDataItem);
			final String clazz = asmDataItem.getClassName();

			if ( event.getModMetadata().modId.equals(modId) ) {
				IOreSpawnPlugin integration;
				try {
					integration = Class.forName(clazz).asSubclass(IOreSpawnPlugin.class).newInstance();
					List<String> temp = new ArrayList<>();
					temp.add(modId);
					temp.add(resourceBase);
					dataStore.put(integration, temp);
				} catch (final Exception ex) {
					OreSpawn.LOGGER.error("Couldn't load integrations for " + modId, ex);
				}
			}
		}
	}
	
	public void register() {
		for( Entry<IOreSpawnPlugin,List<String>> ent : dataStore.entrySet() ) {
			scanResources(new ResourceLocation(ent.getValue().get(0),ent.getValue().get(1)));
			ent.getKey().register(OreSpawn.API);
		}
	}

	public void scanResources(ResourceLocation loc) {
		// STUB!
		// TODO: scan through the location pointed at by loc for json's
		return;
	}
}
