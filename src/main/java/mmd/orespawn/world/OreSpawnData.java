package mmd.orespawn.world;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

import java.util.HashMap;
import java.util.Map;

public class OreSpawnData extends WorldSavedData {
    public final Map<ChunkPos, NBTTagList> chunkData = new HashMap<>();

    public OreSpawnData(String tag) {
        super(tag);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        this.chunkData.clear();

        NBTTagList list = compound.getTagList("List", Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound data = list.getCompoundTagAt(i);

            int chunkX = data.getInteger("ChunkX");
            int chunkZ = data.getInteger("ChunkZ");
            NBTTagList generatedIDs = data.getTagList("GeneratedIDs", Constants.NBT.TAG_STRING);

            this.chunkData.put(new ChunkPos(chunkX, chunkZ), generatedIDs);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();

        for (Map.Entry<ChunkPos, NBTTagList> entry : this.chunkData.entrySet()) {
            NBTTagCompound data = new NBTTagCompound();

            data.setInteger("ChunkX", entry.getKey().x);
            data.setInteger("ChunkZ", entry.getKey().z);
            data.setTag("GeneratedIDs", entry.getValue());

            list.appendTag(data);
        }

        compound.setTag("List", list);

        return compound;
    }

    public static OreSpawnData getData(World world) {
        String tag = "orespawn";

        MapStorage storage = world.getPerWorldStorage();
        OreSpawnData data = (OreSpawnData) storage.getOrLoadData(OreSpawnData.class, tag);

        if (data == null) {
            data = new OreSpawnData(tag);
            storage.setData(tag, data);
        }

        return data;
    }
}
