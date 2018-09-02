package com.mcmoddev.orespawn;

import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.OreGenEvent.GenerateMinable.EventType;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.relauncher.Side;

public class EventHandlers {

	private final Deque<ChunkPos> retroChunks;
	private final Deque<Tuple<ChunkPos, List<String>>> chunks;
	// private Map<ChunkPos, List<String>> chunks

	EventHandlers() {
		retroChunks = new ConcurrentLinkedDeque<>();
		chunks = new ConcurrentLinkedDeque<>();
	}

	private final List<EventType> vanillaEvents = Arrays.asList(EventType.ANDESITE, EventType.COAL,
			EventType.DIAMOND, EventType.DIORITE, EventType.DIRT, EventType.EMERALD, EventType.GOLD,
			EventType.GRANITE, EventType.GRAVEL, EventType.IRON, EventType.LAPIS,
			EventType.REDSTONE, EventType.QUARTZ, EventType.SILVERFISH);

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public void onGenerateMinable(final OreGenEvent.GenerateMinable event) {
		if (Config.getBoolean(Constants.REPLACE_VANILLA_OREGEN)
				&& vanillaEvents.contains(event.getType())) {
			event.setResult(Event.Result.DENY);
		}
	}

	@SubscribeEvent
	public void onChunkSave(final ChunkDataEvent.Save ev) {
		final NBTTagCompound dataTag = ev.getData().getCompoundTag(Constants.CHUNK_TAG_NAME);
		final NBTTagCompound features = new NBTTagCompound();

		// save a list of the spawns that were configured and available - in this dimension - when
		// the chunk
		// was first generated.

		// collect that data
		final int thisDimension = ev.getWorld().provider.getDimension();
		final BlockPos thisPos = new BlockPos(ev.getChunk().x + 8, 128, ev.getChunk().z + 8);
		final Biome thisBiome = ev.getChunk().getBiome(thisPos, ev.getWorld().getBiomeProvider());

		OreSpawn.API.getAllSpawns().entrySet().stream()
				.filter(ent -> ent.getValue().dimensionAllowed(thisDimension))
				.filter(ent -> ent.getValue().biomeAllowed(thisBiome)).forEach(ent -> features
						.setString(ent.getKey(), ent.getValue().getFeature().getFeatureName()));
		dataTag.setTag(Constants.FEATURES_TAG, features);

		ev.getData().setTag(Constants.CHUNK_TAG_NAME, dataTag);
	}

	private boolean dequeContains(final ChunkPos cc) {
		return chunks.stream().map(tup -> tup.getFirst().equals(cc)).collect(Collectors.toList())
				.contains(true);
	}

	@SubscribeEvent
	public void onChunkLoad(final ChunkDataEvent.Load ev) {
		final World clWorld = ev.getWorld();
		final ChunkPos chunkCoords = new ChunkPos(ev.getChunk().x, ev.getChunk().z);

		doBedrockRetrogen(chunkCoords);

		if (dequeContains(chunkCoords)) {
			return;
		}

		if (Config.getBoolean(Constants.RETROGEN_KEY)) {
			final NBTTagCompound chunkTag = ev.getData().getCompoundTag(Constants.CHUNK_TAG_NAME);
			final int thisDimension = clWorld.provider.getDimension();
			final BlockPos thisPos = new BlockPos(ev.getChunk().x + 8, 128, ev.getChunk().z + 8);
			final Biome thisBiome = ev.getChunk().getBiome(thisPos, clWorld.getBiomeProvider());

			if (featuresAreDifferent(chunkTag, thisDimension, thisBiome)
					|| Config.getBoolean(Constants.FORCE_RETROGEN_KEY)) {
				chunks.addLast(new Tuple<>(chunkCoords,
						getDifferingTags(chunkTag, thisDimension, thisBiome)));
			}
		}
	}

