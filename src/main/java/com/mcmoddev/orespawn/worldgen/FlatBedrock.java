package com.mcmoddev.orespawn.worldgen;

import java.util.Random;

import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

public class FlatBedrock implements IWorldGenerator {

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
	    IChunkProvider chunkProvider) {
		// no need to do flat-bedrock on a "FLAT" world
		if (world.getWorldType() != WorldType.FLAT) {
			if (world.provider.getDimension() == -1) {
				genTopPlate(world, new ChunkPos(chunkX, chunkZ), Blocks.NETHERRACK);
				genBottomPlate(world, new ChunkPos(chunkX, chunkZ), Blocks.NETHERRACK);
			} else if (world.provider.getDimension() >= 0 && world.provider.getDimension() != 1) {
				genBottomPlate(world, new ChunkPos(chunkX, chunkZ), Blocks.STONE);
			}
		}
	}

	public void retrogen(World world, int chunkX, int chunkZ) {
		if (world.getWorldType() != WorldType.FLAT) {
			if (world.provider.getDimension() == -1) {
				genTopPlate(world, new ChunkPos(chunkX, chunkZ), Blocks.NETHERRACK);
				genBottomPlate(world, new ChunkPos(chunkX, chunkZ), Blocks.NETHERRACK);
			} else if (world.provider.getDimension() >= 0 && world.provider.getDimension() != 1) {
				genBottomPlate(world, new ChunkPos(chunkX, chunkZ), Blocks.STONE);
			}
		}
	}

	private void genBottomPlate(World world, ChunkPos chunkPos, Block repBlock) {
		int plateThickness = Config.getInt(Constants.BEDROCK_LAYERS);

		for (int xP = 0; xP < 16; xP++) {
			for (int zP = 0; zP < 16; zP++) {
				for (int yP = 5; yP > 0; yP--) {
					BlockPos target = new BlockPos(chunkPos.x * 16 + xP, yP, chunkPos.z * 16 + zP);

					if (yP < plateThickness && !world.getBlockState(target).getBlock().equals(Blocks.BEDROCK)) {
						world.setBlockState(target, Blocks.BEDROCK.getDefaultState(), 26);
					} else if (yP >= plateThickness && world.getBlockState(target).getBlock().equals(Blocks.BEDROCK)) {
						world.setBlockState(target, repBlock.getDefaultState(), 26);
					}
				}
			}
		}
	}

	private void genTopPlate(World world, ChunkPos chunkPos, Block repBlock) {
		int plateThickness = Config.getInt(Constants.BEDROCK_LAYERS);
		int thickness = 127 - plateThickness; // layer where the flat for the top starts

		for (int xP = 0; xP < 16; xP++) {
			for (int zP = 0; zP < 16; zP++) {
				for (int yP = 126; yP > 121; yP--) {
					BlockPos target = new BlockPos(chunkPos.x * 16 + xP, yP, chunkPos.z * 16 + zP);

					if (yP > thickness && !world.getBlockState(target).getBlock().equals(Blocks.BEDROCK)) {
						world.setBlockState(target, Blocks.BEDROCK.getDefaultState(), 26);
					} else if (yP <= thickness && world.getBlockState(target).getBlock().equals(Blocks.BEDROCK)) {
						world.setBlockState(target, repBlock.getDefaultState(), 26);
					}
				}
			}
		}
	}

}
