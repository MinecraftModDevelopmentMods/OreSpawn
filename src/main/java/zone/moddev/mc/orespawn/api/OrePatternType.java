package zone.moddev.mc.orespawn.api;

import java.util.Objects;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraftforge.fml.common.registry.IForgeRegistryEntry;

/**
 * Forge-registered ore pattern type. Its codec is evaluated once while a
 * geology profile is baked; only the resulting compiled pattern reaches the
 * generation loop.
 */
public final class OrePatternType extends IForgeRegistryEntry.Impl<OrePatternType> {
	private final Codec<?> codec;
	private final Function<Object, CompiledOrePattern> compiler;

	private <C> OrePatternType(Codec<C> codec, Function<C, CompiledOrePattern> compiler) {
		this.codec = Objects.requireNonNull(codec, "codec");
		Objects.requireNonNull(compiler, "compiler");
		this.compiler = value -> compiler.apply(cast(value));
	}

	public static <C> OrePatternType create(Codec<C> codec,
			Function<C, CompiledOrePattern> compiler) {
		return new OrePatternType(codec, compiler);
	}

	public Codec<?> codec() {
		return codec;
	}

	public CompiledOrePattern decode(JsonElement configuration) {
		DataResult<?> result = codec.parse(JsonOps.INSTANCE, configuration);
		Object value = result.result().orElseThrow(() -> new IllegalArgumentException(
				"Invalid settings for ore pattern " + getRegistryName() + ": "
						+ result.error().map(Object::toString).orElse("unknown codec error")));
		return compile(value);
	}

	private CompiledOrePattern compile(Object configuration) {
		return Objects.requireNonNull(compiler.apply(configuration), "compiled pattern");
	}

	@SuppressWarnings("unchecked")
	private static <C> C cast(Object value) {
		return (C) value;
	}
}
