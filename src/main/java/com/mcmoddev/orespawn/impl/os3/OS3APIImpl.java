package com.mcmoddev.orespawn.impl.os3;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
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

import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ResourceLocation;

public class OS3APIImpl implements OS3API {

	private static final Map<ResourceLocation, ISpawnEntry> spawns;
	private static final FeatureRegistry features;
	private static final ReplacementsRegistry replacements;
	private static final PresetsStorage presets;
	private static final String ORE_SPAWN_VERSION = "OreSpawn Version";
	private static final Map<String, Path> spawnsToSourceFiles = new TreeMap<>();

	static {
		spawns = new ConcurrentHashMap<>();
		features = new FeatureRegistry();
		replacements = new ReplacementsRegistry();
		presets = new PresetsStorage();
	}

	public OS3APIImpl() {
		//
	}

	public void loadConfigFiles() {
		final String failedReadingConfigsFrom = "Failed reading configs from ";
		PathMatcher featuresFiles = FileSystems.getDefault()
				.getPathMatcher("glob:**/features-*.json");
		PathMatcher replacementsFiles = FileSystems.getDefault()
				.getPathMatcher("glob:**/replacements-*.json");
		PathMatcher jsonMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.json");

		try (final Stream<Path> stream = Files.walk(Constants.SYSCONF, 1)) {
			stream.filter(featuresFiles::matches).map(Path::toFile)
					.forEach(features::loadFeaturesFile);
		} catch (final IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e,
					failedReadingConfigsFrom + Constants.SYSCONF.toString());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}

		// have to do this twice or we have issues
		try (final Stream<Path> stream = Files.walk(Constants.SYSCONF, 1)) {
			stream.filter(replacementsFiles::matches).forEach(replacements::loadFile);
		} catch (final IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e,
					failedReadingConfigsFrom + Constants.SYSCONF.toString());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}

		if (Constants.SYSCONF.resolve("presets-default.json").toFile().exists()) {
			presets.load(Constants.SYSCONF.resolve("presets-default.json"));
		}

		try (final Stream<Path> stream = Files.walk(Constants.CONFDIR, 1)) {
			stream.filter(jsonMatcher::matches).forEach(conf -> {
				try {
					OreSpawnReader.tryReadFile(conf);
				} catch (final MissingVersionException | NotAProperConfigException
						| OldVersionException | UnknownVersionException e) {
					CrashReport report = CrashReport.makeCrashReport(e,
							"Failed reading config " + conf.toString());
					report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
					OreSpawn.LOGGER.info(report.getCompleteReport());
				}
			});
		} catch (final IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e,
					failedReadingConfigsFrom + Constants.CONFDIR.toString());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}
	}

	@Override
	public void addSpawn(final ISpawnEntry spawnEntry) {
		if (spawnEntry != null) {
			spawns.put(new ResourceLocation(spawnEntry.getSpawnName()), spawnEntry);
		}
	}

	@Override
	public void addFeature(final String featureName, final IFeature feature) {
		features.addFeature(featureName, feature);
	}

	@Override
	public void addReplacement(final IReplacementEntry replacementEntry) {
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
		final Map<String, IReplacementEntry> temp = new HashMap<>();
		replacements.getReplacements().entrySet().stream()
				.forEach(e -> temp.put(e.getKey().getPath(), e.getValue()));
		return ImmutableMap.copyOf(temp);
	}

	@Override
	public IReplacementEntry getReplacement(final String replacementName) {
		return replacements.getReplacement(replacementName);
	}

	@Override
	public List<ISpawnEntry> getSpawns(final int dimensionID) {
		return ImmutableList.copyOf(
				spawns.entrySet().stream().filter(e -> e.getValue().dimensionAllowed(dimensionID))
						.map(Map.Entry::getValue).collect(Collectors.toList()));
	}

	@Override
	public ISpawnEntry getSpawn(final String spawnName) {
		return spawns.get(new ResourceLocation(spawnName));
	}

	@Override
	public Map<String, ISpawnEntry> getAllSpawns() {
		final Map<String, ISpawnEntry> sp = new HashMap<>();
		spawns.entrySet().forEach(ent -> sp.put(ent.getKey().getPath(), ent.getValue()));
		return ImmutableMap.copyOf(sp);
	}

	@Override
	public boolean featureExists(final String featureName) {
		return this.featureExists(new ResourceLocation(featureName.contains(":") ? featureName
				: String.format("orespawn:%s", featureName)));
	}

	@Override
	public boolean featureExists(final ResourceLocation featureName) {
		return features.hasFeature(featureName);
	}

	@Override
	public IFeature getFeature(final String featureName) {
		return this.getFeature(new ResourceLocation(featureName));
	}

	@Override
	public IFeature getFeature(final ResourceLocation featureName) {
		return features.getFeature(featureName);
	}

	@Override
	public PresetsStorage copyPresets() {
		final PresetsStorage copy = new PresetsStorage();
		presets.copy(copy);
		return copy;
	}

	@Override
	public List<IBlockState> getDimensionDefaultReplacements(final int dimensionID) {
		return replacements.getDimensionDefault(dimensionID);
	}

	@Override
	public boolean hasReplacement(final ResourceLocation resourceLocation) {
		return replacements.has(resourceLocation);
	}

	@Override
	public boolean hasReplacement(final String name) {
		return this.hasReplacement(new ResourceLocation(
				name.contains(":") ? name : String.format("orespawn:%s", name)));
	}

	@Override
	public void mapEntryToFile(final Path p, final String entryName) {
		spawnsToSourceFiles.put(entryName, p);
	}

	@Override
	public List<String> getSpawnsForFile(final String fileName) {
		final Path p = Constants.CONFDIR.resolve(fileName);
		final List<String> values = spawnsToSourceFiles.entrySet().stream()
				.filter(ent -> ent.getValue().equals(p)).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		return ImmutableList.copyOf(values);
	}

	@Override
	public Map<Path, List<String>> getSpawnsByFile() {
		final Map<Path, List<String>> temp = new HashMap<>();
		spawnsToSourceFiles.entrySet().stream().forEach(ent -> {
			if (temp.containsKey(ent.getValue())) {
				temp.get(ent.getValue()).add(ent.getKey());
			} else {
				temp.put(ent.getValue(), Lists.newLinkedList(Arrays.asList(ent.getKey())));
			}
		});

		return ImmutableMap.copyOf(temp);
	}
}
