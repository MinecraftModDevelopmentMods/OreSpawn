package com.mcmoddev.orespawn.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import cyano.orespawn.worldgen.OreSpawnData;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;

class Os1AbiCompatibilityTest {
	@Test
	void exposesPublishedOreSpawn110PublicDescriptors() throws Exception {
		Class<?> mod = Class.forName("cyano.orespawn.OreSpawn");
		assertField(mod, "oreSpawnConfigFiles", List.class);
		assertField(mod, "additionalStoneBlocks", List.class);
		assertField(mod, "disableVanillaOreGen", boolean.class);
		assertField(mod, "forceOreGen", boolean.class);
		assertField(mod, "ignoreNonExistant", boolean.class);
		assertField(mod, "oreSpawnFolder", Path.class);

		Class<?> data = Class.forName("cyano.orespawn.worldgen.OreSpawnData");
		assertConstructor(data, Block.class, int.class, int.class, int.class, float.class,
				int.class, int.class, Collection.class);
		assertMethod(data, "parseOreSpawnData", data, JsonObject.class);

		Class<?> spawner = Class.forName("cyano.orespawn.worldgen.OreSpawner");
		assertConstructor(spawner, Block.class, int.class, int.class, float.class,
				int.class, int.class, int.class, long.class);
		assertConstructor(spawner, Block.class, int.class, int.class, int.class, float.class,
				int.class, int.class, int.class, long.class);
		assertConstructor(spawner, Block.class, int.class, int.class, int.class, float.class,
				int.class, int.class, Collection.class, int.class, long.class);
		assertConstructor(spawner, OreSpawnData.class, Integer.class, long.class);
		assertMethod(spawner, "generate", void.class, Random.class, int.class, int.class,
				World.class, IChunkGenerator.class, IChunkProvider.class);
		assertMethod(spawner, "spawnOre", void.class, BlockPos.class, Block.class,
				int.class, int.class, World.class, Random.class);

		Class<?> worldGen = Class.forName("cyano.orespawn.init.WorldGen");
		assertMethod(worldGen, "loadConfig", void.class, Path.class);
		assertMethod(worldGen, "init", void.class);
		assertMethod(worldGen, "addOreSpawner", void.class, OreSpawnData.class, Integer.class, long.class);
		assertMethod(Class.forName("cyano.orespawn.events.OreGenDisabler"), "getInstance",
				Class.forName("cyano.orespawn.events.OreGenDisabler"));
	}

	private static void assertField(Class<?> type, String name, Class<?> expected) throws Exception {
		Field field = type.getField(name); assertEquals(expected, field.getType());
	}

	private static void assertConstructor(Class<?> type, Class<?>... arguments) throws Exception {
		Constructor<?> constructor = type.getConstructor(arguments); assertNotNull(constructor);
	}

	private static void assertMethod(Class<?> type, String name, Class<?> result,
			Class<?>... arguments) throws Exception {
		Method method = type.getMethod(name, arguments); assertEquals(result, method.getReturnType());
	}
}
