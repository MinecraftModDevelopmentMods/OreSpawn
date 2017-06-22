package com.mcmoddev.orespawn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.api.SpawnEntry;
import com.mcmoddev.orespawn.api.SpawnLogic;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.DefaultOregenParameters;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

public class EventHandlers {
	
    @SubscribeEvent
    public void onGenerateMinable(OreGenEvent.GenerateMinable event) {
        event.setResult(Event.Result.DENY);
    }
    
	@SubscribeEvent
	public void onChunkSave(ChunkDataEvent.Save ev) {
		NBTTagCompound dataTag = ev.getData().getCompoundTag(Constants.CHUNK_TAG_NAME);
		NBTTagList ores = new NBTTagList();
		NBTTagList features = new NBTTagList();
		features.appendTag( new NBTTagString("orespawn:default"));
		
		for( Entry<String, SpawnLogic> ent : OreSpawn.API.getAllSpawnLogic().entrySet() ) {
			SpawnLogic log = ent.getValue();
			if( log.getAllDimensions().containsKey(ev.getWorld().provider.getDimension()) ) {
				Collection<SpawnEntry> vals = log.getDimension(ev.getWorld().provider.getDimension()).getEntries();
				for( SpawnEntry s : vals ) {
					ores.appendTag( new NBTTagString( s.getState().getBlock().getRegistryName().toString()) );
				}
			}
			if( log.getAllDimensions().containsKey(OreSpawnAPI.DIMENSION_WILDCARD) ) {
				Collection<SpawnEntry> vals = log.getDimension(OreSpawnAPI.DIMENSION_WILDCARD).getEntries();
				for( SpawnEntry s : vals ) {
					ores.appendTag( new NBTTagString( s.getState().getBlock().getRegistryName().toString()) );
				}				
			}
		}
		
		dataTag.setTag(Constants.ORE_TAG, ores);
		dataTag.setTag(Constants.FEATURES_TAG, features);
		ev.getData().setTag(Constants.CHUNK_TAG_NAME, dataTag);
	}
	
	@SubscribeEvent
	public void onChunkLoad(ChunkDataEvent.Load ev) {
		if( Config.getBoolean(Constants.RETROGEN_KEY) ) {
			NBTTagCompound chunkTag = ev.getData().getCompoundTag(Constants.CHUNK_TAG_NAME);
			int count = chunkTag==null?0:chunkTag.getTagList(Constants.ORE_TAG, 8).tagCount();
			if( count != countOres(ev.getWorld().provider.getDimension()) ||
					Config.getBoolean(Constants.FORCE_RETROGEN_KEY)) {
				doRegen(ev.getWorld(), ev.getChunk().x, ev.getChunk().z);
			}
		}
	}

	private void doRegen( World w, int chunkX, int chunkZ ) {
		IChunkProvider prov = w.getChunkProvider();
		IChunkGenerator gen = w.provider.createChunkGenerator();
		Random rand = w.rand;
		
		List<SpawnEntry> spawns = new ArrayList<>();
		for( SpawnLogic sL : OreSpawn.API.getAllSpawnLogic().values() ) {
			DimensionLogic dLM = sL.getDimension(w.provider.getDimension());
			DimensionLogic dLW = sL.getDimension(OreSpawnAPI.DIMENSION_WILDCARD);
			
			spawns.addAll(dLM.getEntries());
			if( !dLW.getEntries().isEmpty() ) {
				spawns.addAll(dLW.getEntries());
			}
		}
		for( SpawnEntry sE : spawns ) {
			Biome biome = w.getBiomeProvider().getBiome(new BlockPos(chunkX*16, 64,chunkZ*16));
			if( sE.getBiomes().contains(biome) || sE.getBiomes() == Collections.EMPTY_LIST || sE.getBiomes().size() == 0 ) {
				retrogen(rand, chunkX, chunkZ, w, prov, gen, sE);
			}
		}
	}
	
	private void retrogen(Random rand, int chunkX, int chunkZ, World world, IChunkProvider prov, IChunkGenerator gen, SpawnEntry s) {
		DefaultOregenParameters p = new DefaultOregenParameters(s);
		s.getFeatureGen().generate(rand, chunkX, chunkZ, world, gen, prov, p);
	}

	private int countOres(int dim) {
		int acc = 0;
		for( Entry<String, SpawnLogic> sL : OreSpawn.API.getAllSpawnLogic().entrySet() ) {
			if( sL.getValue().getAllDimensions().containsKey(dim) ) {
				acc += sL.getValue().getAllDimensions().get(dim).getEntries().size();
			}
			if( sL.getValue().getAllDimensions().containsKey(OreSpawnAPI.DIMENSION_WILDCARD) ) {
				acc += sL.getValue().getAllDimensions().get(OreSpawnAPI.DIMENSION_WILDCARD).getEntries().size();
			}
		}
		return acc;
	}
	
	
}
