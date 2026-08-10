package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.DimensionManager;

final class DimensionDiscovery {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String END = "minecraft:the_end";

	private DimensionDiscovery() {
	}

	static List<String> availableDimensionIds(GuiCreateWorld screen) {
		Set<String> result = new TreeSet<>();
		result.add(OVERWORLD);
		result.add(NETHER);
		result.add(END);

		for (Integer id : DimensionManager.getStaticDimensionIDs()) {
			if (id == 0 || id == -1 || id == 1) continue;
			result.add("legacy:dimension_" + id);
		}
		return vanillaFirst(result);
	}

	private static List<String> vanillaFirst(Set<String> ids) {
		List<String> result = new ArrayList<>();
		result.add(OVERWORLD);
		result.add(NETHER);
		result.add(END);
		ids.remove(OVERWORLD);
		ids.remove(NETHER);
		ids.remove(END);
		result.addAll(ids);
		return result;
	}

	static void addDimensionId(Set<String> target, String namespace, String path) {
		if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+")) return;
		try {
			target.add(new ResourceLocation(namespace, path).toString());
		} catch (RuntimeException ignored) {
			// Ignore malformed resource paths from third-party jars.
		}
	}
}
