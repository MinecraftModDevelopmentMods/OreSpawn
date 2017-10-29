package com.mcmoddev.orespawn;

import java.util.LinkedList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.OreGenEvent.GenerateMinable.EventType;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;

public class EventHandlers {
	private List<ChunkPos> chunks;
	private List<ChunkPos> retroChunks;
	
    public EventHandlers() {
    	chunks = new LinkedList<>();
    	retroChunks = new LinkedList<>();
    }

    List<EventType> vanillaEvents = Arrays.asList(EventType.ANDESITE, EventType.COAL, EventType.DIAMOND, EventType.DIORITE, EventType.DIRT, 
    		EventType.EMERALD, EventType.GOLD, EventType.GRANITE, EventType.GRAVEL, EventType.IRON, EventType.LAPIS, EventType.REDSTONE, 
    		EventType.QUARTZ, EventType.SILVERFISH);

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onGenerateMinable(OreGenEvent.GenerateMinable event) {
    	if( Config.getBoolean(Constants.REPLACE_VANILLA_OREGEN) && vanillaEvents.contains(event.getType())) {
    		event.setResult(Event.Result.DENY);
    	}
    }
    
	@SubscribeEvent
	public void onChunkSave(ChunkDataEvent.Save ev) {
		NBTTagCompound dataTag = ev.getData().getCompoundTag(Constants.CHUNK_TAG_NAME);
		NBTTagList ores = new NBTTagList();
		NBTTagList features = new NBTTagList();
		boolean retro = retroChunks.contains( new ChunkPos(ev.getChunk().x, ev.getChunk().z) );
		
		features.appendTag( new NBTTagString("orespawn:default"));
		
		List<DimensionBuilder> spawns = OreSpawn.API.getSpawns().entrySet().stream()
		.filter( ent -> ent.getValue().getAllDimensions().containsKey(ev.getWorld().provider.getDimension()))
		.map( ent -> ent.getValue().getDimension(ev.getWorld().provider.getDimension()))
		.collect(Collectors.toList());
		
		if( ev.getWorld().provider.getDimension() > 0 && ev.getWorld().provider.getDimension() != 1 ) {
			spawns.addAll(OreSpawn.API.getSpawns().entrySet().stream()
					.filter( ent -> ent.getValue().getAllDimensions().containsKey(OreSpawn.API.dimensionWildcard()))
					.map( ent -> ent.getValue().getDimension(OreSpawn.API.dimensionWildcard()))
					.collect(Collectors.toList()));
		}
		
		List<SpawnBuilder> spc = new LinkedList<>();
		List<OreBuilder> oreList = new LinkedList<>();
		spawns.stream().map( DimensionBuilder::getAllSpawns ).forEach( spc::addAll );
		spc.stream().map( SpawnBuilder::getOres ).forEach( oreList::addAll );
		
		oreList.stream()
		.map( oreEnt -> new NBTTagString( oreEnt.getOre().getBlock().getRegistryName().toString()))
		.forEach( ores::appendTag );
		
		List<FeatureBuilder> featureList = new LinkedList<>();
		
		spawns.stream()
		.map( sp -> featureList.addAll(sp.getAllSpawns().stream().map( SpawnBuilder::getFeatureGen ).collect(Collectors.toList())) );
		featureList.stream()
		.map( feat -> new NBTTagString( feat.getFeatureName() ) )
		.forEach( features::appendTag );
		
		ChunkPos chunkCoords = new ChunkPos( ev.getChunk().x, ev.getChunk().z );
		
		if( !Config.getBoolean(Constants.RETROGEN_KEY) || chunks.contains(chunkCoords) ) {
			dataTag.setTag(Constants.ORE_TAG, ores);
			dataTag.setTag(Constants.FEATURES_TAG, features);
		} 
		
		
		dataTag.setBoolean(Constants.RETRO_BEDROCK_TAG, retro);
		ev.getData().setTag(Constants.CHUNK_TAG_NAME, dataTag);
	}
	
