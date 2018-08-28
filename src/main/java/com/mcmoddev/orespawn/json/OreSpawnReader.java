package com.mcmoddev.orespawn.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.exceptions.BadValueException;
import com.mcmoddev.orespawn.api.exceptions.MissingVersionException;
import com.mcmoddev.orespawn.api.exceptions.NotAProperConfigException;
import com.mcmoddev.orespawn.api.exceptions.OldVersionException;
import com.mcmoddev.orespawn.api.exceptions.UnknownFieldException;
import com.mcmoddev.orespawn.api.exceptions.UnknownNameException;
import com.mcmoddev.orespawn.api.exceptions.UnknownVersionException;
import com.mcmoddev.orespawn.api.os3.IBiomeBuilder;
import com.mcmoddev.orespawn.api.os3.IBlockBuilder;
import com.mcmoddev.orespawn.api.os3.IDimensionBuilder;
import com.mcmoddev.orespawn.api.os3.IFeatureBuilder;
import com.mcmoddev.orespawn.api.os3.IReplacementBuilder;
import com.mcmoddev.orespawn.api.os3.ISpawnBuilder;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.PresetsStorage;
import com.mcmoddev.orespawn.impl.os3.OS3APIImpl;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

public class OreSpawnReader {

	private static final String ORE_SPAWN_VERSION = "OreSpawn Version";

