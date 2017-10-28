package com.mcmoddev.orespawn.json;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.*;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;
import com.mcmoddev.orespawn.json.os3.readers.*;

import net.minecraft.crash.CrashReport;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

public class OS3Reader {

	private OS3Reader() {

	}
	private static void loadFeatures(File file) {
		OreSpawn.FEATURES.loadFeaturesFile(file);
	}

	public static void loadEntries() {
		File directory = new File("config","orespawn3");
		JsonParser parser = new JsonParser();

		if( !directory.exists() ) {
			directory.mkdirs();
			return;
		}

		if( !directory.isDirectory() ) {
			OreSpawn.LOGGER.fatal("OreSpawn data directory inaccessible - "+directory+" is not a directory!");
			return;
		}

		File[] files = directory.listFiles();
		if( files.length == 0 ) {
			// nothing to load
			return;
		}

		if( Files.exists(Paths.get("config","orespawn3","sysconf")) && Files.isDirectory(Paths.get("config","orespawn3","sysconf")) ) {
			Arrays.stream( Paths.get("config","orespawn3","sysconf").toFile().listFiles() )
			.filter( file -> "json".equals(FilenameUtils.getExtension(file.getName())))
			.forEach( file -> {
				String filename = file.getName();
				if( FilenameUtils.getBaseName(filename).matches("features-.+") ) {
					loadFeatures( file );
				} else if(FilenameUtils.getBaseName(filename).matches("replacements-.+") ) {
					Replacements.load(file);
				}
			});
		}
		
		Arrays.stream(files).filter(file -> file.getName().endsWith(".json")).forEach(
				file -> {
					try {
						String rawData = FileUtils.readFileToString(file, Charset.defaultCharset());
						if( rawData.isEmpty() ) return;
						JsonElement full = parser.parse(rawData);
						JsonObject parsed = full.getAsJsonObject();

						IOS3Reader reader = null;
						String version = parsed.get("version").getAsString();
						switch( version ) {
						case "1":
							reader = new OS3V1Reader();
							break;
						case "1.1":
							reader = new OS3V11Reader();
							break;
						case "1.2":
							reader = new OS3V12Reader();
							break;
						case "2.0":
							reader = new OS3V2Reader();
							break;
						default:
							OreSpawn.LOGGER.error("Unknown version %s", version);
							return;
						}

						base_load(reader.parseJson(parsed, FilenameUtils.getBaseName(file.getName())), FilenameUtils.getBaseName(file.getName()));
					} catch (Exception e) {
						CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
						report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
						OreSpawn.LOGGER.info(report.getCompleteReport());
					}
				});

	}
	
	/**
	 * Actually parse the normalized data
	 * @param parseJson normalized data returned by the file loader/normalizer
	 */
	private static void base_load(JsonObject parseJson, String filename) {
		JsonObject work = parseJson.getAsJsonObject("dimensions");
		BuilderLogic logic = OreSpawn.API.getLogic(filename);
		
		// at the top-most level we have the dimension sets
		work.entrySet().forEach( entry -> {
			int dimension = Integer.parseInt(entry.getKey());
			DimensionBuilder builder = logic.newDimensionBuilder(dimension);
			
			entry.getValue().getAsJsonArray().forEach( ore -> {
				JsonObject nw = ore.getAsJsonObject();
				SpawnBuilder spawn = builder.newSpawnBuilder(null);
				// load the "ores" as "OreBuilder" - we should always have a "blocks" here, so...
				List<OreBuilder> blocks = Helpers.loadOres( nw.getAsJsonArray(ConfigNames.BLOCKS), spawn);
				List<IBlockState> replacements = getReplacements(nw.get(ConfigNames.V2.REPLACES).getAsString(), dimension);
				BiomeBuilder biomes = spawn.newBiomeBuilder();
				
				if( nw.getAsJsonObject(ConfigNames.BIOMES).size() < 1 ) {
					biomes.setFromBiomeLocation(Helpers.deserializeBiomeLocationComposition(nw.getAsJsonObject(ConfigNames.BIOMES)));
				}
				
				FeatureBuilder gen = spawn.newFeatureBuilder(nw.get(ConfigNames.FEATURE).getAsString());
				gen.setDefaultParameters();
				gen.setParameters(nw.getAsJsonObject(ConfigNames.PARAMETERS));
				spawn.enabled( nw.get(ConfigNames.V2.ENABLED).getAsBoolean());
				spawn.retrogen( nw.get(ConfigNames.V2.RETROGEN).getAsBoolean());
				spawn.create(biomes, gen, replacements, blocks.stream().toArray(OreBuilder[]::new));
				builder.create(spawn);
			});
			logic.create(builder);
		});
	}
	
	private static List<IBlockState> getReplacements(String configField, int dimension) {
		String work = configField.toLowerCase();
		
		if( work.equals(ConfigNames.DEFAULT)) {
			return ReplacementsRegistry.getDimensionDefault(dimension);
		} else if( work.startsWith("ore:") ) {
			NonNullList<ItemStack> ores = OreDictionary.getOres(work.substring(4));
			List<IBlockState> reps = new ArrayList<>();
			ores.forEach( ore -> reps.add(Block.getBlockFromItem(ore.getItem()).getDefaultState()));
			return reps;
		} else if( !work.matches(":") ) { // probably a "replacements registry" entry
			return Arrays.asList(ReplacementsRegistry.getBlock(work));
		} else {
			return Arrays.asList( ForgeRegistries.BLOCKS.getValue(new ResourceLocation(configField)).getDefaultState());
		}
	}

	public static void loadFromJson(String modName, JsonObject json) {
		String version = json.get("version").getAsString();
		IOS3Reader reader = null;

		switch( version ) {
		case "1":
			reader = new OS3V1Reader();
			break;
		case "1.1":
			reader = new OS3V11Reader();
			break;
		case "1.2":
			reader = new OS3V12Reader();
			break;
		case "2.0":
			reader = new OS3V2Reader();
			break;
		default:
			OreSpawn.LOGGER.error("Unknown version %s", version);
			return;
		}

		reader.parseJson(json, modName);
	}
}
