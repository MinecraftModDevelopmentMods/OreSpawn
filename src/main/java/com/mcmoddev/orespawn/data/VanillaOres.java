package com.mcmoddev.orespawn.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.*;
import com.mcmoddev.orespawn.api.plugin.IOreSpawnPlugin;
import com.mcmoddev.orespawn.api.plugin.OreSpawnPlugin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

@OreSpawnPlugin( modid = "orespawn", resourcePath = "configs" )
public class VanillaOres implements IOreSpawnPlugin {
	private final Map<String, Map<String,Float>> bits;

	public VanillaOres() {
		this.bits = new HashMap<>();
		String[] ores = new String[] {
				"minecraft:coal_ore",
				"minecraft:iron_ore",
				"minecraft:gold_ore",
				"minecraft:diamond_ore",
				"minecraft:redstone_ore",
				"minecraft:lapis_ore",
				"minecraft:gravel"
		};
		Arrays.<String>asList(ores).stream().forEach(ore -> this.bits.put(ore, new HashMap<>()));
		this.bits.entrySet().forEach( entry -> setupParameters(entry) );
	}
	
	private void setupParameters(Entry<String, Map<String, Float>> entry) {
		switch(entry.getKey()) {
		case "minecraft:coal_ore":
			entry.getValue().put("size",(float)25);
			entry.getValue().put("variation",(float)12);
			entry.getValue().put("frequency",20.0f);
			entry.getValue().put("minHeight",(float)0);
			entry.getValue().put("maxHeight",(float)128);
			break;
		case "minecraft:iron_ore":
			entry.getValue().put("size",(float)8);
			entry.getValue().put("variation",(float)4);
			entry.getValue().put("frequency",20.0f);
			entry.getValue().put("minHeight",(float)0);
			entry.getValue().put("maxHeight",(float)64);
			break;
		case "minecraft:gold_ore":
			entry.getValue().put("size",(float)8);
			entry.getValue().put("variation",(float)2);
			entry.getValue().put("frequency",2.0f);
			entry.getValue().put("minHeight",(float)0);
			entry.getValue().put("maxHeight",(float)32);
			break;
		case "minecraft:diamond_ore":
			entry.getValue().put("size",(float)6);
			entry.getValue().put("variation",(float)3);
			entry.getValue().put("frequency",8.0f);
			entry.getValue().put("minHeight",(float)0);
			entry.getValue().put("maxHeight",(float)16);
			break;
		case "minecraft:redstone_ore":
			entry.getValue().put("size",(float)6);
			entry.getValue().put("variation",(float)3);
			entry.getValue().put("frequency",8.0f);
			entry.getValue().put("minHeight",(float)0);
			entry.getValue().put("maxHeight",(float)16);
			break;
		case "minecraft:lapis_ore":
			entry.getValue().put("size",(float)5);
			entry.getValue().put("variation",(float)2);
			entry.getValue().put("frequency",1.0f);
			entry.getValue().put("minHeight",(float)0);
			entry.getValue().put("maxHeight",(float)32);
			break;
		case "minecraft:gravel":
			entry.getValue().put("size",(float)112);
			entry.getValue().put("variation",(float)50);
			entry.getValue().put("frequency",8.0f);
			entry.getValue().put("minHeight",(float)0);
			entry.getValue().put("maxHeight",(float)255);
			break;
		default:
			return;
		}
	}
	
	@Override
	public void register(OS3API apiInterface) {
		BuilderLogic logic = apiInterface.getLogic("orespawn");
        DimensionBuilder dim = logic.newDimensionBuilder();
        List<SpawnBuilder> spawns = new ArrayList<>();
        for( String oreName : this.bits.keySet() ) {
        	spawns.add(makeNormalSpawn(dim, oreName, "minecraft:stone"));
        }
        spawns.add(makeEmeraldSpawn(dim));
        spawns.addAll(makeDirtAndStoneSpawns(dim));
        spawns.add(makeNetherSpawns(logic));
	}
	
