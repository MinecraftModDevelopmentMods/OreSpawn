package zone.moddev.mc.orespawn.test;

import java.lang.reflect.Field;

/** Creates empty fixture objects without starting the Minecraft server their constructors need. */
public final class TestObjects {
    private TestObjects() {
    }

    public static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        // Keep the internal Java 8 API behind reflection so Eclipse can compile
        // these tests with its normal JRE access restrictions still enabled.
        Class<?> allocator = Class.forName("sun.misc.Unsafe");
        Field singleton = allocator.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object instance = allocator.getMethod("allocateInstance", Class.class)
                .invoke(singleton.get(null), type);
        return type.cast(instance);
    }
}