	@SubscribeEvent
	public void onChunkLoad(ChunkDataEvent.Load ev) {
		World world = ev.getWorld();
		ChunkPos chunkCoords = new ChunkPos(ev.getChunk().x, ev.getChunk().z);
		
		doBedrockRetrogen(chunkCoords, ev.getData());
		
		if( chunks.contains(chunkCoords) ) {
			return;
		}
		
		if( Config.getBoolean(Constants.RETROGEN_KEY) ) {			
			NBTTagCompound chunkTag = ev.getData().getCompoundTag(Constants.CHUNK_TAG_NAME);
			if( featuresAreDifferent( chunkTag, world.provider.getDimension() ) || Config.getBoolean(Constants.FORCE_RETROGEN_KEY) ) {
				chunks.add(chunkCoords);
			}
		}
	}


	private boolean featuresAreDifferent(NBTTagCompound chunkTag, int dim) {
		boolean rV = ((countOres(dim) != chunkTag.getTagList(Constants.ORE_TAG, 8).tagCount()) ||
				compFeatures(chunkTag.getTagList(Constants.FEATURES_TAG, 8), dim));
		OreSpawn.LOGGER.fatal("featuresAreDifferent: %s", rV);
		
		return rV;
	}

	private boolean compFeatures(NBTTagList tagList, int dim) {
		List<DimensionBuilder> spawns = OreSpawn.API.getSpawns().entrySet().stream()
		.filter( ent -> ent.getValue().getAllDimensions().containsKey(dim) )
		.map( ent -> ent.getValue().getDimension(dim) )
		.collect(Collectors.toList());
		
		if( dim > 0 && dim != 1 ) {
			spawns.addAll(OreSpawn.API.getSpawns().entrySet().stream()
					.filter( ent -> ent.getValue().getAllDimensions().containsKey(OreSpawn.API.dimensionWildcard()))
					.map( ent -> ent.getValue().getDimension(OreSpawn.API.dimensionWildcard()))
					.collect(Collectors.toList()));
		}
		
		List<SpawnBuilder> spc = new LinkedList<>();
		spawns.stream().map( DimensionBuilder::getAllSpawns ).forEach( spc::addAll );
		
		List<FeatureBuilder> featureList = new LinkedList<>();
		
		spawns.stream()
		.map( sp -> featureList.addAll(sp.getAllSpawns().stream().map( SpawnBuilder::getFeatureGen ).collect(Collectors.toList())) );
		
		return featureList.size() == tagList.tagCount();
	}

	private void doBedrockRetrogen(ChunkPos chunkCoords, NBTTagCompound eventData) {
		if( retroChunks.contains(chunkCoords) ) return;
		if( Config.getBoolean(Constants.RETRO_BEDROCK) ) {
			NBTTagCompound chunkTag = eventData.getCompoundTag(Constants.CHUNK_TAG_NAME);
			if( chunkTag != null && (!chunkTag.hasKey( Constants.RETRO_BEDROCK_TAG ) || !chunkTag.getBoolean(Constants.RETRO_BEDROCK_TAG))) {
				// make sure we record that we've hit this chunk already
				retroChunks.add(chunkCoords);
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
	
	@SubscribeEvent
	public void worldTick(WorldTickEvent ev) {
		if( ev.side != Side.SERVER ) return;
		
		World world = ev.world;
		
		if( ev.phase == Phase.END ) {
			for( int c = 0; c < 5 && chunks.size() > 0; c++ ) {
				ChunkPos p = chunks.get(0);
				Random random = new Random(world.getSeed());
				// re-seed with something totally new :P
				random.setSeed( (((random.nextLong() >> 4 + 1) + p.x) + ((random.nextLong() >> 2 + 1) + p.z)) ^ world.getSeed() );
				ChunkProviderServer chunkProvider = (ChunkProviderServer) world.getChunkProvider();
				IChunkGenerator chunkGenerator = ObfuscationReflectionHelper.getPrivateValue(ChunkProviderServer.class, chunkProvider, "field_186029_c", "chunkGenerator");
				OreSpawn.API.getGenerator().generate(random, p.x, p.z, world, chunkGenerator, chunkProvider);
				chunks.remove(0);
			}
			
			for( int c = 0; c < 5 && retroChunks.size() > 0; c++ ) {
				OreSpawn.flatBedrock.retrogen(world, retroChunks.get(0).x, retroChunks.get(0).z);
				retroChunks.remove(0);
			}
		}
	}
}