	public static void tryReadFile(final Path conf, final OS3APIImpl os3apiImpl)
			throws MissingVersionException, NotAProperConfigException, OldVersionException,
			UnknownVersionException {
		final JsonParser parser = new JsonParser();

		try (BufferedReader data = Files.newBufferedReader(conf)) {
			final JsonElement json = parser.parse(data);

			if (!json.isJsonObject()) {
				throw new NotAProperConfigException();
			}

			final JsonObject root = json.getAsJsonObject();
			if (!root.has(Constants.ConfigNames.FILE_VERSION)) {
				throw new MissingVersionException();
			}

			final float version = root.get(Constants.ConfigNames.FILE_VERSION).getAsFloat();
			if (version < 2f) {
				throw new OldVersionException();
			} else if (version != 2f) {
				throw new UnknownVersionException();
			}

			if (!root.has(Constants.ConfigNames.SPAWNS)) {
				throw new NotAProperConfigException();
			}

			final JsonObject spawnData = doHandlePresets(root).get(Constants.ConfigNames.SPAWNS)
					.getAsJsonObject();
			spawnData.entrySet().stream().forEach(e -> {
				try {
					OreSpawn.API.mapEntryToFile(conf, e.getKey());
					loadSingleEntry(e);
				} catch (UnknownFieldException | BadValueException | UnknownNameException e1) {
					CrashReport report = CrashReport.makeCrashReport(e1,
							"Error parsing an entry " + e.getKey() + " in " + conf.toString());
					report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
					OreSpawn.LOGGER.info(report.getCompleteReport());
				}
			});
		} catch (final IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e,
					"Failed reading config data " + conf.toString());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		} catch (final JsonIOException | JsonSyntaxException e) {
			CrashReport report = CrashReport.makeCrashReport(e,
					"JSON Parsing Error in " + conf.toString());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}
	}

	private static JsonObject doHandlePresets(final JsonObject spawnData) {
		final PresetsStorage configPresets = OreSpawn.API.copyPresets();
		if (spawnData.has(Constants.ConfigNames.PRESETS)) {
			spawnData.get(Constants.ConfigNames.PRESETS).getAsJsonObject().entrySet().stream()
					.forEach(entry -> {
						String section = entry.getKey();
						entry.getValue().getAsJsonObject().entrySet().stream()
								.forEach(sect -> configPresets.setSymbolSection(section,
										sect.getKey(), sect.getValue()));
					});
		}

		final JsonObject spawnDataFixed = new JsonObject();
		for (final Entry<String, JsonElement> elem : spawnData.get(Constants.ConfigNames.SPAWNS)
				.getAsJsonObject().entrySet()) {
			spawnDataFixed.add(elem.getKey(), doPresetFix(elem.getValue(), configPresets));
		}
		return spawnData;
	}

	private static JsonElement doPresetFix(final JsonElement value,
			final PresetsStorage configPresets) {
		if (value.isJsonObject()) {
			return doPresetForObject(value.getAsJsonObject(), configPresets);
		} else if (value.isJsonArray()) {
			return doPresetForArray(value.getAsJsonArray(), configPresets);
		} else if (value.isJsonPrimitive() && !value.isJsonNull()) {
			if (value.getAsJsonPrimitive().isString() && value.getAsString().matches("^\\$.*")) {
				return configPresets.get(value.getAsString());
			} else {
				return value;
			}
		} else {
			OreSpawn.LOGGER.error("Error handling presets for config, unknown value type for item "
					+ value.toString());
			return value;
		}
	}

	private static JsonElement doPresetForArray(final JsonArray value,
			final PresetsStorage configPresets) {
		final JsonArray rv = new JsonArray();
		value.forEach(it -> rv.add(doPresetFix(it, configPresets)));
		return rv;
	}

	private static JsonElement doPresetForObject(final JsonObject value,
			final PresetsStorage configPresets) {
		final JsonObject rv = new JsonObject();

		value.entrySet().stream().forEach(
				entry -> rv.add(entry.getKey(), doPresetFix(entry.getValue(), configPresets)));
		return rv;
	}

	public static void loadFromJson(final String name, final JsonElement json) {
		final Entry<String, JsonElement> t = new AbstractMap.SimpleEntry<>(name, json);
		try {
			loadSingleEntry(t);
		} catch (UnknownFieldException | BadValueException | UnknownNameException e) {
			final CrashReport report = CrashReport.makeCrashReport(e,
					"Error parsing an manual JSON read for " + name);
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}
	}

	private static void loadSingleEntry(final Entry<String, JsonElement> entry)
			throws UnknownFieldException, BadValueException, UnknownNameException {
		final ISpawnBuilder sb = OreSpawn.API.getSpawnBuilder();
		final IFeatureBuilder fb = OreSpawn.API.getFeatureBuilder();
		sb.setName(entry.getKey());

		for (final Entry<String, JsonElement> ent : entry.getValue().getAsJsonObject().entrySet()) {
			switch (ent.getKey()) {
				case Constants.ConfigNames.RETROGEN:
					sb.setRetrogen(ent.getValue().getAsBoolean());
					break;
				case Constants.ConfigNames.ENABLED:
					sb.setEnabled(ent.getValue().getAsBoolean());
					break;
				case Constants.ConfigNames.DIMENSIONS:
					final IDimensionBuilder db = OreSpawn.API.getDimensionBuilder();
					if (ent.getValue().isJsonArray()) {
						final JsonArray dims = ent.getValue().getAsJsonArray();
						if (dims.size() == 0) {
							// blank list, accept all overworld
							db.setAcceptAllOverworld();
						} else {
							dims.forEach(item -> {
								if (item.isJsonPrimitive()
										&& item.getAsJsonPrimitive().isNumber()) {
									db.addWhitelistEntry(item.getAsInt());
								}
							});
						}
					} else if (ent.getValue().isJsonObject()) {
						loadDimensions(db, ent.getValue().getAsJsonObject());
					} else {
						throw new BadValueException(Constants.ConfigNames.DIMENSIONS,
								ent.getValue().toString());
					}
					sb.setDimensions(db.create());
					break;
				case Constants.ConfigNames.BIOMES:
					if (!ent.getValue().isJsonObject()) {
						throw new BadValueException(Constants.ConfigNames.BIOMES,
								ent.getValue().toString());
					}
					final IBiomeBuilder bb = OreSpawn.API.getBiomeBuilder();
					loadBiomes(bb, ent.getValue().getAsJsonObject());
					sb.setBiomes(bb.create());
					break;
				case Constants.ConfigNames.FEATURE:
					if (ent.getValue().isJsonPrimitive()
							&& !ent.getValue().getAsJsonPrimitive().isString()) {
						throw new BadValueException(Constants.ConfigNames.FEATURE,
								ent.getValue().toString());
					}
					final String featureName = ent.getValue().getAsString();
					if (!OreSpawn.API.featureExists(featureName)) {
						throw new UnknownNameException(Constants.ConfigNames.FEATURE, featureName);
					}
					fb.setFeature(featureName);
					break;
				case Constants.ConfigNames.REPLACEMENT:
					final IReplacementBuilder rb = OreSpawn.API.getReplacementBuilder();
					if (!ent.getValue().isJsonArray()
							&& !ent.getValue().getAsJsonPrimitive().isString()) {
						throw new BadValueException(Constants.ConfigNames.REPLACEMENT,
								ent.getValue().toString());
					} else if (ent.getValue().isJsonPrimitive()
							&& ent.getValue().getAsJsonPrimitive().isString()) {
						if (OreSpawn.API.hasReplacement(ent.getValue().getAsString())) {
							rb.setFromName(ent.getValue().getAsString());
						}
					} else {
						for (final JsonElement e : ent.getValue().getAsJsonArray()) {
							if (e.isJsonObject()) {
								loadBlock(e.getAsJsonObject()).stream().forEach(rb::addEntry);
							} else {
								OreSpawn.LOGGER.error(
										"Skipping value %s in replacements list as it is not the correct format",
										e.toString());
							}
						}
					}

					if (rb.hasEntries()) {
						sb.setReplacement(rb.create());
					}
					break;
				case Constants.ConfigNames.BLOCK:
					if (ent.getValue().isJsonArray()) {
						for (final JsonElement elem : ent.getValue().getAsJsonArray()) {
							final IBlockBuilder block = OreSpawn.API.getBlockBuilder();
							if (elem.isJsonObject()) {
								final JsonObject bl = elem.getAsJsonObject();
								if (bl.has(Constants.ConfigNames.STATE)) {
									block.setFromNameWithChance(
											bl.get(Constants.ConfigNames.NAME).getAsString(),
											bl.get(Constants.ConfigNames.STATE).getAsString(),
											bl.get(Constants.ConfigNames.CHANCE).getAsInt());
								} else if (bl.has(Constants.ConfigNames.METADATA)) {
									block.setFromNameWithChance(
											bl.get(Constants.ConfigNames.NAME).getAsString(),
											bl.get(Constants.ConfigNames.METADATA).getAsInt(),
											bl.get(Constants.ConfigNames.CHANCE).getAsInt());
								} else {
									block.setFromNameWithChance(
											bl.get(Constants.ConfigNames.NAME).getAsString(),
											bl.get(Constants.ConfigNames.CHANCE).getAsInt());
								}
								sb.addBlock(block.create());
							} else {
								OreSpawn.LOGGER.error(
										"Skipping value %s in blocks list as it is not the correct format",
										elem.toString());
							}
						}
					} else {
						throw new BadValueException(Constants.ConfigNames.BLOCK,
								ent.getValue().toString());
					}
				case Constants.ConfigNames.PARAMETERS:
					if (ent.getValue().isJsonObject()) {
						ent.getValue().getAsJsonObject().entrySet().stream()
								.forEach(e -> fb.setParameter(e.getKey(), e.getValue()));
					}
					break;
				default:
					throw new UnknownFieldException(ent.getKey());
			}
		}
		sb.setFeature(fb.create());
		OreSpawn.API.addSpawn(sb.create());
	}

	private static List<IBlockState> loadBlock(final JsonObject json) {
		final String blockName = json.get(Constants.ConfigNames.NAME).getAsString();
		if (json.has(Constants.ConfigNames.STATE)) {
			final Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
			return Arrays.asList(StateUtil.deserializeState(block,
					json.get(Constants.ConfigNames.STATE).getAsString()));
		} else if (json.has(Constants.ConfigNames.METADATA)) {
			final Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
			return Arrays.asList(
					block.getStateFromMeta(json.get(Constants.ConfigNames.METADATA).getAsInt()));
		}

		if (blockName.startsWith("ore:")) {
			final String entry = blockName.split(":")[1];
			return ImmutableList.copyOf(OreDictionary.getOres(entry, false).stream().map(is -> {
				final Block b = Block.getBlockFromItem(is.getItem());
				return b.getStateFromMeta(is.getMetadata());
			}).collect(Collectors.toList()));
		}
		final Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
		return Arrays.asList(block.getDefaultState());
	}

	private static void loadBiomes(final IBiomeBuilder bb, final JsonObject biomeList) {
		boolean emptyWhitelist = false;

		if (biomeList.has(Constants.ConfigNames.WHITELIST)
				&& biomeList.get(Constants.ConfigNames.WHITELIST).getAsJsonArray().size() > 0) {
			for (final JsonElement elem : biomeList.get(Constants.ConfigNames.WHITELIST)
					.getAsJsonArray()) {
				if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
					final String xN = elem.getAsString();
					if (xN.contains(":")) {
						// not a BiomeDictionary entry (we hope)
						bb.addWhitelistEntry(xN);
					} else {
						BiomeDictionary.getBiomes(BiomeDictionary.Type.getType(xN)).stream()
								.forEach(bb::addWhitelistEntry);
					}
				} else {
					OreSpawn.LOGGER.error("Skipping entry (%s) in whitelist, not a proper value",
							elem.getAsString());
				}
			}
		} else {
			emptyWhitelist = true;
		}

		if (biomeList.has(Constants.ConfigNames.BLACKLIST)
				&& biomeList.get(Constants.ConfigNames.BLACKLIST).getAsJsonArray().size() > 0) {
			for (final JsonElement elem : biomeList.get(Constants.ConfigNames.BLACKLIST)
					.getAsJsonArray()) {
				if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
					final String xN = elem.getAsString();
					if (xN.contains(":")) {
						// not a BiomeDictionary entry (we hope)
						bb.addBlacklistEntry(xN);
					} else {
						BiomeDictionary.getBiomes(BiomeDictionary.Type.getType(xN)).stream()
								.forEach(bb::addBlacklistEntry);
					}
				} else {
					OreSpawn.LOGGER.error("Skipping entry (%s) in blacklist, not a proper value",
							elem.getAsString());
				}
			}
		} else if (emptyWhitelist) {
			// empty whitelist and blacklist - accept everything
			bb.setAcceptAll();
		}
	}

	private static void loadDimensions(final IDimensionBuilder db, final JsonObject dimensionList) {
		boolean emptyWhitelist = false;

		if (dimensionList.has(Constants.ConfigNames.WHITELIST)
				&& dimensionList.get(Constants.ConfigNames.WHITELIST).getAsJsonArray().size() > 0) {
			for (final JsonElement elem : dimensionList.get(Constants.ConfigNames.WHITELIST)
					.getAsJsonArray()) {
				if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isNumber()) {
					db.addWhitelistEntry(elem.getAsInt());
				} else {
					OreSpawn.LOGGER.error("Skipping entry (%s) in whitelist, not a proper value",
							elem.getAsString());
				}
			}
		} else {
			emptyWhitelist = true;
		}

		if (dimensionList.has(Constants.ConfigNames.BLACKLIST)
				&& dimensionList.get(Constants.ConfigNames.BLACKLIST).getAsJsonArray().size() > 0) {
			for (final JsonElement elem : dimensionList.get(Constants.ConfigNames.BLACKLIST)
					.getAsJsonArray()) {
				if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isNumber()) {
					db.addBlacklistEntry(elem.getAsInt());
				} else {
					OreSpawn.LOGGER.error("Skipping entry (%s) in whitelist, not a proper value",
							elem.getAsString());
				}
			}
		} else if (emptyWhitelist) {
			db.setAcceptAllOverworld();
		}
	}
}
