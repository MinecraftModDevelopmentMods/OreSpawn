package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import net.minecraft.util.registry.Registry;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.block.Blocks;

class FluidDepositFeatureTest {
	@Test
	void explicitBiomeFiltersBakeAsDynamicRegistryKeys() {
		JsonObject rule = new JsonObject();
		JsonArray ids = new JsonArray();
		ids.add("minecraft:cold_ocean");
		rule.add("biome_ids", ids);

		RegistryKey<Biome> expected = RegistryKey.create(Registry.BIOME_REGISTRY,
				new ResourceLocation("minecraft", "cold_ocean"));
		assertTrue(FluidDepositFeature.resolveBiomes(rule, "biome_ids", "biome_dictionary")
				.contains(expected));
	}

	@Test
	void onlySolidDryBlocksOrTheOutputFluidSealAFluidLobe() {
		assertTrue(FluidDepositFeature.isSealingState(
				Blocks.STONE.defaultBlockState(), Blocks.LAVA.defaultBlockState()));
		assertTrue(FluidDepositFeature.isSealingState(
				Blocks.LAVA.defaultBlockState(), Blocks.LAVA.defaultBlockState()));
		assertFalse(FluidDepositFeature.isSealingState(
				Blocks.AIR.defaultBlockState(), Blocks.LAVA.defaultBlockState()));
		assertFalse(FluidDepositFeature.isSealingState(
				Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState()));
	}
}
