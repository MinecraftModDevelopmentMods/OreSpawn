package com.mcmoddev.orespawn.init;

import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.CompiledOrePattern;
import com.mcmoddev.orespawn.api.OrePatternType;
import com.mcmoddev.orespawn.api.OrePlacementContext;
import com.mcmoddev.orespawn.api.OreSpawnPatternRegistry;
import com.mcmoddev.orespawn.api.StandardPatternSettings;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

/** Registry and allocation-free implementations of OreSpawn's built-in patterns. */
public final class OreSpawnPatterns {
	private static final DeferredRegister<OrePatternType> TYPES =
			DeferredRegister.create(OreSpawnPatternRegistry.REGISTRY_NAME, OreSpawn.MODID);
	private static final Supplier<IForgeRegistry<OrePatternType>> REGISTRY = TYPES.makeRegistry(
			() -> new RegistryBuilder<OrePatternType>().disableSaving().disableSync());

	public static final RegistryObject<OrePatternType> DEFAULT = register("default", OreSpawnPatterns::compact);
	public static final RegistryObject<OrePatternType> VEIN = register("vein", OreSpawnPatterns::vein);
	public static final RegistryObject<OrePatternType> NORMAL_CLOUD =
			register("normal_cloud", OreSpawnPatterns::cloud);
	public static final RegistryObject<OrePatternType> PRECISION =
			register("precision", OreSpawnPatterns::precision);
	public static final RegistryObject<OrePatternType> CLUSTERS =
			register("clusters", OreSpawnPatterns::clusters);
	public static final RegistryObject<OrePatternType> UNDERFLUIDS =
			register("underfluids", OreSpawnPatterns::underFluids);

	private OreSpawnPatterns() {
	}

	public static void register(IEventBus bus) {
		TYPES.register(bus);
	}

	public static IForgeRegistry<OrePatternType> registry() {
		return REGISTRY.get();
	}

	public static Supplier<IForgeRegistry<OrePatternType>> registrySupplier() {
		return REGISTRY;
	}

	public static CompiledOrePattern decode(JsonObject rule) {
		JsonObject patternObject = rule.has("pattern") && rule.get("pattern").isJsonObject()
				? rule.getAsJsonObject("pattern") : null;
		String text = patternObject == null
				? string(rule, "pattern", "vein") : string(patternObject, "type", "orespawn:vein");
		ResourceLocation id = patternId(text);
		OrePatternType type = registry().getValue(id);
		if (type == null) {
			throw new IllegalArgumentException("Unknown ore pattern type: " + id);
		}
		JsonObject settings = patternObject != null && patternObject.has("settings")
				&& patternObject.get("settings").isJsonObject()
				? patternObject.getAsJsonObject("settings") : legacySettings(rule);
		return decode(type, settings);
	}

