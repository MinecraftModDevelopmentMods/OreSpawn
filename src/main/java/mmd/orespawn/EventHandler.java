package mmd.orespawn;

import mmd.orespawn.world.OreSpawnData;
import mmd.orespawn.world.OreSpawnWorldGenerator;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public enum EventHandler {
    INSTANCE;

    //@SubscribeEvent
    public void onChunkLoad(ChunkDataEvent.Load event) {
        if (OreSpawn.DO_RETRO_GENERATION) {
            WorldServer world = (WorldServer) event.getWorld();
            Chunk chunk = event.getChunk();

            this.generateChunk(world, chunk);
        }
    }

    //@SubscribeEvent
    public void onPopulateChunk(PopulateChunkEvent event) {
        if (OreSpawn.DO_RETRO_GENERATION) {
            WorldServer world = (WorldServer) event.getWorld();
            Chunk chunk = world.getChunkFromChunkCoords(event.getChunkX(), event.getChunkZ());

            this.generateChunk(world, chunk);
        }
    }

    public void generateChunk(WorldServer world, Chunk chunk) {
        OreSpawnData data = OreSpawnData.getData(world);
        NBTTagList dataList = data.chunkData.get(chunk.getPos());
        List<String> generatedIDs = new ArrayList<>();

        if (dataList == null) {
            dataList = new NBTTagList();
        }

        for (int i = 0; i < dataList.tagCount(); i++) {
            generatedIDs.add(dataList.getStringTagAt(i));
        }

        for (Map.Entry<String, List<OreSpawnWorldGenerator>> entry : OreSpawn.API.getWorldGenerators().entrySet()) {
            if (!generatedIDs.contains(entry.getKey())) {
                long worldSeed = world.getSeed();
                Random random = new Random(worldSeed);
                long xSeed = random.nextLong() >> 2 + 1L;
                long zSeed = random.nextLong() >> 2 + 1L;
                long chunkSeed = (xSeed * chunk.xPosition + zSeed * chunk.zPosition) ^ worldSeed;

                ChunkProviderServer chunkProvider = world.getChunkProvider();
                IChunkGenerator chunkGenerator = chunkProvider.chunkGenerator;

                for (OreSpawnWorldGenerator generator : entry.getValue()) {
                    random.setSeed(chunkSeed);
                    generator.generate(random, chunk.xPosition, chunk.zPosition, world, chunkGenerator, chunkProvider);
                }

                dataList.appendTag(new NBTTagString(entry.getKey()));
            }
        }

        data.chunkData.put(chunk.getPos(), dataList);
        data.markDirty();
    }

    @SubscribeEvent
    public void onGenerateMinable(OreGenEvent.GenerateMinable event) {
        event.setResult(Event.Result.DENY);
    }
}
