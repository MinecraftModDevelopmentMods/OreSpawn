package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Random;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import zone.moddev.mc.orespawn.api.WorldgenProvider;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class ExactHostStateBakeTest {
	@Test
	void providerStatesReachTheExistingMatcherWithoutAcceptingSiblingVariants() throws Exception {
		Forge12TestBootstrap.registerVanilla();
		if (zone.moddev.mc.orespawn.init.OreSpawnPatterns.registry()
				.getValue(new ResourceLocation("orespawn:default")) == null) {
			zone.moddev.mc.orespawn.init.OreSpawnPatterns.registerTypes(new net.minecraftforge.event.RegistryEvent.Register<>(
					zone.moddev.mc.orespawn.api.OreSpawnPatternRegistry.REGISTRY_NAME,
					zone.moddev.mc.orespawn.init.OreSpawnPatterns.registry()));
		}
		JsonObject provider = WorldgenProvider.builder("coaltest", 1)
				.ore(new ResourceLocation("coaltest:coal"), new ResourceLocation("minecraft:coal_ore"),
						ore -> ore.dimension(WorldIds.OVERWORLD, placement -> placement
								.hostState(new ResourceLocation("minecraft:stone"), 0)))
				.build().toJson();
		provider.addProperty("manage_vanilla_ores", true);
		String before = provider.toString();

		Method geology = GeomeConfig.class.getDeclaredMethod("bake", JsonObject.class, ResourceLocation.class);
		geology.setAccessible(true);
		BakedGeomeConfig config = (BakedGeomeConfig) geology.invoke(null, new JsonObject(), WorldIds.OVERWORLD);
		Method bake = OreSpawnOreGeneration.class.getDeclaredMethod("bakeOres", JsonObject.class, BakedGeomeConfig.class);
		bake.setAccessible(true);
		Object baked = bake.invoke(null, provider, config);
		Field byDimension = baked.getClass().getDeclaredField("byDimension");
		byDimension.setAccessible(true);
		Object[] rules = (Object[]) ((Map<?, ?>) byDimension.get(baked)).get(WorldIds.OVERWORLD);
		assertEquals(1, rules.length);
		Method accepts = rules[0].getClass().getDeclaredMethod("accepts", IBlockState.class, Random.class, BakedGeomeConfig.class);
		accepts.setAccessible(true);
		for (int metadata = 0; metadata <= 6; metadata++) {
			assertEquals(metadata == 0, accepts.invoke(rules[0], Blocks.STONE.getStateFromMeta(metadata), new Random(1), config));
		}
		assertEquals(before, provider.toString());
	}
}
