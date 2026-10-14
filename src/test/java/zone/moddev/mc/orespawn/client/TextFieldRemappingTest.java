package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import net.minecraft.client.gui.FontRenderer;

/** Exercises the name collision introduced when another mod uses official mappings. */
public class TextFieldRemappingTest {
	private static final String WRAPPER = TextFieldWidget.class.getName().replace('.', '/');
	private static final String NATIVE = "net/minecraft/client/gui/widget/TextFieldWidget";
	private static final String REMAPPED_NATIVE = OfficialTextField.class.getName().replace('.', '/');

	@ParameterizedTest
	@ValueSource(strings = { "setValue", "getValue", "setMaxLength" })
	void eachOfficialNameStillDelegatesToTheNativeWidget(String method) throws Exception {
		Object field = remappedField();
		if (method.equals("setValue")) {
			invoke(field.getClass(), field, method, String.class, "minecraft:stone");
			assertEquals("minecraft:stone", ((OfficialTextField) field).value);
		} else if (method.equals("setMaxLength")) {
			invoke(field.getClass(), field, method, int.class, 1024);
			assertEquals(1024, ((OfficialTextField) field).maximum);
		} else {
			assertEquals("", readValue(field.getClass(), field));
		}
	}

	@Test
	void remappedWidgetPreservesLongValuesAndNativeLimits() throws Exception {
		Object field = remappedField();
		Class<?> remapped = field.getClass();
		String value = "minecraft:stone,minecraft:granite,minecraft:diorite,minecraft:andesite";

		invoke(remapped, field, "setMaxLength", int.class, 1024);
		invoke(remapped, field, "setValue", String.class, value);
		assertEquals(value, readValue(remapped, field));
		invoke(remapped, field, "setMaxLength", int.class, 12);
		assertEquals(value.substring(0, 12), readValue(remapped, field));
		invoke(remapped, field, "setValue", String.class, "minecraft:granite");
		assertEquals("minecraft:gr", readValue(remapped, field));
	}

	private Object remappedField() throws Exception {
		// Use the real compiled wrapper, remapping the three native MCP names as
		// ForgeGradle does when importing an SRG jar into an official-mapped project.
		ClassWriter writer = new ClassWriter(0);
		try (InputStream input = TextFieldWidget.class.getResourceAsStream("TextFieldWidget.class")) {
			new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM6, writer) {
				@Override
				public void visit(int version, int access, String name, String signature,
						String superName, String[] interfaces) {
					super.visit(version, access, name, signature, REMAPPED_NATIVE, interfaces);
				}

				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor,
						String signature, String[] exceptions) {
					return new MethodVisitor(Opcodes.ASM6,
							super.visitMethod(access, name, descriptor, signature, exceptions)) {
						@Override
						public void visitMethodInsn(int opcode, String owner, String name,
								String descriptor, boolean isInterface) {
							if (owner.equals(WRAPPER) || owner.equals(NATIVE)) {
								if (name.equals("setText")) name = "setValue";
								if (name.equals("getText")) name = "getValue";
								if (name.equals("setMaxStringLength")) name = "setMaxLength";
							}
							super.visitMethodInsn(opcode, owner.equals(NATIVE) ? REMAPPED_NATIVE : owner,
								name, descriptor, isInterface);
						}
					};
				}
			}, 0);
		}
		Class<?> remapped = new ClassLoader(getClass().getClassLoader()) {
			Class<?> defineWrapper() {
				byte[] bytes = writer.toByteArray();
				return defineClass(TextFieldWidget.class.getName(), bytes, 0, bytes.length);
			}
		}.defineWrapper();
		Constructor<?> constructor = remapped.getDeclaredConstructor(FontRenderer.class,
				int.class, int.class, int.class, int.class, String.class);
		constructor.setAccessible(true);
		return constructor.newInstance(null, 0, 0, 200, 20, "host_blocks");
	}

	private static void invoke(Class<?> type, Object field, String name, Class<?> parameter,
			Object value) throws Exception {
		Method method = type.getDeclaredMethod(name, parameter);
		method.setAccessible(true);
		method.invoke(field, value);
	}

	private static Object readValue(Class<?> type, Object field) throws Exception {
		Method method = type.getDeclaredMethod("getValue");
		method.setAccessible(true);
		return method.invoke(field);
	}

	/** Small native-widget stand-in: only naming and virtual dispatch matter here. */
	public static class OfficialTextField {
		public int x, y, width, height;
		private int maximum = 32;
		private String value = "";

		public OfficialTextField(FontRenderer font, int x, int y, int width, int height, String label) { }
		public void setValue(String value) {
			this.value = value.substring(0, Math.min(maximum, value.length()));
		}
		public String getValue() { return value; }
		public void setMaxLength(int maximum) {
			this.maximum = maximum;
			value = value.substring(0, Math.min(maximum, value.length()));
		}
	}
}