	public static boolean isBuiltIn(JsonObject rule) {
		try {
			JsonObject pattern = rule.has("pattern") && rule.get("pattern").isJsonObject()
					? rule.getAsJsonObject("pattern") : null;
			ResourceLocation id = patternId(pattern == null
					? string(rule, "pattern", "vein") : string(pattern, "type", ""));
			return OreSpawn.MODID.equals(id.getNamespace());
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static RegistryObject<OrePatternType> register(String name,
			java.util.function.Function<StandardPatternSettings, CompiledOrePattern> compiler) {
		return TYPES.register(name, () -> OrePatternType.create(StandardPatternSettings.CODEC, compiler));
	}

	private static CompiledOrePattern decode(OrePatternType type, JsonObject settings) {
		return type.decode(new JsonParser().parse(settings.toString()));
	}

	private static JsonObject legacySettings(JsonObject rule) {
		JsonObject settings = new JsonObject();
		settings.addProperty("spread", integer(rule, "spread", 8));
		settings.addProperty("vertical_spread", integer(rule, "vertical_spread", 4));
		settings.addProperty("node_size", integer(rule, "node_size", 4));
		settings.addProperty("length", integer(rule, "length", 16));
		settings.addProperty("fluid", string(rule, "fluid", "minecraft:water"));
		return settings;
	}

	private static ResourceLocation patternId(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if ("cluster".equals(normalized)) normalized = "clusters";
		if ("cloud".equals(normalized) || "normal-cloud".equals(normalized)) normalized = "normal_cloud";
		if ("under_fluid".equals(normalized) || "under-fluid".equals(normalized)) normalized = "underfluids";
		return normalized.indexOf(':') >= 0 ? new ResourceLocation(normalized)
				: new ResourceLocation(OreSpawn.MODID, normalized);
	}

	private static CompiledOrePattern compact(StandardPatternSettings settings) {
		return context -> ConnectedOreShape.place(context, context.originX(), context.originY(),
				context.originZ(), context.quantity());
	}

	private static CompiledOrePattern precision(StandardPatternSettings settings) {
		return context -> {
			int target = context.quantity();
			int radius = Math.max(1, (int) Math.ceil(Math.cbrt(target)));
			int placed = 0;
			for (int y = -radius; y <= radius && placed < target; y++) {
				for (int x = -radius; x <= radius && placed < target; x++) {
					for (int z = -radius; z <= radius && placed < target; z++) {
						if ((x * x) + (y * y) + (z * z) <= radius * radius
								&& context.tryPlace(context.originX() + x, context.originY() + y,
										context.originZ() + z)) placed++;
					}
				}
			}
			return placed > 0;
		};
	}

	private static CompiledOrePattern cloud(StandardPatternSettings settings) {
		return context -> {
			Random random = context.random();
			int placed = 0;
			int limit = Math.min(256, context.quantity() * 4);
			for (int attempt = 0; attempt < limit && placed < context.quantity(); attempt++) {
				int x = context.originX() + triangular(random, settings.spread());
				int y = context.originY() + triangular(random, settings.verticalSpread());
				int z = context.originZ() + triangular(random, settings.spread());
				if (context.tryPlace(x, y, z)) placed++;
			}
			return placed > 0;
		};
	}

	private static CompiledOrePattern clusters(StandardPatternSettings settings) {
		return context -> {
			Random random = context.random();
			int remaining = context.quantity();
			boolean changed = false;
			while (remaining > 0) {
				int node = Math.min(settings.nodeSize(), remaining);
				changed |= ConnectedOreShape.place(context,
						context.originX() + triangular(random, settings.spread()),
						context.originY() + triangular(random, settings.verticalSpread()),
						context.originZ() + triangular(random, settings.spread()), node);
				remaining -= node;
			}
			return changed;
		};
	}

	private static CompiledOrePattern vein(StandardPatternSettings settings) {
		return context -> {
			Random random = context.random();
			double x = context.originX();
			double y = context.originY();
			double z = context.originZ();
			double yaw = random.nextDouble() * Math.PI * 2.0D;
			double pitch = (random.nextDouble() - 0.5D) * 0.35D;
			int remaining = context.quantity();
			int steps = Math.min(64, Math.max(1, settings.length()));
			boolean changed = false;
			for (int step = 0; step < steps && remaining > 0; step++) {
				int node = Math.min(settings.nodeSize(), remaining);
				changed |= ConnectedOreShape.place(context, (int) Math.round(x), (int) Math.round(y),
						(int) Math.round(z), node);
				remaining -= node;
				yaw += (random.nextDouble() - 0.5D) * 0.55D;
				pitch = Math.max(-0.65D, Math.min(0.65D,
						pitch + (random.nextDouble() - 0.5D) * 0.22D));
				x += Math.cos(yaw) * Math.cos(pitch);
				y += Math.sin(pitch);
				z += Math.sin(yaw) * Math.cos(pitch);
			}
			return changed;
		};
	}

	private static CompiledOrePattern underFluids(StandardPatternSettings settings) {
		Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(settings.fluid()));
		if (fluid == null) fluid = Fluids.WATER;
		final Fluid targetFluid = fluid;
		return context -> {
			Random random = context.random();
			for (int sample = 0; sample < 24; sample++) {
				int x = context.originX() + random.nextInt(5) - 2;
				int y = Math.max(context.minY(), Math.min(context.maxY(),
						context.originY() + random.nextInt(5) - 2));
				int z = context.originZ() + random.nextInt(5) - 2;
				if (!context.isFluid(x, y, z, targetFluid)) continue;
				while (y > context.minY() && context.isFluid(x, y - 1, z, targetFluid)) y--;
				return ConnectedOreShape.place(context, x, y - 1, z, context.quantity());
			}
			return false;
		};
	}

	private static int triangular(Random random, int radius) {
		return radius <= 0 ? 0 : random.nextInt(radius + 1) - random.nextInt(radius + 1);
	}

	private static int integer(JsonObject json, String key, int fallback) {
		try { return json.has(key) ? json.get(key).getAsInt() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static String string(JsonObject json, String key, String fallback) {
		try { return json.has(key) ? json.get(key).getAsString() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}
}
