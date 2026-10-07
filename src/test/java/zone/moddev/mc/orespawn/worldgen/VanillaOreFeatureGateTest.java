package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import zone.moddev.mc.orespawn.test.Forge36TestBootstrap;

class VanillaOreFeatureGateTest {
	@BeforeAll
	static void bootstrap() {
		Forge36TestBootstrap.registerVanilla();
	}

	@Test
	void reloadedQuartzKeepsItsPositionAndDelegateWithoutDoubleWrapping() throws Exception {
		ConfiguredFeature<?, ?> quartz = ore(Blocks.NETHER_QUARTZ_ORE);
		ConfiguredFeature<?, ?> other = ore(Blocks.BLUE_WOOL);
		List<Supplier<ConfiguredFeature<?, ?>>> features = new ArrayList<>();
		features.add(() -> other);
		features.add(() -> quartz);
		assertSame(Blocks.NETHER_QUARTZ_ORE, VanillaOreFeatureGate.nativeOutput(quartz));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(features));
		assertSame(other, features.get(0).get());
		ConfiguredFeature<?, ?> wrapper = features.get(1).get();
		assertNotSame(quartz, wrapper);
		java.lang.reflect.Field delegate = wrapper.config().getClass().getDeclaredField("delegate");
		delegate.setAccessible(true);
		assertSame(quartz, ((Supplier<?>) delegate.get(wrapper.config())).get());
		assertFalse(VanillaOreFeatureGate.wrapFeatureList(features));
	}

	@Test
	void stoneAndDeepslateCopperRemainOneManagedFamily() {
		ConfiguredFeature<?, ?> copper = ore(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
		assertSame(Blocks.COPPER_ORE, VanillaOreFeatureGate.nativeOutput(copper));
		assertSame(Blocks.COPPER_ORE, VanillaOreFeatureGate.nativeOutput(ore(Blocks.DEEPSLATE_COPPER_ORE)));
	}

	@Test
	void emeraldUsesTheTargetNativeOreConfiguration() {
		assertSame(Blocks.EMERALD_ORE, VanillaOreFeatureGate.nativeOutput(
				ore(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE)));
	}

	@Test
	void mixedAndUnknownOutputsAreNeverClaimed() {
		assertNull(VanillaOreFeatureGate.nativeOutput(ore(Blocks.COPPER_ORE, Blocks.DIAMOND_ORE)));
		assertNull(VanillaOreFeatureGate.nativeOutput(ore(Blocks.COPPER_ORE, Blocks.BLUE_WOOL)));
		assertNull(VanillaOreFeatureGate.nativeOutput(ore(Blocks.BLUE_WOOL)));
		assertNull(VanillaOreFeatureGate.nativeOutput(ore()));
	}

	@Test
	void scatteredAncientDebrisUsesTheSameFallback() {
		ConfiguredFeature<?, ?> debris = Feature.SCATTERED_ORE.configured(new OreConfiguration(
				OreConfiguration.Predicates.NETHERRACK, Blocks.ANCIENT_DEBRIS.defaultBlockState(), 3));
		assertSame(Blocks.ANCIENT_DEBRIS, VanillaOreFeatureGate.nativeOutput(debris));
	}

	private static ConfiguredFeature<?, ?> ore(Block... outputs) {
		List<OreConfiguration.TargetBlockState> targets = new ArrayList<>();
		for (Block output : outputs) targets.add(OreConfiguration.target(
				OreConfiguration.Predicates.NETHERRACK, output.defaultBlockState()));
		return Feature.ORE.configured(new OreConfiguration(targets, 14));
	}
}
