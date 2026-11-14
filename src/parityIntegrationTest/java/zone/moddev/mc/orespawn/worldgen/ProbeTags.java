package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagCollection;

/** Test-only ordering: vanilla chooses coral variants from an identity-hashed set. */
final class ProbeTags {
    private static final net.minecraft.world.gen.feature.Feature<net.minecraft.world.gen.feature.NoFeatureConfig> FOSSILS =
            new net.minecraft.world.gen.feature.Feature<net.minecraft.world.gen.feature.NoFeatureConfig>() {
                public boolean func_212245_a(net.minecraft.world.IWorld world, net.minecraft.world.gen.IChunkGenerator<? extends net.minecraft.world.gen.IChunkGenSettings> generator,
                        java.util.Random random, net.minecraft.util.math.BlockPos origin, net.minecraft.world.gen.feature.NoFeatureConfig config) {
                    // Vanilla FossilsFeature ignores its seeded feature RNG and uses an unseeded region RNG.
                    world.getRandom().setSeed(world.getSeed() ^ origin.toLong() ^ 0x6E17C073L);
                    return net.minecraft.world.gen.feature.Feature.FOSSILS.func_212245_a(world, generator, random, origin, config);
                }
            };
    private ProbeTags() { }
    static void stabilizeCorals() {
        TagCollection<Block> tags = BlockTags.getCollection();
        for (Tag<Block> tag : java.util.Arrays.asList(BlockTags.CORAL_BLOCKS, BlockTags.CORALS, BlockTags.WALL_CORALS)) {
            List<Block> blocks = new ArrayList<>(tag.getAllElements());
            blocks.sort(Comparator.comparing(block -> block.getRegistryName().toString()));
            tags.getTagMap().put(tag.getId(), Tag.Builder.<Block>create().ordered(true).addAll(blocks).build(tag.getId()));
        }
        BlockTags.setCollection(tags);
    }
    static void stabilizeFossils() {
        for (net.minecraft.world.biome.Biome biome : net.minecraftforge.registries.ForgeRegistries.BIOMES.getValues())
            for (net.minecraft.world.gen.GenerationStage.Decoration stage : net.minecraft.world.gen.GenerationStage.Decoration.values()) {
                List<net.minecraft.world.gen.feature.CompositeFeature<?, ?>> features = biome.getFeatures(stage);
                for (int index = 0; index < features.size(); index++) {
                    net.minecraft.world.gen.feature.CompositeFeature<?, ?> feature = features.get(index);
                    if (feature.getFeature() == net.minecraft.world.gen.feature.Feature.FOSSILS)
                        features.set(index, ConfiguredFeatureInspector.replaceRoot(feature, FOSSILS, ConfiguredFeatureInspector.featureConfig(feature)));
                }
            }
    }
}
