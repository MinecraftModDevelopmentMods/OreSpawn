package com.mcmoddev.orespawn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.Event;

public class EventHandlers {
	private List<ChunkPos> chunks;
	
    public EventHandlers() {
    	chunks = new ArrayList<>();
    }

    @SubscribeEvent
    public void onGenerateMinable(OreGenEvent.GenerateMinable event) {
    	if( Config.getBoolean(Constants.REPLACE_VANILLA_OREGEN) ) {
    		event.setResult(Event.Result.DENY);
    	}
    }
    
	@SubscribeEvent
	public void onChunkSave(ChunkDataEvent.Save ev) {
		NBTTagCompound dataTag = ev.getData().getCompoundTag(Constants.CHUNK_TAG_NAME);
		NBTTagList ores = new NBTTagList();
		NBTTagList features = new NBTTagList();
		features.appendTag( new NBTTagString("orespawn:default"));
		
		for( Entry<String, BuilderLogic> ent : OreSpawn.API.getSpawns().entrySet() ) {
			BuilderLogic log = ent.getValue();
			if( log.getAllDimensions().containsKey(ev.getWorld().provider.getDimension()) ) {
				Collection<SpawnBuilder> vals = log.getDimension(ev.getWorld().provider.getDimension()).getAllSpawns();
				for( SpawnBuilder s : vals ) {
					ores.appendTag( new NBTTagString( s.getOres().get(0).getOre().getBlock().getRegistryName().toString() ) );
				}
			}
			if( log.getAllDimensions().containsKey(OreSpawn.API.dimensionWildcard()) ) {
				Collection<SpawnBuilder> vals = log.getDimension(OreSpawn.API.dimensionWildcard()).getAllSpawns();
				for( SpawnBuilder s : vals ) {
					ores.appendTag( new NBTTagString( s.getOres().get(0).getOre().getBlock().getRegistryName().toString() ) );
				}				
			}
		}
		
		dataTag.setTag(Constants.ORE_TAG, ores);
		dataTag.setTag(Constants.FEATURES_TAG, features);
		ev.getData().setTag(Constants.CHUNK_TAG_NAME, dataTag);
	}
	
	@SubscribeEvent
	public void onChunkLoad(ChunkDataEvent.Load ev) {
		World world = ev.getWorld();
		ChunkPos chunkCoords = new ChunkPos(ev.getChunk().x, ev.getChunk().z);
		int chunkX = ev.getChunk().x;
		int chunkZ = ev.getChunk().z;
		
		if( chunks.contains(chunkCoords) ) {
			return;
		}
		
		if( Config.getBoolean(Constants.RETROGEN_KEY) ) {
            chunks.add(chunkCoords);
	        Set<IWorldGenerator> worldGens = ObfuscationReflectionHelper.getPrivateValue(GameRegistry.class, null, "worldGenerators");
			NBTTagCompound chunkTag = ev.getData().getCompoundTag(Constants.CHUNK_TAG_NAME);
			int count = chunkTag==null?0:chunkTag.getTagList(Constants.ORE_TAG, 8).tagCount();
			if( count != countOres(ev.getWorld().provider.getDimension()) ||
					Config.getBoolean(Constants.FORCE_RETROGEN_KEY)) {
                for (Iterator<IWorldGenerator> iterator = worldGens.iterator(); iterator.hasNext();)
                {
                    IWorldGenerator wg = iterator.next();
                    if( !(wg instanceof OreSpawnWorldGen) ) break;
                    OreSpawnWorldGen owg = (OreSpawnWorldGen) wg;
                    long worldSeed = world.getSeed();
                    Random fmlRandom = new Random(worldSeed);
                    long xSeed = fmlRandom.nextLong() >> 2 + 1L;
                    long zSeed = fmlRandom.nextLong() >> 2 + 1L;
                    long chunkSeed = (xSeed * chunkCoords.x + zSeed * chunkCoords.z) ^ worldSeed;

                    fmlRandom.setSeed(chunkSeed);
                    ChunkProviderServer chunkProvider = (ChunkProviderServer) world.getChunkProvider();
                    IChunkGenerator chunkGenerator = ObfuscationReflectionHelper.getPrivateValue(ChunkProviderServer.class, chunkProvider, "field_186029_c", "chunkGenerator");
                    owg.retrogen(fmlRandom, chunkX, chunkZ, world, chunkGenerator, chunkProvider);
                }
			}
		}
	}


	private int countOres(int dim) {
		int acc = 0;
		for( Entry<String, BuilderLogic> sL : OreSpawn.API.getSpawns().entrySet() ) {
			if( sL.getValue().getAllDimensions().containsKey(dim) ) {
				acc += sL.getValue().getAllDimensions().get(dim).getAllSpawns().size();
			}
			if( sL.getValue().getAllDimensions().containsKey(OreSpawn.API.dimensionWildcard()) ) {
				acc += sL.getValue().getAllDimensions().get(OreSpawn.API.dimensionWildcard()).getAllSpawns().size();
			}
		}
		return acc;
	}
	
	
}
