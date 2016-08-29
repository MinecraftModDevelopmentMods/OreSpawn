package mmd.orespawn.impl;

import com.google.common.collect.ImmutableMap;
import mmd.orespawn.OreSpawn;
import mmd.orespawn.api.DimensionLogic;
import mmd.orespawn.api.OreSpawnAPI;
import mmd.orespawn.api.SpawnEntry;
import mmd.orespawn.api.SpawnLogic;
import mmd.orespawn.world.OreSpawnWorldGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.*;

public class OreSpawnImpl implements OreSpawnAPI {
    private final Map<String, SpawnLogic> spawnLogic = new HashMap<>();
    private final Map<String, List<OreSpawnWorldGenerator>> worldGenerators = new HashMap<>();

    @Override
    public SpawnLogic createSpawnLogic() {
        return new SpawnLogicImpl();
    }

    @Override
    public SpawnLogic getSpawnLogic(String id) {
        return this.spawnLogic.get(id);
    }

    @Override
    public Map<String, SpawnLogic> getAllSpawnLogic() {
        return ImmutableMap.copyOf(this.spawnLogic);
    }

    public void registerSpawnLogic(String id, SpawnLogic spawnLogic) {
        this.spawnLogic.put(id, spawnLogic);

        Random random = new Random();

        List<OreSpawnWorldGenerator> list = new ArrayList<>();
        for (Map.Entry<Integer, DimensionLogic> dimension : spawnLogic.getAllDimensions().entrySet()) {
            for (SpawnEntry entry : dimension.getValue().getEntries()) {
                OreSpawnWorldGenerator generator = new OreSpawnWorldGenerator(entry, dimension.getKey(), random.nextLong());
                list.add(generator);
                if (!OreSpawn.DO_RETRO_GENERATION) {
                    GameRegistry.registerWorldGenerator(generator, 100);
                }
            }
        }
        this.worldGenerators.put(id, list);

        OreSpawn.LOGGER.info("Registered spawn logic for mod " + id);
    }

    public Map<String, List<OreSpawnWorldGenerator>> getWorldGenerators() {
        return ImmutableMap.copyOf(this.worldGenerators);
    }
}
