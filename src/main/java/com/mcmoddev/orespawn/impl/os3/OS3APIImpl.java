package com.mcmoddev.orespawn.impl.os3;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.exceptions.MissingVersionException;
import com.mcmoddev.orespawn.api.exceptions.NotAProperConfigException;
import com.mcmoddev.orespawn.api.exceptions.OldVersionException;
import com.mcmoddev.orespawn.api.exceptions.UnknownVersionException;
import com.mcmoddev.orespawn.api.os3.IBiomeBuilder;
import com.mcmoddev.orespawn.api.os3.IBlockBuilder;
import com.mcmoddev.orespawn.api.os3.IDimensionBuilder;
import com.mcmoddev.orespawn.api.os3.IFeatureBuilder;
import com.mcmoddev.orespawn.api.os3.IReplacementBuilder;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.api.os3.ISpawnBuilder;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.FeatureRegistry;
import com.mcmoddev.orespawn.data.PresetsStorage;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.json.OreSpawnReader;

import net.minecraft.crash.CrashReport;
import net.minecraft.util.ResourceLocation;
import net.minecraft.block.state.IBlockState;

public class OS3APIImpl implements OS3API {
	private static final Map<ResourceLocation,ISpawnEntry> spawns;
	private static final FeatureRegistry features;
	private static final ReplacementsRegistry replacements;
	private static final PresetsStorage presets;
	private static final String ORE_SPAWN_VERSION = "OreSpawn Version";
	
	static {
		spawns = new ConcurrentHashMap<>();
		features = new FeatureRegistry();
		replacements = new ReplacementsRegistry();
		presets = new PresetsStorage();
	}
	
	public OS3APIImpl() {
		PathMatcher featuresFiles = FileSystems.getDefault().getPathMatcher("glob:features-*.json");
		PathMatcher replacementsFiles = FileSystems.getDefault().getPathMatcher("glob:replacements-*.json");
		PathMatcher jsonMatcher = FileSystems.getDefault().getPathMatcher("glob:*.json");
		
		try (Stream<Path> stream = Files.walk(Constants.SYSCONF, 1)) {
			stream.filter(featuresFiles::matches)
			.map(Path::toFile)
			.forEach(features::loadFeaturesFile);
		}  catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed reading configs from " + Constants.SYSCONF.toString());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}
		
		// have to do this twice or we have issues	
		try (Stream<Path> stream = Files.walk(Constants.SYSCONF, 1)) {
			stream.filter(replacementsFiles::matches)
			.forEach(replacements::loadFile);
		}  catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed reading configs from " + Constants.SYSCONF.toString());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}

		if(Constants.SYSCONF.resolve("presets-default.json").toFile().exists()) {
			presets.load(Constants.SYSCONF.resolve("presets-default.json"));
		}
		
		try(Stream<Path> stream = Files.walk(Constants.CONFDIR, 1)) {
			stream.filter(jsonMatcher::matches)
			.forEach(conf -> {
				try {
					OreSpawnReader.tryReadFile(conf, this);
				} catch (MissingVersionException | NotAProperConfigException | OldVersionException
						| UnknownVersionException e) {
					CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + conf.toString());
					report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
					OreSpawn.LOGGER.info(report.getCompleteReport());
				}
			});
		} catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed reading configs from " + Constants.CONFDIR.toString());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}
	}
	
	@Override
	public void addSpawn(ISpawnEntry spawnEntry) {
		spawns.put(new ResourceLocation(spawnEntry.getSpawnName()), spawnEntry);
	}

	@Override
	public void addFeature(String featureName, IFeature feature) {
		features.addFeature(featureName, feature);
	}

	@Override
	public void addReplacement(IReplacementEntry replacementEntry) {
		replacements.addReplacement(replacementEntry);
	}

	@Override
	public ISpawnBuilder getSpawnBuilder() {
		return new SpawnBuilder();
	}

	@Override
	public IDimensionBuilder getDimensionBuilder() {
		return new DimensionBuilder();
	}

	@Override
	public IFeatureBuilder getFeatureBuilder() {
		return new FeatureBuilder();
	}

	@Override
	public IBlockBuilder getBlockBuilder() {
		return new BlockBuilder();
	}

	@Override
	public IBiomeBuilder getBiomeBuilder() {
		return new BiomeBuilder();
	}

	@Override
	public IReplacementBuilder getReplacementBuilder() {
		return new ReplacementBuilder();
	}
	
	@Override
	public Map<String, IReplacementEntry> getReplacements() {
		Map<String, IReplacementEntry> temp = new HashMap<>();
		replacements.getReplacements().entrySet().stream()
		.forEach(e -> temp.put(e.getKey().getResourcePath(), e.getValue()));
		return ImmutableMap.copyOf(temp);
	}

	@Override
	public IReplacementEntry getReplacement(String replacementName) {
		return replacements.getReplacement(replacementName);
	}

	@Override
	public List<ISpawnEntry> getSpawns(int dimensionID) {
		return ImmutableList.copyOf(spawns.entrySet().stream()
		.filter(e -> e.getValue().dimensionAllowed(dimensionID))
		.map(e -> e.getValue())
		.collect(Collectors.toList()));
	}

	@Override
	public ISpawnEntry getSpawn(String spawnName) {
		return spawns.get(new ResourceLocation(spawnName));
	}

	@Override
	public Map<String, ISpawnEntry> getAllSpawns() {
		Map<String, ISpawnEntry> sp = new HashMap<>();
		spawns.entrySet().forEach(ent -> sp.put(ent.getKey().getResourcePath(), ent.getValue()));
		return ImmutableMap.copyOf(sp);
	}

	@Override
	public boolean featureExists(String featureName) {
		return this.featureExists(new ResourceLocation(featureName));
	}
	
	@Override
	public boolean featureExists(ResourceLocation featureName) {
		return features.hasFeature(featureName);
	}

	@Override
	public IFeature getFeature(String featureName) {
		return this.getFeature(new ResourceLocation(featureName));
	}

	@Override
	public IFeature getFeature(ResourceLocation featureName) {
		return features.getFeature(featureName);
	}

	@Override
	public PresetsStorage copyPresets() {
		PresetsStorage copy = new PresetsStorage();
		presets.copy(copy);
		return copy;
	}

	@Override
	public List<IBlockState> getDimensionDefaultReplacements(int dimensionID) {
		return replacements.getDimensionDefault(dimensionID);
	}

}
