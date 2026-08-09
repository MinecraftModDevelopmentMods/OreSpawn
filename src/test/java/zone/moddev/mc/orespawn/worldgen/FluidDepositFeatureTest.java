package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import net.minecraft.util.ResourceLocation;
import net.minecraft.block.Blocks;

class FluidDepositFeatureTest {
	@Test
	void explicitBiomeFiltersBakeAsStaticRegistryIds() {
		JsonObject rule = new JsonObject();
		JsonArray ids = new JsonArray();
		ids.add("minecraft:cold_ocean");
		rule.add("biome_ids", ids);

		ResourceLocation expected = new ResourceLocation("minecraft", "cold_ocean");
		assertTrue(FluidDepositFeature.resolveBiomes(rule, "biome_ids", "biome_dictionary")
				.contains(expected));
	}

	@Test
	void onlySolidDryBlocksOrTheOutputFluidSealAFluidLobe() {
		assertTrue(FluidDepositFeature.isSealingState(
				Blocks.STONE.getDefaultState(), Blocks.LAVA.getDefaultState()));
		assertTrue(FluidDepositFeature.isSealingState(
				Blocks.LAVA.getDefaultState(), Blocks.LAVA.getDefaultState()));
		assertFalse(FluidDepositFeature.isSealingState(
				Blocks.AIR.getDefaultState(), Blocks.LAVA.getDefaultState()));
		assertFalse(FluidDepositFeature.isSealingState(
				Blocks.WATER.getDefaultState(), Blocks.LAVA.getDefaultState()));
	}
}
