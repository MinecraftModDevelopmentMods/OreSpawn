package com.mcmoddev.orespawn;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.compat.LegacyOs3Bridge;
import com.mcmoddev.orespawn.data.FeatureRegistry;
import com.mcmoddev.orespawn.json.OS3Writer;
import net.minecraftforge.fml.common.event.FMLFingerprintViolationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/** Deprecated OS3 facade. OreSpawn 4 owns the actual mod lifecycle. */
@Deprecated
public class OreSpawn {
	public static OreSpawn instance = new OreSpawn();
	public static final Logger LOGGER = LogManager.getLogger("OreSpawn-OS3-Bridge");
	public static final OS3API API = LegacyOs3Bridge.api();
	public static final OS3Writer writer = new OS3Writer();
	public static final FeatureRegistry FEATURES = LegacyOs3Bridge.features();
	@SuppressWarnings("rawtypes")
	protected static final Map<Integer, List> spawns = Collections.emptyMap();
	public OreSpawn() { }
	@SuppressWarnings("rawtypes")
	public static Map<Integer, List> getSpawns() { return spawns; }
	public void onFingerprintViolation(FMLFingerprintViolationEvent event) { }
	public void preInit(FMLPreInitializationEvent event) { LegacyOs3Bridge.initialize(event); }
	public void init(FMLInitializationEvent event) { }
	public void postInit(FMLPostInitializationEvent event) { }
	public void onServerStarting(FMLServerStartingEvent event) { }
}
