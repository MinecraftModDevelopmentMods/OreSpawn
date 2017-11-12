package com.mcmoddev.orespawn.json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.google.common.base.Charsets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
		File directory = new File(Constants.FileBits.CONFIG_DIR,Constants.FileBits.OS3);
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

		Path presets = Paths.get(Constants.FileBits.CONFIG_DIR, Constants.FileBits.OS3, Constants.FileBits.SYSCONF, Constants.FileBits.PRESETS);
		loadPresets( presets );
		
		if( Paths.get(Constants.FileBits.CONFIG_DIR,Constants.FileBits.OS3,Constants.FileBits.SYSCONF).toFile().exists() && Paths.get(Constants.FileBits.CONFIG_DIR,Constants.FileBits.OS3,Constants.FileBits.SYSCONF).toFile().isDirectory() ) {
			Arrays.stream( Paths.get(Constants.FileBits.CONFIG_DIR,Constants.FileBits.OS3,Constants.FileBits.SYSCONF).toFile().listFiles() )
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

						String version = parsed.get("version").getAsString();
						IOS3Reader reader = getReader( version );
						if( reader != null )
							finallyParse(reader.parseJson(parsed, FilenameUtils.getBaseName(file.getName())), FilenameUtils.getBaseName(file.getName()));
					} catch (Exception e) {
						CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
						report.getCategory().addCrashSection(Constants.ORESPAWN_VERSION_CRASH_MESSAGE, Constants.VERSION);
						OreSpawn.LOGGER.info(report.getCompleteReport());
					}
				});

	}
	
	private static void loadPresets(Path presets) {
		if( presets.toFile().exists() ) {
			try {
				JsonParser parser = new JsonParser();
				String rawJson = FileUtils.readFileToString(presets.toFile(), Charsets.UTF_8);
				JsonObject top = parser.parse(rawJson).getAsJsonObject();
				top.entrySet()
				.forEach( entry -> {
					String section = entry.getKey();
					entry.getValue().getAsJsonObject().entrySet()
					.forEach( pres -> OreSpawn.API.getPresets().setSymbolSection(section, pres.getKey(), pres.getValue()) );
				});
			} catch( IOException exc ) {
				CrashReport report = CrashReport.makeCrashReport(exc, "Failed reading presets " + presets.toFile().getName());
				report.getCategory().addCrashSection(Constants.ORESPAWN_VERSION_CRASH_MESSAGE, Constants.VERSION);
				OreSpawn.LOGGER.info(report.getCompleteReport());
			} catch( JsonParseException ex ) {
				CrashReport report = CrashReport.makeCrashReport(ex, "Failed loading or parsing " + presets.toFile().getName());
				report.getCategory().addCrashSection(Constants.ORESPAWN_VERSION_CRASH_MESSAGE, Constants.VERSION);
				OreSpawn.LOGGER.info(report.getCompleteReport());
			}
		}
	}
	
	/**
	 * Actually parse the normalized data
	 * @param parseJson normalized data returned by the file loader/normalizer
	 */
	private static void finallyParse(JsonObject parseJson, String filename) {
		JsonObject work = parseJson.getAsJsonObject("dimensions");
		BuilderLogic logic = OreSpawn.API.getLogic(filename);
		
		// at the top-most level we have the dimension sets
		work.entrySet().forEach( entry -> {
			int dimension = Integer.parseInt(entry.getKey());
			DimensionBuilder builder = logic.newDimensionBuilder(dimension);
			
			entry.getValue().getAsJsonArray().forEach( ore -> {
				try {
					JsonObject nw = ore.getAsJsonObject();
					SpawnBuilder spawn = builder.newSpawnBuilder(null);
					// load the "ores" as "OreBuilder" - we should always have a "blocks" here, so...
					List<OreBuilder> blocks = Helpers.loadOres( nw.getAsJsonArray(ConfigNames.BLOCKS), spawn);
					List<IBlockState> replacements = getReplacements(nw.get(ConfigNames.V2.REPLACES).getAsString(), dimension);
					BiomeBuilder biomes = spawn.newBiomeBuilder();

					if( nw.get(ConfigNames.BIOMES).isJsonObject () ) {
						biomes.setFromBiomeLocation(Helpers.deserializeBiomeLocationComposition(nw.getAsJsonObject(ConfigNames.BIOMES)));
					}

					FeatureBuilder gen = spawn.newFeatureBuilder(nw.get(ConfigNames.FEATURE).getAsString());
					gen.setDefaultParameters();
					gen.setParameters(nw.getAsJsonObject(ConfigNames.PARAMETERS));
					spawn.enabled( nw.get(ConfigNames.V2.ENABLED).getAsBoolean());
					spawn.retrogen( nw.get(ConfigNames.V2.RETROGEN).getAsBoolean());

					if( nw.has( ConfigNames.DIMENSION) )
						spawn.create( biomes, gen, replacements, nw.getAsJsonObject( ConfigNames.DIMENSION ), blocks.toArray( new OreBuilder[0]) );
					else
						spawn.create(biomes, gen, replacements, blocks.toArray( new OreBuilder[0] ) );

					builder.create(spawn);
				} catch( JsonParseException ex ) {
					OreSpawn.LOGGER.error("Error parsing entry %s : %s", ore.getAsJsonObject().get("name").getAsString(), ex);
				} catch( NullPointerException npe ) {
					OreSpawn.LOGGER.error("Exception parsing entry %s : possibly mis-named or missing item ?", ore.getAsJsonObject().get("name").getAsString());
				}
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
		} else if( !work.contains(":") ) { // probably a "replacements registry" entry
			return Arrays.asList(ReplacementsRegistry.getBlock(work));
		} else {
			return Arrays.asList(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(configField)).getDefaultState());
		}
	}

	public static IOS3Reader getReader(String version) {
		switch( version ) {
			case "1":
			case "1.1":
			case "1.2":
				return new OS3V1Reader();
			case "2.0":
				return new OS3V2Reader();
			default:
				OreSpawn.LOGGER.error("Unknown version %s", version);
				return null;
		}
	}
}
