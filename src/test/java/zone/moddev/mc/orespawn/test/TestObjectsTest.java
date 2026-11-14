package zone.moddev.mc.orespawn.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

class TestObjectsTest {
    @Test
    void allocationDoesNotRunTheConstructorOrFieldInitializers() throws Exception {
        Fixture value = TestObjects.allocate(Fixture.class);
        assertEquals(0, value.number);
    }

    @Test
    void eachAllocationCreatesAnIndependentObject() throws Exception {
        Fixture first = TestObjects.allocate(Fixture.class);
        Fixture second = TestObjects.allocate(Fixture.class);
        first.number = 12;
        assertNotSame(first, second);
        assertEquals(12, first.number);
        assertEquals(0, second.number);
    }

    private static final class Fixture {
        int number = 42;

        private Fixture() {
            throw new AssertionError("A test shell must not run this constructor");
        }
    }
}