	private SpawnBuilder makeNetherSpawns(BuilderLogic logic) {
		DimensionBuilder nether = logic.newDimensionBuilder(-1);
		SpawnBuilder netherSpawns = nether.newSpawnBuilder("nether");
		OreBuilder ore = netherSpawns.newOreBuilder();
		ore.setOre("minecraft:quartz_ore");
		FeatureBuilder feature = netherSpawns.newFeatureBuilder("default");
		feature.setDefaultParameters();
		feature.setGenerator("default");
        feature.addParameter("size", 15);
        feature.addParameter("variation", 4);
        feature.addParameter("frequency", 7.0f);
        feature.addParameter("minHeight", 0);
        feature.addParameter("maxHeight", 128);
        BiomeBuilder biomes = netherSpawns.newBiomeBuilder();
        IBlockState rep = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft","netherrack")).getDefaultState();
        List<IBlockState> replacements = new ArrayList<>();
        replacements.add(rep);
        return netherSpawns.create(biomes, feature, replacements, ore);
	}

	private List<SpawnBuilder> makeDirtAndStoneSpawns(DimensionBuilder dim) {
		List<SpawnBuilder> spawns = new ArrayList<>();
		String[] stoneVariants = new String[] {
				"variant=granite",
				"variant=andesite",
				"variant=diorite"
		};
		
        IBlockState rep = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")).getDefaultState();
        List<IBlockState> replacements = new ArrayList<>();
        replacements.add(rep);
        
		for( String variant : stoneVariants ) {
			SpawnBuilder thisSpawn = dim.newSpawnBuilder(null);
			OreBuilder thisOre = thisSpawn.newOreBuilder();
			thisOre.setOre("minecraft:stone", variant);
			BiomeBuilder localBiomes = thisSpawn.newBiomeBuilder();
	        FeatureBuilder feature = thisSpawn.newFeatureBuilder("default");
	        feature.setDefaultParameters();
			feature.addParameter("size",112);
			feature.addParameter("variation",50);
			feature.addParameter("frequency",10.0f);
			feature.addParameter("minHeight",0);
			feature.addParameter("maxHeight",255);	        
	        spawns.add(thisSpawn.create(localBiomes, feature, replacements, thisOre));
		}

		SpawnBuilder thisSpawn = dim.newSpawnBuilder(null);
		OreBuilder thisOre = thisSpawn.newOreBuilder();
		thisOre.setOre("minecraft:dirt", "snowy=false,variant=dirt");
		BiomeBuilder localBiomes = thisSpawn.newBiomeBuilder();
        FeatureBuilder feature = thisSpawn.newFeatureBuilder("default");
        feature.setDefaultParameters();
        feature.addParameter("size",112);
        feature.addParameter("variation",50);
        feature.addParameter("frequency",10.0f);
        feature.addParameter("minHeight",0);
        feature.addParameter("maxHeight",255);
        spawns.add(thisSpawn.create(localBiomes, feature, replacements, thisOre));
		return spawns;
	}

	private SpawnBuilder makeEmeraldSpawn(DimensionBuilder dim) {
		SpawnBuilder rv = dim.newSpawnBuilder(null);
		OreBuilder ores = rv.newOreBuilder();
		ores.setOre("minecraft:emerald_ore");
		FeatureBuilder feature = rv.newFeatureBuilder("default");
		feature.setDefaultParameters();
		feature.addParameter("size",(float)1);
		feature.addParameter("variation",(float)0);
		feature.addParameter("frequency",8.0f);
		feature.addParameter("minHeight",(float)4);
		feature.addParameter("maxHeight",(float)32);
        IBlockState rep = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")).getDefaultState();
        List<IBlockState> replacements = new ArrayList<>();
        replacements.add(rep);
        BiomeBuilder biomes = rv.newBiomeBuilder();
        biomes.whitelistBiomeByName("minecraft:extreme_hills");
        biomes.whitelistBiomeByName("minecraft:smaller_extreme_hills");
		return rv.create(biomes, feature, replacements, ores);
	}

	private SpawnBuilder makeNormalSpawn( DimensionBuilder dim, String ore, String replaces ) {
		SpawnBuilder rv = dim.newSpawnBuilder(null);
		OreBuilder oreB = rv.newOreBuilder();
		oreB.setOre(ore);
		FeatureBuilder feature = rv.newFeatureBuilder("default");
		feature.setDefaultParameters();
		this.bits.get(ore).entrySet().forEach( entry -> feature.addParameter(entry.getKey(), entry.getValue()));
        IBlockState rep = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(replaces)).getDefaultState();
        List<IBlockState> replacements = new ArrayList<>();
        replacements.add(rep);
		return rv.create(rv.newBiomeBuilder(),feature,replacements,oreB);
	}
}
