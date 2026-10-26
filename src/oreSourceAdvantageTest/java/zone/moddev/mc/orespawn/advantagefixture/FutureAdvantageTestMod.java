package zone.moddev.mc.orespawn.advantagefixture;

import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.mojang.serialization.MapCodec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.WorldgenProvider;

/** Test-only stand-in for a future Advantage ore provider. */
@Mod(FutureAdvantageTestMod.MODID)
public final class FutureAdvantageTestMod {
	static final String MODID = "electricadvantage";
	private static final DeferredRegister<MapCodec<? extends Feature>> FEATURES =
			DeferredRegister.create(BuiltInRegistries.FEATURE_TYPE.key(), MODID);
	static {
		FEATURES.register("independent_marker", () -> ExternalMarkerFeature.CODEC);
	}

	public FutureAdvantageTestMod(FMLJavaModLoadingContext context) {
		BusGroup modBusGroup = context.getModBusGroup();
		FEATURES.register(modBusGroup);
		InterModEnqueueEvent.getBus(modBusGroup).addListener(this::enqueue);
	}

	private void enqueue(InterModEnqueueEvent event) {
		WorldgenProvider.Builder provider = WorldgenProvider.builder(MODID, 1);
		provider.ore(id("electricadvantage:sulfur"), id("minecraft:yellow_wool"), ore -> ore
				.material(id("orespawn:sulfur"))
				.dimension(id("minecraft:overworld"), rule -> rule
						.yRange(20, 42).attempts(20.0D).quantity(7)
						.hostBlock(id("minecraft:stone"))));
		provider.ore(id("electricadvantage:sulfur_output"), id("minecraft:green_wool"), ore -> ore
				.material(id("orespawn:sulfur"))
				.enabled(false)
				.dimension(id("minecraft:overworld"), rule -> rule
						.yRange(20, 42).attempts(20.0D).quantity(7)
						.hostBlock(id("minecraft:stone"))));
		if (!OreSpawnApi.enqueue(provider.build())) {
			throw new IllegalStateException("Advantage fixture was not sent to OreSpawn");
		}
	}

	private static Identifier id(String value) { return Identifier.parse(value); }

	private static final class ExternalMarkerFeature implements Feature {
		private static final ExternalMarkerFeature INSTANCE = new ExternalMarkerFeature();
		private static final MapCodec<ExternalMarkerFeature> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public MapCodec<ExternalMarkerFeature> codec() { return CODEC; }

		@Override
		public boolean place(WorldGenLevel world, ChunkGenerator generator,
				RandomSource random, BlockPos origin) {
			return world.setBlock(new BlockPos(origin.getX(), 60, origin.getZ()),
					Blocks.WOOL.pick(DyeColor.RED).defaultBlockState(), 2);
		}
	}
}
