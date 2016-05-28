package cyano.orespawn.init;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cyano.orespawn.worldgen.OreSpawnData;
import cyano.orespawn.worldgen.OreSpawner;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


/**
 * This class contains static initializers to add ore spawning as world-gen. 
 * To add a new ore via the Base Metals mod, either add the appropriate entry 
 * in the config file config/orespawn/ore-spawn.json or directly invoke the
 * addOreSpawner(...) method from this class.
 * @author DrCyano
 *
 */
public abstract class WorldGen {

	
	
	private static final Map<Integer,List<OreSpawnData>> oreSpawnRegistry  = new HashMap<>();
	
	
	
	public static void loadConfig(Path jsonFile) throws IOException{
		final JsonObject settings;
		JsonParser parser = new JsonParser();
		BufferedReader fileReader = Files.newBufferedReader(jsonFile, Charset.forName("UTF-8"));
		settings = parser.parse(fileReader).getAsJsonObject();
		fileReader.close();
		parseConfig(settings);
	}
	
	
	public static void init(){
		WorldGenMinable b;
		net.minecraft.world.gen.ChunkProviderHell h;
		net.minecraft.world.biome.BiomeDecorator bd;
		// load ore settings (must be done AFTER loading the blocks
		// add custom spawners to the world
		Random prng = new Random();
		for(Integer dim : oreSpawnRegistry.keySet()){
			List<OreSpawnData> ores = oreSpawnRegistry.get(dim);
			for(OreSpawnData ore : ores){
				addOreSpawner(ore,dim,prng.nextLong());
			}
		}
	}


	private static void parseConfig(JsonObject root) {
		JsonArray dimensions = root
				.get("dimensions")
				.getAsJsonArray();
		for(int n = 0; n < dimensions.size(); n++){
			JsonObject dim = dimensions.get(n).getAsJsonObject();
			final Integer dimIndex;
			if(dim.get("dimension").getAsString().equals("+")){
				// misc dimensions
				dimIndex = null;
			} else {
				dimIndex = dim.get("dimension").getAsInt();
			}
			JsonArray ores = dim.get("ores").getAsJsonArray();
			for(int i = 0; i < ores.size(); i++){
				OreSpawnData ore = new OreSpawnData(ores.get(i).getAsJsonObject());
				FMLLog.info("Parsed ore spawn setting for dimension "+dimIndex+": "+ore);
				if(oreSpawnRegistry.containsKey(dimIndex) == false){
					oreSpawnRegistry.put(dimIndex, new LinkedList<OreSpawnData>());
				}
				oreSpawnRegistry.get(dimIndex).add(ore);
			}
		}
	}
	private static int weight = 100;
	
	/**
	 * Adds a new ore spawner to Minecraft
	 * @param spawnParameters An instance of OreSpawnData describing what to 
	 * spawn and how
	 * @param dimension Which dimension to spawn in (0 for the normal Minecraft 
	 * world, -1 for the Nether, 1 for the End)
	 * @param randomHash This is a random number that should be unique to each 
	 * ore spawner that you add.
	 */
	public static void addOreSpawner(OreSpawnData spawnParameters, Integer dimension, long randomHash){
		OreSpawner spawner = new OreSpawner(spawnParameters,dimension,randomHash);
		GameRegistry.registerWorldGenerator(spawner, weight++);
	}
}
