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
            new net.minecraft.world.gen.feature.Feature<net.minecraft.world.gen.feature.NoFeatureConfig>(net.minecraft.world.gen.feature.NoFeatureConfig::deserialize) {
                public boolean place(net.minecraft.world.IWorld world, net.minecraft.world.gen.ChunkGenerator<? extends net.minecraft.world.gen.GenerationSettings> generator,
                        java.util.Random random, net.minecraft.util.math.BlockPos origin, net.minecraft.world.gen.feature.NoFeatureConfig config) {
                    // Vanilla FossilsFeature ignores its seeded feature RNG and uses an unseeded region RNG.
                    world.getRandom().setSeed(world.getSeed() ^ origin.toLong() ^ 0x6E17C073L);
                    return net.minecraft.world.gen.feature.Feature.FOSSIL.place(world, generator, random, origin, config);
                }
            };
    private ProbeTags() { }
    static void stabilizeCorals() {
        TagCollection<Block> tags = BlockTags.getCollection();
        java.util.Map<net.minecraft.util.ResourceLocation, Tag.Builder<Block>> builders = new java.util.LinkedHashMap<>();
        tags.getTagMap().forEach((id, tag) -> builders.put(id, Tag.Builder.<Block>create().add(tag.getAllElements().toArray(new Block[0]))));
        for (Tag<Block> tag : java.util.Arrays.asList(BlockTags.CORAL_BLOCKS, BlockTags.CORALS, BlockTags.WALL_CORALS)) {
            List<Block> blocks = new ArrayList<>(tag.getAllElements());
            blocks.sort(Comparator.comparing(block -> block.getRegistryName().toString()));
            builders.put(tag.getId(), Tag.Builder.<Block>create().ordered(true).add(blocks.toArray(new Block[0])));
        }
        tags.registerAll(builders);
        BlockTags.setCollection(tags);
    }
    static void stabilizeFossils() {
        for (net.minecraft.world.biome.Biome biome : net.minecraftforge.registries.ForgeRegistries.BIOMES.getValues())
            for (net.minecraft.world.gen.GenerationStage.Decoration stage : net.minecraft.world.gen.GenerationStage.Decoration.values()) {
                List<net.minecraft.world.gen.feature.ConfiguredFeature<?, ?>> features = biome.getFeatures(stage);
                for (int index = 0; index < features.size(); index++) {
                    net.minecraft.world.gen.feature.ConfiguredFeature<?, ?> feature = features.get(index);
                    features.set(index, stabilize(feature));
                }
            }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static net.minecraft.world.gen.feature.ConfiguredFeature<?, ?> stabilize(net.minecraft.world.gen.feature.ConfiguredFeature<?, ?> feature) {
        if (feature.feature == net.minecraft.world.gen.feature.Feature.FOSSIL)
            return new net.minecraft.world.gen.feature.ConfiguredFeature<>(FOSSILS, new net.minecraft.world.gen.feature.NoFeatureConfig());
        if (feature.config instanceof net.minecraft.world.gen.feature.DecoratedFeatureConfig) {
            net.minecraft.world.gen.feature.DecoratedFeatureConfig config = (net.minecraft.world.gen.feature.DecoratedFeatureConfig) feature.config;
            net.minecraft.world.gen.feature.ConfiguredFeature<?, ?> child = stabilize(config.feature);
            if (child != config.feature) return new net.minecraft.world.gen.feature.ConfiguredFeature((net.minecraft.world.gen.feature.Feature) feature.feature,
                    new net.minecraft.world.gen.feature.DecoratedFeatureConfig(child, config.decorator));
        }
        return feature;
    }
}
