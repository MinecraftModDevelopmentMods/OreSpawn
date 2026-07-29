package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

class FluidDepositFeatureTest {
	@Test
	void explicitBiomeFiltersBakeAsDynamicRegistryKeys() {
		JsonObject rule = new JsonObject();
		JsonArray ids = new JsonArray();
		ids.add("minecraft:cold_ocean");
		rule.add("biome_ids", ids);

		ResourceKey<Biome> expected = ResourceKey.create(Registries.BIOME,
				ResourceLocation.fromNamespaceAndPath("minecraft", "cold_ocean"));
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
