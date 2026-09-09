package zone.moddev.mc.orespawn.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;

/**
 * Keeps the legacy public Codec descriptor while avoiding class-versus-interface
 * invocation bytecode in OreSpawn's own startup and profile decoding paths.
 */
final class LegacyCodecBridge {
	private static final Map<Object, Function<JsonElement, ?>> DECODERS = new IdentityHashMap<>();

	private LegacyCodecBridge() {
	}

	@SuppressWarnings("unchecked")
	static <A> Codec<A> create(Function<JsonElement, A> decoder) {
		if (decoder == null) throw new NullPointerException("decoder");
		Class<?> codecType = Codec.class;
		Object codec;
		try {
			if (codecType.isInterface()) {
				codec = Proxy.newProxyInstance(codecType.getClassLoader(), new Class<?>[] { codecType },
						new CodecInvocationHandler<>(decoder));
			} else {
				Method factory = codecType.getMethod("of", Function.class);
				codec = factory.invoke(null, decoder);
			}
		} catch (ReflectiveOperationException exception) {
			throw linkageFailure("create the legacy ore-pattern codec", codecType, exception);
		}
		synchronized (DECODERS) {
			DECODERS.put(codec, decoder);
		}
		return (Codec<A>) codec;
	}

	static Object decode(Object codec, JsonElement input) {
		Function<JsonElement, ?> decoder;
		synchronized (DECODERS) {
			decoder = DECODERS.get(codec);
		}
		if (decoder != null) {
			return decoder.apply(input);
		}

		try {
			ClassLoader loader = codec.getClass().getClassLoader();
			Class<?> jsonOps = Class.forName("com.mojang.serialization.JsonOps", true, loader);
			Object operations = jsonOps.getField("INSTANCE").get(null);
			Method parse = findParse(codec.getClass(), operations, input);
			Object result = parse.invoke(codec, operations, input);
			return unwrapResult(result);
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause() == null ? exception : exception.getCause();
			throw new IllegalArgumentException(message(cause), cause);
		} catch (ReflectiveOperationException exception) {
			throw linkageFailure("decode legacy ore-pattern settings", codec.getClass(), exception);
		}
	}

	private static Method findParse(Class<?> codecType, Object operations, JsonElement input)
			throws NoSuchMethodException {
		for (Method method : codecType.getMethods()) {
			if (!"parse".equals(method.getName()) || method.getParameterCount() != 2) continue;
			Class<?>[] parameters = method.getParameterTypes();
			if (parameters[0].isInstance(operations)
					&& (input == null || parameters[1].isInstance(input)
							|| parameters[1] == Object.class)) {
				return method;
			}
		}
		throw new NoSuchMethodException(codecType.getName()
				+ " has no JSON-compatible parse(operations, input) method");
	}

	private static Object unwrapResult(Object dataResult) throws ReflectiveOperationException {
		if (dataResult == null) {
			throw new IllegalArgumentException("Codec returned no result");
		}
		Object value = dataResult.getClass().getMethod("result").invoke(dataResult);
		if (value instanceof Optional && ((Optional<?>) value).isPresent()) {
			return ((Optional<?>) value).get();
		}
		Object error = dataResult.getClass().getMethod("error").invoke(dataResult);
		String detail = error instanceof Optional && ((Optional<?>) error).isPresent()
				? String.valueOf(((Optional<?>) error).get()) : "unknown codec error";
		throw new IllegalArgumentException(detail);
	}

	private static IllegalStateException linkageFailure(String action, Class<?> codecType,
			ReflectiveOperationException exception) {
		String origin = "unknown origin";
		if (codecType.getProtectionDomain() != null
				&& codecType.getProtectionDomain().getCodeSource() != null) {
			origin = String.valueOf(codecType.getProtectionDomain().getCodeSource().getLocation());
		}
		return new IllegalStateException("Cannot " + action + " using " + codecType.getName()
				+ " from " + origin, exception);
	}

	private static String message(Throwable failure) {
		String text = failure.getMessage();
		return text == null || text.trim().isEmpty() ? failure.getClass().getSimpleName() : text;
	}

	private static final class CodecInvocationHandler<A> implements InvocationHandler {
		private final Function<JsonElement, A> decoder;

		private CodecInvocationHandler(Function<JsonElement, A> decoder) {
			this.decoder = decoder;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
			if (method.getDeclaringClass() == Object.class) {
				switch (method.getName()) {
				case "toString": return "OreSpawn legacy Codec bridge";
				case "hashCode": return System.identityHashCode(proxy);
				case "equals": return proxy == arguments[0];
				default: throw new UnsupportedOperationException(method.toString());
				}
			}
			if ("parse".equals(method.getName()) && arguments != null && arguments.length == 2
					&& arguments[1] instanceof JsonElement) {
				try {
					return dataResult(method.getReturnType(), decoder.apply((JsonElement) arguments[1]), null);
				} catch (RuntimeException exception) {
					return dataResult(method.getReturnType(), null, message(exception));
				}
			}
			throw new UnsupportedOperationException("OreSpawn's legacy Codec bridge supports JSON parse only: "
					+ method);
		}
	}

	private static Object dataResult(Class<?> resultType, Object value, String error)
			throws ReflectiveOperationException {
		if (error == null) {
			return invokeStaticFactory(resultType, "success", value, null);
		}
		return invokeStaticFactory(resultType, "error", error, () -> error);
	}

	private static Object invokeStaticFactory(Class<?> type, String name, Object direct,
			Supplier<String> supplied) throws ReflectiveOperationException {
		for (Method method : type.getMethods()) {
			if (!name.equals(method.getName()) || !Modifier.isStatic(method.getModifiers())
					|| method.getParameterCount() != 1) continue;
			Class<?> parameter = method.getParameterTypes()[0];
			if (supplied != null && Supplier.class.isAssignableFrom(parameter)) {
				return method.invoke(null, supplied);
			}
			if (direct == null || parameter.isInstance(direct) || parameter == Object.class) {
				return method.invoke(null, direct);
			}
		}
		throw new NoSuchMethodException(type.getName() + "." + name + "(value)");
	}
}
