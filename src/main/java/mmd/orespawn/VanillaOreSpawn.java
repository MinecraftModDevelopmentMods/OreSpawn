package mmd.orespawn;

import com.google.common.base.Function;
import mmd.orespawn.api.OreSpawnAPI;
import mmd.orespawn.api.SpawnLogic;
import net.minecraft.block.BlockStone;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;

public class VanillaOreSpawn implements Function<OreSpawnAPI, SpawnLogic> {
    @Override
    public SpawnLogic apply(OreSpawnAPI api) {
        SpawnLogic logic = api.createSpawnLogic();

        logic.getDimension(-1)
                .addOre(Blocks.QUARTZ_ORE.getDefaultState(), 15, 4, 7, 0, 128);

        logic.getDimension(OreSpawnAPI.DIMENSION_WILDCARD)
                .addOre(Blocks.COAL_ORE.getDefaultState(), 25, 12, 20, 0, 128)
                .addOre(Blocks.IRON_ORE.getDefaultState(), 8, 4, 20, 0, 64)
                .addOre(Blocks.GOLD_ORE.getDefaultState(), 8, 2, 2, 0, 32)
                .addOre(Blocks.DIAMOND_ORE.getDefaultState(), 6, 3, 8, 0, 16)
                .addOre(Blocks.LAPIS_ORE.getDefaultState(), 5, 2, 1, 0, 32)
                .addOre(Blocks.EMERALD_ORE.getDefaultState(), 1, 0, 8, 4, 32, Biomes.EXTREME_HILLS, Biomes.EXTREME_HILLS_EDGE)
                .addOre(Blocks.DIRT.getDefaultState(), 112, 50, 10, 0, 255)
                .addOre(Blocks.GRAVEL.getDefaultState(), 112, 50, 8, 0, 255)
                .addOre(Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.GRANITE), 112, 50, 10, 0, 255)
                .addOre(Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.DIORITE), 112, 50, 10, 0, 255)
                .addOre(Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.ANDESITE), 112, 50, 10, 0, 255);

        return logic;
    }
}
