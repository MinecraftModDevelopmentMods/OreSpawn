package com.mcmoddev.orespawn.worldgen;

import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.GeneratorParameters;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.oredict.OreDictionary;

public class OreSpawnWorldGen implements IWorldGenerator {

	private final Map<Integer, List<SpawnBuilder>> dimensions;
	private static final List<Block> SPAWN_BLOCKS = new ArrayList<>();

	@SuppressWarnings("unused")
	public OreSpawnWorldGen(Map<Integer, List<SpawnBuilder>> allDimensions, long nextLong) {
		this.dimensions = Collections.unmodifiableMap(allDimensions);

		if (SPAWN_BLOCKS.isEmpty()) {
			SPAWN_BLOCKS.add(Blocks.STONE);
			SPAWN_BLOCKS.add(Blocks.NETHERRACK);
			SPAWN_BLOCKS.add(Blocks.END_STONE);
			SPAWN_BLOCKS.addAll(OreDictionary.getOres("stone").stream().filter(stack -> stack.getItem() instanceof ItemBlock).map(stack -> ((ItemBlock) stack.getItem()).getBlock()).collect(Collectors.toList()));
		}
	}

	public static ImmutableList<Block> getSpawnBlocks() {
		return ImmutableList.copyOf(SPAWN_BLOCKS);
	}

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
	    IChunkProvider chunkProvider) {

		int thisDim = world.provider.getDimension();
		List<SpawnBuilder> entries = new ArrayList<> (this.dimensions.getOrDefault(thisDim, new ArrayList<> ()));

		if (!this.dimensions.getOrDefault(OreSpawn.API.dimensionWildcard(), new ArrayList<>()).isEmpty())
			entries.addAll(this.dimensions.get(OreSpawn.API.dimensionWildcard()).stream()
			    .filter(ent -> (!ent.hasExtendedDimensions() && thisDim > 0 && thisDim != 1) ||
			        ent.extendedDimensionsMatch(thisDim))
			    .collect(Collectors.toList()));

		entries.stream()
		.filter(SpawnBuilder::enabled)
		.filter(sb -> !Config.getBoolean(Constants.RETROGEN_KEY) || (sb.retrogen() || Config.getBoolean(Constants.FORCE_RETROGEN_KEY)))
		.forEach(sE -> {
			IFeature currentFeatureGen = sE.getFeatureGen().getGenerator();
			List<IBlockState> replacement = sE.getReplacementBlocks();
			replacement = replacement.isEmpty() ? ReplacementsRegistry.getDimensionDefault(thisDim) : replacement;

			GeneratorParameters parameters = new GeneratorParameters(new ChunkPos(chunkX, chunkZ), sE.getOreSpawns(), replacement, sE.getBiomes(), sE.getFeatureGen().getParameters());

			currentFeatureGen.setRandom(random);
			currentFeatureGen.generate(world, chunkGenerator, chunkProvider, parameters);
		});
	}
}


