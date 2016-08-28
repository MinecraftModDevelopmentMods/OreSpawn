package mmd.orespawn.impl;

import mmd.orespawn.OreSpawn;
import mmd.orespawn.api.DimensionLogic;
import mmd.orespawn.api.OreSpawnAPI;
import mmd.orespawn.api.SpawnEntry;
import mmd.orespawn.api.SpawnLogic;
import mmd.orespawn.world.OreSpawnWorldGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class OreSpawnImpl implements OreSpawnAPI {
    private final Map<String, SpawnLogic> spawnLogic = new HashMap<>();

    @Override
    public SpawnLogic createSpawnLogic() {
        return new SpawnLogicImpl();
    }

    @Override
    public SpawnLogic getSpawnLogic(String id) {
        return this.spawnLogic.get(id);
    }

    public void registerSpawnLogic(String id, SpawnLogic spawnLogic) {
        this.spawnLogic.put(id, spawnLogic);

        Random random = new Random();

        for (Map.Entry<Integer, DimensionLogic> dimension : spawnLogic.getAllDimensions().entrySet()) {
            for (SpawnEntry entry : dimension.getValue().getEntries()) {
                GameRegistry.registerWorldGenerator(new OreSpawnWorldGenerator(entry, dimension.getKey(), random.nextLong()), 100);
            }
        }

        OreSpawn.LOGGER.info("Registered spawn logic for mod " + id);
    }
}
