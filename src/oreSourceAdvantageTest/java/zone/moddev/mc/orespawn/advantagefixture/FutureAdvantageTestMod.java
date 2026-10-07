package zone.moddev.mc.orespawn.advantagefixture;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneDecoratorConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.FeatureDecorator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fmllegacy.RegistryObject;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.WorldgenProvider;

/** Test-only stand-in for a future Advantage ore provider. */
@Mod(FutureAdvantageTestMod.MODID)
public final class FutureAdvantageTestMod {
	static final String MODID = "electricadvantage";
	private static final DeferredRegister<Feature<?>> FEATURES =
			DeferredRegister.create(ForgeRegistries.FEATURES, MODID);
	private static final RegistryObject<ExternalMarkerFeature> EXTERNAL_FEATURE =
			FEATURES.register("independent_marker", ExternalMarkerFeature::new);
	private static ConfiguredFeature<?, ?> externalConfigured;

	public FutureAdvantageTestMod() {
		FEATURES.register(FMLJavaModLoadingContext.get().getModEventBus());
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueue);
		MinecraftForge.EVENT_BUS.addListener(this::addExternalGenerator);
	}

	private void setup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> externalConfigured = Registry.register(
				BuiltinRegistries.CONFIGURED_FEATURE,
				new ResourceLocation(MODID, "independent_marker"),
				EXTERNAL_FEATURE.get().configured(NoneFeatureConfiguration.INSTANCE)
						.decorated(FeatureDecorator.NOPE.configured(NoneDecoratorConfiguration.INSTANCE))));
	}

	private void addExternalGenerator(BiomeLoadingEvent event) {
		if (event.getName() == null || !"minecraft".equals(event.getName().getNamespace())) return;
		if (externalConfigured == null) throw new IllegalStateException("External fixture feature was not registered");
		event.getGeneration().getFeatures(GenerationStep.Decoration.UNDERGROUND_ORES)
				.add(() -> externalConfigured);
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

	private static ResourceLocation id(String value) { return new ResourceLocation(value); }

	private static final class ExternalMarkerFeature extends Feature<NoneFeatureConfiguration> {
		private ExternalMarkerFeature() { super(NoneFeatureConfiguration.CODEC); }

		@Override
		public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
			BlockPos origin = context.origin();
			return context.level().setBlock(new BlockPos(origin.getX(), 60, origin.getZ()),
					Blocks.RED_WOOL.defaultBlockState(), 2);
		}
	}
}
