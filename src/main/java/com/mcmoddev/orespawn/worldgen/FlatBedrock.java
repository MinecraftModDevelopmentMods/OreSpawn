package com.mcmoddev.orespawn.worldgen;

import java.util.Random;

import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;

public class FlatBedrock implements IWorldGenerator {

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
	    IChunkProvider chunkProvider) {
		// no need to do flat-bedrock on a "FLAT" world
		if (world.getWorldType() != WorldType.FLAT) {
			if (world.provider.getDimensionId() == -1) {
				genTopPlate(world, new ChunkCoordIntPair(chunkX, chunkZ), Blocks.netherrack);
				genBottomPlate(world, new ChunkCoordIntPair(chunkX, chunkZ), Blocks.netherrack);
			} else if (world.provider.getDimensionId() >= 0 && world.provider.getDimensionId() != 1) {
				genBottomPlate(world, new ChunkCoordIntPair(chunkX, chunkZ), Blocks.stone);
			}
		}
	}

	public void retrogen(World world, int chunkX, int chunkZ) {
		if (world.getWorldType() != WorldType.FLAT) {
			if (world.provider.getDimensionId() == -1) {
				genTopPlate(world, new ChunkCoordIntPair(chunkX, chunkZ), Blocks.netherrack);
				genBottomPlate(world, new ChunkCoordIntPair(chunkX, chunkZ), Blocks.netherrack);
			} else if (world.provider.getDimensionId() >= 0 && world.provider.getDimensionId() != 1) {
				genBottomPlate(world, new ChunkCoordIntPair(chunkX, chunkZ), Blocks.stone);
			}
		}
	}

	private void genBottomPlate(World world, ChunkCoordIntPair chunkPos, Block repBlock) {
		int plateThickness = Config.getInt(Constants.BEDROCK_LAYERS);

		for (int xP = 0; xP < 16; xP++) {
			for (int zP = 0; zP < 16; zP++) {
				for (int yP = 5; yP > 0; yP--) {
					BlockPos target = new BlockPos(chunkPos.chunkXPos * 16 + xP, yP, chunkPos.chunkZPos * 16 + zP);

					if (yP < plateThickness && !world.getBlockState(target).getBlock().equals(Blocks.bedrock)) {
						world.setBlockState(target, Blocks.bedrock.getDefaultState(), 26);
					} else if (yP >= plateThickness && world.getBlockState(target).getBlock().equals(Blocks.bedrock)) {
						world.setBlockState(target, repBlock.getDefaultState(), 26);
					}
				}
			}
		}
	}

	private void genTopPlate(World world, ChunkCoordIntPair chunkPos, Block repBlock) {
		int plateThickness = Config.getInt(Constants.BEDROCK_LAYERS);
		int thickness = 127 - plateThickness; // layer where the flat for the top starts

		for (int xP = 0; xP < 16; xP++) {
			for (int zP = 0; zP < 16; zP++) {
				for (int yP = 126; yP > 121; yP--) {
					BlockPos target = new BlockPos(chunkPos.chunkXPos * 16 + xP, yP, chunkPos.chunkZPos * 16 + zP);

					if (yP > thickness && !world.getBlockState(target).getBlock().equals(Blocks.bedrock)) {
						world.setBlockState(target, Blocks.bedrock.getDefaultState(), 26);
					} else if (yP <= thickness && world.getBlockState(target).getBlock().equals(Blocks.bedrock)) {
						world.setBlockState(target, repBlock.getDefaultState(), 26);
					}
				}
			}
		}
	}

}
