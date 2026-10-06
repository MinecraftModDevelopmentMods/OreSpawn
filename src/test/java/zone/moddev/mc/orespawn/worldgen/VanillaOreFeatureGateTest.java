package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import zone.moddev.mc.orespawn.test.Forge36TestBootstrap;

class VanillaOreFeatureGateTest {
	@BeforeAll
	static void bootstrap() {
		Forge36TestBootstrap.registerVanilla();
	}

	@Test
	void reloadedQuartzIsWrappedAtItsOriginalPositionAndKeepsItsDelegate() throws Exception {
		ConfiguredFeature<?, ?> quartz = ore(Blocks.NETHER_QUARTZ_ORE);
		List<Supplier<ConfiguredFeature<?, ?>>> features = new ArrayList<>();
		features.add(() -> quartz);
		assertSame(Blocks.NETHER_QUARTZ_ORE, VanillaOreFeatureGate.nativeOutput(quartz));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(features));
		ConfiguredFeature<?, ?> wrapper = features.get(0).get();
		assertNotSame(quartz, wrapper);
		java.lang.reflect.Field delegate = wrapper.config().getClass().getDeclaredField("delegate");
		delegate.setAccessible(true);
		assertSame(quartz, ((Supplier<?>) delegate.get(wrapper.config())).get());
		assertFalse(VanillaOreFeatureGate.wrapFeatureList(features), "Do not wrap a gate twice");
	}

	@Test
	void reloadedEmeraldUsesItsOwnFeatureType() {
		ConfiguredFeature<?, ?> emerald = Feature.EMERALD_ORE.configured(
				new net.minecraft.world.gen.feature.ReplaceBlockConfig(
						Blocks.STONE.defaultBlockState(), Blocks.EMERALD_ORE.defaultBlockState()));
		assertSame(Blocks.EMERALD_ORE, VanillaOreFeatureGate.nativeOutput(emerald));
		List<Supplier<ConfiguredFeature<?, ?>>> features = new ArrayList<>();
		features.add(() -> emerald);
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(features));
	}

	@Test
	void unregisteredNonVanillaOutputsAreNotClaimed() {
		ConfiguredFeature<?, ?> other = ore(Blocks.BLUE_WOOL);
		List<Supplier<ConfiguredFeature<?, ?>>> features = new ArrayList<>();
		features.add(() -> other);
		assertNull(VanillaOreFeatureGate.nativeOutput(other));
		assertFalse(VanillaOreFeatureGate.wrapFeatureList(features));
		assertSame(other, features.get(0).get());
	}

	private static ConfiguredFeature<?, ?> ore(net.minecraft.block.Block output) {
		return Feature.ORE.configured(new OreFeatureConfig(OreFeatureConfig.FillerBlockType.NETHERRACK,
				output.defaultBlockState(), 14));
	}
}