	private List<String> getDifferingTags(final NBTTagCompound chunkTag, final int dim,
			final Biome biome) {
		final NBTTagCompound tagList = chunkTag.getCompoundTag(Constants.FEATURES_TAG);
		final Map<String, String> currentBits = new TreeMap<>();
		final Map<String, String> oldBits = new TreeMap<>();

		OreSpawn.API.getAllSpawns().entrySet().stream()
				.filter(ent -> ent.getValue().dimensionAllowed(dim))
				.filter(ent -> ent.getValue().biomeAllowed(biome)).forEach(ent -> currentBits
						.put(ent.getKey(), ent.getValue().getFeature().getFeatureName()));

		tagList.getKeySet().stream().forEach(tag -> oldBits.put(tag, tagList.getString(tag)));

		final MapDifference<String, String> diff = Maps.difference(oldBits, currentBits);

		final List<String> stuff = Lists.newLinkedList();
		stuff.addAll(diff.entriesDiffering().entrySet().stream().map(Map.Entry::getKey)
				.collect(Collectors.toList()));
		stuff.addAll(diff.entriesOnlyOnRight().entrySet().stream().map(Map.Entry::getKey)
				.collect(Collectors.toList()));
		return ImmutableList.copyOf(stuff);
	}

	private boolean featuresAreDifferent(final NBTTagCompound chunkTag, final int dim,
			final Biome biome) {
		final NBTTagCompound tagList = chunkTag.getCompoundTag(Constants.FEATURES_TAG);
		final Map<String, String> currentBits = new TreeMap<>();
		final Map<String, String> oldBits = new TreeMap<>();

		OreSpawn.API.getAllSpawns().entrySet().stream()
				.filter(ent -> ent.getValue().dimensionAllowed(dim))
				.filter(ent -> ent.getValue().biomeAllowed(biome)).forEach(ent -> currentBits
						.put(ent.getKey(), ent.getValue().getFeature().getFeatureName()));

		tagList.getKeySet().stream().forEach(tag -> oldBits.put(tag, tagList.getString(tag)));

		final MapDifference<String, String> diff = Maps.difference(oldBits, currentBits);

		return diff.entriesDiffering().size() == 0 && diff.entriesOnlyOnLeft().size() == 0
				&& diff.entriesOnlyOnRight().size() == 0;
	}

	private void doBedrockRetrogen(final ChunkPos chunkCoords) {
		if (retroChunks.contains(chunkCoords)) {
			return;
		}

		if (Config.getBoolean(Constants.RETRO_BEDROCK)) {
			retroChunks.addLast(chunkCoords);
		}
	}

	private static World world;
	private static ChunkProviderServer chunkProvider;
	private static IChunkGenerator chunkGenerator;
	private static Random random;

	private static void setupData(final World nw) {
		if (world == null || !world.equals(nw)) {
			world = nw;
		}

		if (chunkProvider == null || !chunkProvider.equals(nw.getChunkProvider())) {
			chunkProvider = (ChunkProviderServer) nw.getChunkProvider();
		}

		if (chunkGenerator == null) {
			chunkGenerator = ObfuscationReflectionHelper.getPrivateValue(ChunkProviderServer.class,
					chunkProvider, "field_186029_c", "chunkGenerator");
		}

		if (random == null) {
			random = new Random(world.getSeed());
		}

	}

	private void runBits(final Tuple<ChunkPos, List<String>> tup) {
		final ChunkPos p = tup.getFirst();
		final List<String> spawns = tup.getSecond();

		// re-seed with something totally new :P
		random.setSeed((((random.nextLong() >> 4 + 1) + p.x) + ((random.nextLong() >> 2 + 1) + p.z))
				^ world.getSeed());
		spawns.stream().forEach(s -> OreSpawn.API.getSpawn(s).generate(random, world,
				chunkGenerator, chunkProvider, p));
	}

	@SubscribeEvent
	public void worldTick(final WorldTickEvent ev) {
		setupData(ev.world);

		if (ev.side != Side.SERVER) {
			return;
		}

		if (ev.phase == Phase.END) {
			final Deque<Tuple<ChunkPos, List<String>>> b = new LinkedList<>();
			for (int c = 0; c < 5 && !chunks.isEmpty(); c++) {
				b.push(chunks.pop());
			}

			b.forEach(this::runBits);

			for (int c = 0; c < 5 && !retroChunks.isEmpty(); c++) {
				final ChunkPos p = retroChunks.pop();
				OreSpawn.flatBedrock.retrogen(world, p.x, p.z);
			}
		}
	}
}
