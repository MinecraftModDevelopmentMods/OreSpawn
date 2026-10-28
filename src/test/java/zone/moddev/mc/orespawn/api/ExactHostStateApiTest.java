package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import net.minecraft.util.ResourceLocation;

class ExactHostStateApiTest {

	@Test
	void publicBuilderCanSelectPlainStoneWithoutItsIgneousVariants() throws Exception {
		WorldgenProvider.OreDimensionDefinition.Builder builder =
				WorldgenProvider.OreDimensionDefinition.builder(new ResourceLocation("minecraft:overworld"));
		Method hostState = builder.getClass().getMethod("hostState", ResourceLocation.class, int.class);
		hostState.invoke(builder, new ResourceLocation("minecraft:stone"), 0);

		assertEquals("[{\"block\":\"minecraft:stone\",\"metadata\":0}]",
				builder.build().toJson().getAsJsonArray("host_blocks").toString());
	}

	@Test
	void exactHostsAreImmutableWeightedAndDoNotChangeOldBlockSelectors() {
		ResourceLocation stone = new ResourceLocation("minecraft:stone");
		WorldgenProvider.OreDimensionDefinition.Builder builder =
				WorldgenProvider.OreDimensionDefinition.builder(new ResourceLocation("minecraft:overworld"));
		String original = builder.hostBlock(stone).build().toJson().toString();
		WorldgenProvider.OreDimensionDefinition exact = builder.hostState(stone, 0, 0.5D).build();

		assertEquals(0.5D, exact.hostStates().get(stone).get(0));
		assertThrows(UnsupportedOperationException.class, () -> exact.hostStates().clear());
		assertThrows(UnsupportedOperationException.class, () -> exact.hostStates().get(stone).put(1, 1.0D));
		builder.hostState(stone, 1);
		assertEquals(1, exact.hostStates().get(stone).size());
		assertEquals(original, WorldgenProvider.OreDimensionDefinition
				.builder(new ResourceLocation("minecraft:overworld")).hostBlock(stone).build().toJson().toString());
		assertThrows(IllegalArgumentException.class, () -> builder.hostState(stone, -1));
		assertThrows(IllegalArgumentException.class, () -> builder.hostState(stone, 16));
		assertThrows(NullPointerException.class, () -> builder.hostState(null, 0));
	}

	@Test
	void rejectedWeightsDoNotBypassTheNoHostCheck() {
		assertAll("invalid weights", () -> rejectWithoutAddingHost(-0.1D),
				() -> rejectWithoutAddingHost(1.1D), () -> rejectWithoutAddingHost(Double.NaN),
				() -> rejectWithoutAddingHost(Double.POSITIVE_INFINITY),
				() -> rejectWithoutAddingHost(Double.NEGATIVE_INFINITY));
	}

	private static void rejectWithoutAddingHost(double weight) {
		WorldgenProvider.OreDimensionDefinition.Builder builder =
				WorldgenProvider.OreDimensionDefinition.builder(new ResourceLocation("minecraft:overworld"));
		assertThrows(IllegalArgumentException.class,
				() -> builder.hostState(new ResourceLocation("minecraft:stone"), 0, weight));
		assertThrows(IllegalStateException.class, builder::build,
				"A rejected host must not make an empty rule valid: " + weight);
	}

	@Test
	void rejectedWeightsLeaveExistingHostsUnchanged() {
		ResourceLocation stone = new ResourceLocation("minecraft:stone");
		ResourceLocation dirt = new ResourceLocation("minecraft:dirt");
		WorldgenProvider.OreDimensionDefinition.Builder builder =
				WorldgenProvider.OreDimensionDefinition.builder(new ResourceLocation("minecraft:overworld"))
						.hostState(stone, 0, 0.5D);
		String original = builder.build().toJson().toString();
		assertThrows(IllegalArgumentException.class, () -> builder.hostState(dirt, 0, Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> builder.hostState(stone, 0, 2.0D));
		WorldgenProvider.OreDimensionDefinition result = builder.build();
		assertFalse(result.hostStates().containsKey(dirt));
		assertEquals(0.5D, result.hostStates().get(stone).get(0));
		assertEquals(original, result.toJson().toString());
	}

	@Test
	void endpointsAndSeveralVariantsSerializeInInsertionOrder() {
		WorldgenProvider.OreDimensionDefinition value =
				WorldgenProvider.OreDimensionDefinition.builder(new ResourceLocation("minecraft:overworld"))
						.hostState(new ResourceLocation("minecraft:stone"), 0, 0.0D)
						.hostState(new ResourceLocation("minecraft:stone"), 1, 0.5D)
						.hostState(new ResourceLocation("minecraft:stone"), 6, 1.0D).build();
		assertEquals("[{\"block\":\"minecraft:stone\",\"metadata\":0,\"weight\":0.0},"
				+ "{\"block\":\"minecraft:stone\",\"metadata\":1,\"weight\":0.5},"
				+ "{\"block\":\"minecraft:stone\",\"metadata\":6}]",
				value.toJson().getAsJsonArray("host_blocks").toString());
	}
}
