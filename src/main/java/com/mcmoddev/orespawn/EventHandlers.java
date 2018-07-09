package com.mcmoddev.orespawn;

import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
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
	private Deque<ChunkPos> retroChunks;
	private Map<ChunkPos, List<String>> chunks;

	EventHandlers() {
		retroChunks = new ConcurrentLinkedDeque<>();
		chunks = new ConcurrentHashMap<>();
	}

	private List<EventType> vanillaEvents = Arrays.asList(EventType.ANDESITE, EventType.COAL, EventType.DIAMOND, EventType.DIORITE, EventType.DIRT,
	        EventType.EMERALD, EventType.GOLD, EventType.GRANITE, EventType.GRAVEL, EventType.IRON, EventType.LAPIS, EventType.REDSTONE,
	        EventType.QUARTZ, EventType.SILVERFISH);

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public void onGenerateMinable(OreGenEvent.GenerateMinable event) {
		if (Config.getBoolean(Constants.REPLACE_VANILLA_OREGEN) && vanillaEvents.contains(event.getType())) {
			event.setResult(Event.Result.DENY);
		}
	}

	@SubscribeEvent
	public void onChunkSave(ChunkDataEvent.Save ev) {
		NBTTagCompound dataTag = ev.getData().getCompoundTag(Constants.CHUNK_TAG_NAME);
		NBTTagCompound features = new NBTTagCompound();
		
		// save a list of the spawns that were configured and available - in this dimension - when the chunk
		// was first generated.
		
		// collect that data
		int thisDimension = ev.getWorld().provider.getDimension();
		BlockPos thisPos = new BlockPos( ev.getChunk().x+8, 128, ev.getChunk().z+8 );
		Biome thisBiome = ev.getChunk().getBiome(thisPos, ev.getWorld().getBiomeProvider());
		
		OreSpawn.API.getAllSpawns().entrySet().stream()
		.filter( ent -> ent.getValue().dimensionAllowed(thisDimension) )
		.filter( ent -> ent.getValue().biomeAllowed(thisBiome) )
		.forEach( ent -> features.setString(ent.getKey(), ent.getValue().getFeature().getFeatureName()) );
		dataTag.setTag(Constants.FEATURES_TAG, features);
		
		ev.getData().setTag(Constants.CHUNK_TAG_NAME, dataTag);
	}

	@SubscribeEvent
	public void onChunkLoad(ChunkDataEvent.Load ev) {
		World world = ev.getWorld();
		ChunkPos chunkCoords = new ChunkPos(ev.getChunk().x, ev.getChunk().z);

		doBedrockRetrogen(chunkCoords);

		if (chunks.containsKey(chunkCoords)) {
			return;
		}

		if (Config.getBoolean(Constants.RETROGEN_KEY)) {
			NBTTagCompound chunkTag = ev.getData().getCompoundTag(Constants.CHUNK_TAG_NAME);
			int thisDimension = world.provider.getDimension();
			BlockPos thisPos = new BlockPos( ev.getChunk().x+8, 128, ev.getChunk().z+8 );
			Biome thisBiome = ev.getChunk().getBiome(thisPos, world.getBiomeProvider());

			if (featuresAreDifferent(chunkTag, thisDimension, thisBiome) || Config.getBoolean(Constants.FORCE_RETROGEN_KEY)) {
				chunks.put(chunkCoords, getDifferingTags(chunkTag, thisDimension, thisBiome));
			}
		}
	}

	private List<String> getDifferingTags(NBTTagCompound chunkTag, int dim, Biome biome) {
		NBTTagCompound tagList = chunkTag.getCompoundTag(Constants.FEATURES_TAG);
		Map<String,String> currentBits = new TreeMap<>();
		Map<String,String> oldBits = new TreeMap<>();
		
		OreSpawn.API.getAllSpawns().entrySet().stream()
		.filter( ent -> ent.getValue().dimensionAllowed(dim) )
		.filter( ent -> ent.getValue().biomeAllowed(biome) )
		.forEach( ent -> currentBits.put(ent.getKey(), ent.getValue().getFeature().getFeatureName()) );

		tagList.getKeySet().stream()
		.forEach( tag -> oldBits.put(tag, tagList.getString(tag)));
		
		MapDifference<String,String> diff = Maps.difference(oldBits, currentBits);
		
		List<String> stuff = Lists.newLinkedList();
		stuff.addAll(diff.entriesDiffering().entrySet()
				.stream().map(ent -> ent.getKey()).collect(Collectors.toList()));
		stuff.addAll(diff.entriesOnlyOnRight().entrySet()
				.stream().map(ent -> ent.getKey()).collect(Collectors.toList()));
		return ImmutableList.copyOf(stuff);
	}

	private boolean featuresAreDifferent(NBTTagCompound chunkTag, int dim, Biome biome) {
		NBTTagCompound tagList = chunkTag.getCompoundTag(Constants.FEATURES_TAG);
		Map<String,String> currentBits = new TreeMap<>();
		Map<String,String> oldBits = new TreeMap<>();
		
		OreSpawn.API.getAllSpawns().entrySet().stream()
		.filter( ent -> ent.getValue().dimensionAllowed(dim) )
		.filter( ent -> ent.getValue().biomeAllowed(biome) )
		.forEach( ent -> currentBits.put(ent.getKey(), ent.getValue().getFeature().getFeatureName()) );

		tagList.getKeySet().stream()
		.forEach( tag -> oldBits.put(tag, tagList.getString(tag)));
		
		MapDifference<String,String> diff = Maps.difference(oldBits, currentBits);

		return diff.entriesDiffering().size() == 0 && 
				diff.entriesOnlyOnLeft().size() == 0 && 
				diff.entriesOnlyOnRight().size() == 0;
	}

	private void doBedrockRetrogen(ChunkPos chunkCoords) {
		if (retroChunks.contains(chunkCoords)) {
			return;
		}

		if (Config.getBoolean(Constants.RETRO_BEDROCK)) {
			retroChunks.addLast(chunkCoords);
		}
	}

	@SubscribeEvent
	public void worldTick(WorldTickEvent ev) {
		if (ev.side != Side.SERVER) {
			return;
		}

		World world = ev.world;
		
		if (ev.phase == Phase.END) {
			Deque<ChunkPos> keys = Queues.newArrayDeque(chunks.keySet());

			// if 'chunks' is empty, exit
			// if 'keys' is empty, exit
			// exit after 5 items, regardless
			for (int c = 0; c < 5 && !chunks.isEmpty() && !keys.isEmpty(); c++) {
				ChunkPos p = keys.pop();
				List<String> spawns = chunks.remove(p);
				
				Random random = new Random(world.getSeed());
				// re-seed with something totally new :P
				random.setSeed((((random.nextLong() >> 4 + 1) + p.x) + ((random.nextLong() >> 2 + 1) + p.z)) ^ world.getSeed());
				ChunkProviderServer chunkProvider = (ChunkProviderServer) world.getChunkProvider();
				IChunkGenerator chunkGenerator = ObfuscationReflectionHelper.getPrivateValue(ChunkProviderServer.class, chunkProvider, "field_186029_c", "chunkGenerator");
				for(String s : spawns) {
					OreSpawn.API.getSpawn(s).generate(random, world, chunkGenerator, chunkProvider, p);
				}
			}

			for (int c = 0; c < 5 && !retroChunks.isEmpty(); c++) {
				ChunkPos p = retroChunks.pop();
				OreSpawn.flatBedrock.retrogen(world, p.x, p.z);
			}
		}
	}
}
