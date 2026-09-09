package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import zone.moddev.mc.orespawn.api.CompiledOrePattern;
import zone.moddev.mc.orespawn.api.OrePatternType;
import zone.moddev.mc.orespawn.api.StandardPatternSettings;

class LegacyCodecLinkageTest {
	private static final String LEGACY_CONSUMER_SHA256 =
			"BA3AECFB53AAA31B7FB24CD6F7A8376EF11185F61425024A9543FA2D10220564";

	@TempDir
	Path temporaryDirectory;

	@Test
	void bundledClassCodecStillDecodesAndKeepsItsPublicDescriptors() throws Exception {
		assertFalse(Codec.class.isInterface());
		DataResult<StandardPatternSettings> parsed = StandardPatternSettings.CODEC.parse(
				JsonOps.INSTANCE, json("{\"spread\":17,\"length\":21}"));
		assertEquals(17, parsed.result().get().spread());
		assertEquals(21, parsed.result().get().length());

		Field codecField = StandardPatternSettings.class.getField("CODEC");
		assertEquals(Codec.class, codecField.getType());
		Method create = OrePatternType.class.getMethod("create", Codec.class, Function.class);
		assertEquals(OrePatternType.class, create.getReturnType());
		assertEquals(Codec.class, OrePatternType.class.getMethod("codec").getReturnType());
	}

