package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.util.ResourceLocation;
import net.minecraft.init.Blocks;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class FluidDepositFeatureTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void fluidWritesDoNotLeakReusablePositionsIntoForgeTickData() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "worldgen", "FluidDepositFeature.java")),
				StandardCharsets.UTF_8);
		assertTrue(source.contains("world.setBlockState(cursor.toImmutable(), deposit.output,"),
				"Forge 1.10 dynamic liquids retain their write position for scheduled ticks");
		assertTrue(source.contains("GENERATION_WRITE_FLAGS = 2;"));
		assertFalse(source.contains("chunk.setBlockState(cursor, deposit.output)"));
	}

	@Test
	void explicitBiomeFiltersBakeAsStaticRegistryIds() {
		JsonObject rule = new JsonObject();
		JsonArray ids = new JsonArray();
		ids.add(new JsonPrimitive("minecraft:cold_ocean"));
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
