package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.util.ResourceLocation;
import net.minecraft.init.Blocks;
import zone.moddev.mc.orespawn.test.Forge14TestBootstrap;

class FluidDepositFeatureTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge14TestBootstrap.registerVanilla();
	}

	@Test
	void dynamicFluidWritesUseForgesPositionSnapshotInsteadOfLeakingTheReusableCursor()
			throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "worldgen", "FluidDepositFeature.java")),
				StandardCharsets.UTF_8);
		assertTrue(source.contains("GENERATION_WRITE_FLAGS = 2 | 16"));
		assertTrue(source.contains("world.setBlockState(cursor, deposit.output, GENERATION_WRITE_FLAGS)"));
		assertFalse(source.contains("chunk.setBlockState(cursor, deposit.output)"));
	}

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
