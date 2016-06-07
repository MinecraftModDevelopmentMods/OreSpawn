package cyano.orespawn.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cyano.orespawn.OreSpawn;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.registry.GameData;

import java.util.*;

public class OreSpawnData {
	public final float frequency;
	public final int spawnQuantity;
	public final int minY;
	public final int maxY;
	public final int variation;
	public final boolean restrictBiomes;
	public final Set<String> biomesByName;

	public final Block ore;
	public final int metaData;

	public static final OreSpawnData EMPTY_PLACEHOLDER = new OreSpawnData(Blocks.STONE,0,1,127,0,1,1,Collections.EMPTY_LIST);
	
	public OreSpawnData(Block oreBlock, int metaDataValue, int minHeight, int maxHeight, float spawnFrequency, int spawnQuantity, int spawnQuantityVariation, Collection<String> biomes){
		this.spawnQuantity = spawnQuantity;
		this.frequency = spawnFrequency;
		this.minY = minHeight;
		this.maxY = maxHeight;
		this.ore = oreBlock;
		this.metaData = metaDataValue;
		this.variation = spawnQuantityVariation;
		this.restrictBiomes = biomes != null && !biomes.isEmpty();
		Set<String> list = new HashSet<>();
		if(restrictBiomes) list.addAll(biomes);
		biomesByName = Collections.unmodifiableSet(list);
	}
	
	private static boolean doOnce = true; 

	public static OreSpawnData parseOreSpawnData(JsonObject jsonEntry){
		String blockName = jsonEntry.get("blockID").getAsString();
		if(blockName == null || Block.getBlockFromName(blockName) == null){
			// block does not exist!
			if(OreSpawn.ignoreNonExistant) {
				FMLLog.warning("%s: ignoring orespawn data for %s because that block does not exist",OreSpawn.MODID,blockName);
				return EMPTY_PLACEHOLDER;
			}else{
				throw new IllegalArgumentException(String.format("Ore block with ID %s does not exist!",blockName));
			}
		}
		return new OreSpawnData(jsonEntry);
	}

	private OreSpawnData(JsonObject jsonEntry){
		String blockName = jsonEntry.get("blockID").getAsString();
		String modId;
		String name;
		if(blockName.contains(":")){
			modId = blockName.substring(0,blockName.indexOf(":"));
			name = blockName.substring(blockName.indexOf(":")+1);
		} else {
			modId = "minecraft";
			name = blockName;
		}
		//this.ore = GameRegistry.findBlock(modId, name); // sadly, this doesn't work because the GameData now store the block key as a ResourceLocation instead of a String
		//this.ore = GameData.getBlockRegistry().getObject(new ResourceLocation(blockName));
		ResourceLocation blockKey = new ResourceLocation(blockName);
		if(!Block.REGISTRY.containsKey(blockKey)){
			FMLLog.severe("Failed to find ore block "+modId+":"+name);
			if(doOnce){
				StringBuilder sb = new StringBuilder("Valid block IDs:\n");
				for(Object key : GameData.getBlockRegistry().getKeys()){
					sb.append("\t(").append(key.getClass().getName()).append(")\t")
					.append(String.valueOf(key)).append("\n");
				}
				FMLLog.severe(sb.toString());
				doOnce = false;
			}
		}
		this.ore = (Block)Block.REGISTRY.getObject(blockKey);
		this.metaData = get("blockMeta",0,jsonEntry);
		this.spawnQuantity = (int)get("size",8.0f,jsonEntry);
		this.frequency = get("frequency",20.0f,jsonEntry);
		this.minY = get("minHeight",0,jsonEntry);
		this.maxY = get("maxHeight",255,jsonEntry);
		this.variation = (int)get("variation",0.5f * spawnQuantity,jsonEntry);
		if(jsonEntry.has("biomes") && jsonEntry.get("biomes").getAsJsonArray().size() > 0){
			this.restrictBiomes = true;
			JsonArray biomeEntries = jsonEntry.get("biomes").getAsJsonArray();
			Set<String> list = new HashSet<>();
			for(int n = 0; n < biomeEntries.size(); n++){
				list.add(biomeEntries.get(n).getAsString());
			}
			biomesByName = Collections.unmodifiableSet(list);
		} else {
			this.restrictBiomes = false;
			biomesByName = Collections.EMPTY_SET;
		}
	}

	private static int get(String key, int defaultValue, JsonObject root){
		if(root.has(key)){
			return root.get(key).getAsInt();
		} else {
			return defaultValue;
		}
	}
	private static float get(String key, float defaultValue, JsonObject root){
		if(root.has(key)){
			return root.get(key).getAsFloat();
		} else {
			return defaultValue;
		}
	}
	private static String get(String key, String defaultValue, JsonObject root){
		if(root.has(key)){
			return root.get(key).getAsString();
		} else {
			return defaultValue;
		}
	}
	@Override
	public String toString(){
		return "oreSpawn: [ore="+ore+"#"+metaData+",frequency="+frequency+",spawnQuantity="+spawnQuantity+",variation=+/-"+variation+",Y-range="+minY+"-"+maxY+",restrictBiomes="+restrictBiomes+",biomes="+Arrays.toString(biomesByName.toArray())+"]";
	}
}