	@Test
	void builtInPatternDecodeUsesTheBridgeAndReportsInvalidSettings() {
		AtomicReference<StandardPatternSettings> decoded = new AtomicReference<>();
		OrePatternType type = OrePatternType.create(StandardPatternSettings.CODEC, settings -> {
			decoded.set(settings);
			return context -> false;
		});
		CompiledOrePattern pattern = type.decode(json(
				"{\"spread\":19,\"vertical_spread\":7,\"node_size\":5,\"length\":23}"));
		assertNotNull(pattern);
		assertEquals(19, decoded.get().spread());
		assertEquals(7, decoded.get().verticalSpread());
		assertEquals(5, decoded.get().nodeSize());
		assertEquals(23, decoded.get().length());

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> type.decode(json("{\"spread\":\"not-a-number\"}")));
		assertTrue(error.getMessage().contains("Invalid settings for ore pattern"));
		assertTrue(error.getMessage().contains("not-a-number"));
	}

	@Test
	void interfaceFirstRuntimeInitializesAndDecodesWithoutLinkageErrors() throws Exception {
		Path classes = compileInterfaceFixture(true);
		ProcessResult result = run(classes, "probe.InterfaceCodecProbe");
		assertEquals(0, result.exitCode, result.output);
		assertTrue(result.output.contains("CODEC_INTERFACE=true"), result.output);
		assertTrue(result.output.contains("DIRECT_CODEC_PARSE_OK"), result.output);
		assertTrue(result.output.contains("ORE_PATTERN_DECODE_OK"), result.output);
		assertFalse(result.output.contains("IncompatibleClassChangeError"), result.output);
	}

	@Test
	void consumerCompiledAgainstFourZeroSixteenRunsUnchangedWithInterfaceCodec() throws Exception {
		Path classes = compileInterfaceFixture(false);
		Path consumer = classes.resolve("probe/LegacyApiConsumer.class");
		Files.createDirectories(consumer.getParent());
		try (InputStream input = getClass().getResourceAsStream(
				"/codec-conflict/probe/LegacyApiConsumer.class")) {
			assertNotNull(input, "missing sealed 4.0.16 API consumer");
			Files.copy(input, consumer);
		}
		assertEquals(LEGACY_CONSUMER_SHA256, sha256(consumer));

		ProcessResult result = run(classes, "probe.LegacyApiConsumer");
		assertEquals(0, result.exitCode, result.output);
		assertTrue(result.output.contains("LEGACY_4_0_16_API_CONSUMER_OK"), result.output);
		assertFalse(result.output.contains("LinkageError"), result.output);
	}

	@Test
	void startupClassesContainNoDirectSerializationMethodReferences() throws Exception {
		for (Class<?> type : Arrays.asList(StandardPatternSettings.class, OrePatternType.class,
				Class.forName("zone.moddev.mc.orespawn.api.LegacyCodecBridge"))) {
			List<String> forbidden = new ArrayList<>();
			for (MethodReference reference : methodReferences(type)) {
				if (reference.owner.startsWith("com/mojang/serialization/")) {
					forbidden.add(reference.owner + "." + reference.name);
				}
			}
			assertTrue(forbidden.isEmpty(),
					type.getName() + " directly invokes a classpath-sensitive serialization method: "
							+ forbidden);
		}
	}

	private Path compileInterfaceFixture(boolean includeProbe) throws Exception {
		Path sources = temporaryDirectory.resolve(includeProbe ? "interface-probe-src" : "interface-src");
		Path classes = temporaryDirectory.resolve(includeProbe ? "interface-probe-classes" : "interface-classes");
		write(sources.resolve("com/mojang/serialization/DynamicOps.java"),
				"package com.mojang.serialization; public interface DynamicOps<T> {}\n");
		write(sources.resolve("com/mojang/serialization/JsonOps.java"),
				"package com.mojang.serialization;"
				+ " import com.google.gson.JsonElement;"
				+ " public final class JsonOps implements DynamicOps<JsonElement> {"
				+ " public static final JsonOps INSTANCE = new JsonOps(); private JsonOps() {} }\n");
		write(sources.resolve("com/mojang/serialization/DataResult.java"),
				"package com.mojang.serialization;"
				+ " import java.util.Optional; import java.util.function.Supplier;"
				+ " public final class DataResult<A> { private final A value; private final String failure;"
				+ " private DataResult(A value,String failure){this.value=value;this.failure=failure;}"
				+ " public static <A> DataResult<A> success(A value){return new DataResult<A>(value,null);}"
				+ " public static <A> DataResult<A> error(Supplier<String> error){"
				+ " return new DataResult<A>(null,error.get());}"
				+ " public Optional<A> result(){return Optional.ofNullable(value);}"
				+ " public Optional<String> error(){return Optional.ofNullable(failure);} }\n");
		write(sources.resolve("com/mojang/serialization/Codec.java"),
				"package com.mojang.serialization; public interface Codec<A> {"
				+ " <T> DataResult<A> parse(DynamicOps<T> operations,T input); }\n");
		if (includeProbe) {
			write(sources.resolve("probe/InterfaceCodecProbe.java"), interfaceProbeSource());
		}

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "tests require a JDK, not a JRE");
		Files.createDirectories(classes);
		List<File> javaSources = new ArrayList<>();
		Files.walk(sources).filter(path -> path.toString().endsWith(".java"))
				.forEach(path -> javaSources.add(path.toFile()));
		try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null,
				StandardCharsets.UTF_8)) {
			List<String> options = Arrays.asList("-source", "8", "-target", "8", "-proc:none",
					"-classpath", System.getProperty("java.class.path"), "-d", classes.toString());
			Boolean compiled = compiler.getTask(null, manager, null, options, null,
					manager.getJavaFileObjectsFromFiles(javaSources)).call();
			assertEquals(Boolean.TRUE, compiled, "could not compile interface-first fixture");
		}
		return classes;
	}

	private static String interfaceProbeSource() {
		return "package probe;"
				+ " import java.util.concurrent.atomic.AtomicReference;"
				+ " import com.google.gson.JsonElement; import com.google.gson.JsonParser;"
				+ " import com.mojang.serialization.Codec; import com.mojang.serialization.DataResult;"
				+ " import com.mojang.serialization.JsonOps;"
				+ " import zone.moddev.mc.orespawn.api.CompiledOrePattern;"
				+ " import zone.moddev.mc.orespawn.api.OrePatternType;"
				+ " import zone.moddev.mc.orespawn.api.StandardPatternSettings;"
				+ " public final class InterfaceCodecProbe { public static void main(String[] args) {"
				+ " System.out.println(\"CODEC_INTERFACE=\"+Codec.class.isInterface());"
				+ " JsonElement json=new JsonParser().parse(\"{\\\"spread\\\":29,\\\"length\\\":31}\");"
				+ " DataResult<StandardPatternSettings> direct=StandardPatternSettings.CODEC.parse(JsonOps.INSTANCE,json);"
				+ " if(!direct.result().isPresent()||direct.result().get().spread()!=29)"
				+ " throw new AssertionError(\"direct proxy parse failed\");"
				+ " System.out.println(\"DIRECT_CODEC_PARSE_OK\");"
				+ " AtomicReference<StandardPatternSettings> decoded=new AtomicReference<StandardPatternSettings>();"
				+ " OrePatternType type=OrePatternType.create(StandardPatternSettings.CODEC,s->{decoded.set(s);return c->false;});"
				+ " CompiledOrePattern pattern=type.decode(json);"
				+ " if(pattern==null||decoded.get()==null||decoded.get().length()!=31)"
				+ " throw new AssertionError(\"OrePatternType bridge decode failed\");"
				+ " System.out.println(\"ORE_PATTERN_DECODE_OK\"); } }\n";
	}

	private static ProcessResult run(Path firstClasspathEntry, String mainClass) throws Exception {
		Path java = new File(System.getProperty("java.home"), "bin/java.exe").toPath();
		if (!Files.isRegularFile(java)) java = new File(System.getProperty("java.home"), "bin/java").toPath();
		String classpath = firstClasspathEntry + File.pathSeparator + System.getProperty("java.class.path");
		Process process = new ProcessBuilder(java.toString(), "-cp", classpath, mainClass)
				.redirectErrorStream(true).start();
		boolean finished = process.waitFor(30, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			throw new AssertionError(mainClass + " did not finish within 30 seconds");
		}
		return new ProcessResult(process.exitValue(), read(process.getInputStream()));
	}

	private static JsonElement json(String text) {
		return new JsonParser().parse(text);
	}

	private static void write(Path file, String contents) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
	}

	private static String read(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		for (int count = input.read(buffer); count >= 0; count = input.read(buffer)) {
			if (count > 0) output.write(buffer, 0, count);
		}
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}

	private static String sha256(Path file) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream input = Files.newInputStream(file)) {
			byte[] buffer = new byte[4096];
			for (int count = input.read(buffer); count >= 0; count = input.read(buffer)) {
				if (count > 0) digest.update(buffer, 0, count);
			}
		}
		StringBuilder result = new StringBuilder();
		for (byte value : digest.digest()) result.append(String.format("%02X", value & 0xff));
		return result.toString();
	}

	private static List<MethodReference> methodReferences(Class<?> type) throws Exception {
		String resource = "/" + type.getName().replace('.', '/') + ".class";
		try (DataInputStream input = new DataInputStream(type.getResourceAsStream(resource))) {
			assertEquals(0xCAFEBABE, input.readInt());
			input.readUnsignedShort();
			input.readUnsignedShort();
			int count = input.readUnsignedShort();
			int[] tags = new int[count];
			Object[] values = new Object[count];
			for (int index = 1; index < count; index++) {
				int tag = input.readUnsignedByte();
				tags[index] = tag;
				switch (tag) {
				case 1: values[index] = input.readUTF(); break;
				case 3: case 4: input.readInt(); break;
				case 5: case 6: input.readLong(); index++; break;
				case 7: case 8: case 16: case 19: case 20:
					values[index] = input.readUnsignedShort(); break;
				case 9: case 10: case 11: case 12: case 17: case 18:
					values[index] = new int[] { input.readUnsignedShort(), input.readUnsignedShort() }; break;
				case 15:
					values[index] = new int[] { input.readUnsignedByte(), input.readUnsignedShort() }; break;
				default: throw new IOException("Unknown constant-pool tag " + tag);
				}
			}
			List<MethodReference> references = new ArrayList<>();
			for (int index = 1; index < count; index++) {
				if (tags[index] != 10 && tags[index] != 11) continue;
				int[] reference = (int[]) values[index];
				int classNameIndex = (Integer) values[reference[0]];
				int[] nameAndType = (int[]) values[reference[1]];
				references.add(new MethodReference((String) values[classNameIndex],
						(String) values[nameAndType[0]]));
			}
			return references;
		}
	}

	private static final class MethodReference {
		private final String owner;
		private final String name;

		private MethodReference(String owner, String name) {
			this.owner = owner;
			this.name = name;
		}
	}

	private static final class ProcessResult {
		private final int exitCode;
		private final String output;

		private ProcessResult(int exitCode, String output) {
			this.exitCode = exitCode;
			this.output = output;
		}
	}
}
