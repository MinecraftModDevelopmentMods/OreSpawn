package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

class WorldgenBenchmarkTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void resolvesVanillaAliasesAndCustomDimensionIds() {
		assertEquals(Level.OVERWORLD, WorldgenBenchmark.benchmarkDimensionKey("overworld"));
		assertEquals(Level.NETHER, WorldgenBenchmark.benchmarkDimensionKey("NETHER"));
		assertEquals(Level.END, WorldgenBenchmark.benchmarkDimensionKey(" end "));
		assertEquals(ResourceKey.create(Registry.DIMENSION_REGISTRY,
				new ResourceLocation("test", "ordinary")),
				WorldgenBenchmark.benchmarkDimensionKey("test:ordinary"));
	}

	@Test
	void rejectsInvalidCustomDimensionIds() {
		assertThrows(IllegalArgumentException.class,
				() -> WorldgenBenchmark.benchmarkDimensionKey("not a dimension"));
	}

	@Test
	void leavesGameTestHarnessInControlOfServerShutdown() {
		assertEquals(false, WorldgenBenchmark.ownsServerShutdown(GameTestServer.class));
		assertEquals(true, WorldgenBenchmark.ownsServerShutdown(MinecraftServer.class));
	}
}
