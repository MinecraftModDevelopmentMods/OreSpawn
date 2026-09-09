package zone.moddev.mc.orespawn.api;

import java.util.Objects;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;

import net.minecraftforge.registries.IForgeRegistryEntry;

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
		try {
			return compile(LegacyCodecBridge.decode(codec, configuration));
		} catch (RuntimeException exception) {
			String detail = exception.getMessage() == null
					? exception.getClass().getSimpleName() : exception.getMessage();
			throw new IllegalArgumentException("Invalid settings for ore pattern "
					+ getRegistryName() + ": " + detail, exception);
		}
	}

	private CompiledOrePattern compile(Object configuration) {
		return Objects.requireNonNull(compiler.apply(configuration), "compiled pattern");
	}

	@SuppressWarnings("unchecked")
	private static <C> C cast(Object value) {
		return (C) value;
	}
}
