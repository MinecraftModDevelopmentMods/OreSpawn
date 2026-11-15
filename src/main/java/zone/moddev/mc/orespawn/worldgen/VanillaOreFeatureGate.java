package zone.moddev.mc.orespawn.worldgen;

import java.util.List;

import com.mojang.datafixers.Dynamic;
import com.mojang.datafixers.types.DynamicOps;

import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraftforge.registries.IForgeRegistry;

/** Runtime wrapper for Forge 28's inline vanilla ore features. */
public final class VanillaOreFeatureGate {
	private static final GateFeature GATE = new GateFeature();

	private VanillaOreFeatureGate() {
	}

	public static void registerFeatures(IForgeRegistry<Feature<?>> registry) {
		registry.register(GATE);
	}

	static void register() {
		// Minecraft 1.14 stores configured features inline. Wrap them when installing the biome lists.
	}

    static Block wrappedOutput(Feature<?> feature, IFeatureConfig config) {
        return feature == GATE && config instanceof GateConfig ? ((GateConfig) config).output : null;
    }

	static boolean wrapFeatureList(List<ConfiguredFeature<?>> features) {
		boolean changed = false;
		for (int index = 0; index < features.size(); index++) {
			ConfiguredFeature<?> original = features.get(index);
			if (original.feature == GATE) continue;
			Block output = ConfiguredFeatureInspector.firstOreOutput(original);
			if (output == null) continue;
			features.set(index, new ConfiguredFeature<>(GATE, new GateConfig(original, output)));
			changed = true;
		}
		return changed;
	}

	private static final class GateFeature extends ContextFeature<GateConfig> {
		GateFeature() {
			super(dynamic -> GateConfig.deserialize(dynamic));
			setRegistryName(new ResourceLocation(OreSpawn.MODID, "vanilla_ore_gate"));
		}

		@Override
		boolean place(FeaturePlaceContext<GateConfig> context) {
			WorldGeologyProfileManager.resolveFreshPoliciesBeforeGeneration();
			if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) return false;
            net.minecraft.util.math.ChunkPos chunk = context.decorationChunk().getPos();
            if (!OreSpawnOreGeneration.allowsVanillaOre(WorldIds.dimension(context.level()),
                    context.config().output, context.level().getSeed(), chunk.x, chunk.z)) return false;
            boolean previous = NativeStonePatchContext.enter(
                    NaturalFormations.enabledForPlacement(context.level().getWorld()), context.config().output);
            try {
                return context.config().delegate.place(context.level(), context.chunkGenerator(),
                        context.random(), context.origin());
            } finally {
                NativeStonePatchContext.restore(previous);
            }
		}
	}

	private static final class GateConfig implements IFeatureConfig {
		final ConfiguredFeature<?> delegate;
		final Block output;

		GateConfig(ConfiguredFeature<?> delegate, Block output) {
			this.delegate = delegate;
			this.output = output;
		}

		static GateConfig deserialize(Dynamic<?> ignored) {
			throw new IllegalStateException("OreSpawn 1.14 ore gates are runtime-only configured features");
		}

		@Override
		public <T> Dynamic<T> serialize(DynamicOps<T> operations) {
			return new Dynamic<>(operations, operations.emptyMap());
		}
	}
}
