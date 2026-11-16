package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge31TestBootstrap;
import zone.moddev.mc.orespawn.test.TestObjects;

class NativeBakeDiagnosticsTest {
    @Test void onlyAnAuthoritativeWorldTagBakeWarnsAboutAnUnresolvedRule() throws Exception {
        Forge31TestBootstrap.registerVanilla();
        Logger logger = (Logger) LogManager.getLogger(OreSpawnOreGeneration.class);
        Level oldLevel = logger.getLevel(); boolean oldAdditive = logger.isAdditive();
        List<Level> levels = new ArrayList<>();
        AbstractAppender sink = new AbstractAppender("tag-readiness-test", null, null, false) {
            @Override public void append(LogEvent event) { levels.add(event.getLevel()); }
        };
        Field server = WorldGeologyProfileManager.class.getDeclaredField("activeServer"); server.setAccessible(true);
        Field pending = WorldGeologyProfileManager.class.getDeclaredField("tagRulesPending"); pending.setAccessible(true);
        Object previousServer = server.get(null), previousPending = pending.get(null);
        sink.start(); logger.addAppender(sink); logger.setLevel(Level.ALL); logger.setAdditive(false);
        try {
            Method report = OreSpawnOreGeneration.class.getDeclaredMethod("reportBakeProblem", String.class, Object[].class);
            report.setAccessible(true);
            server.set(null, TestObjects.allocate(net.minecraft.server.dedicated.DedicatedServer.class));
            pending.set(null, true); report.invoke(null, "Provisional tag lookup", new Object[0]);
            pending.set(null, false); report.invoke(null, "Definitively invalid lookup", new Object[0]);
            server.set(null, null); report.invoke(null, "Early mod-loading lookup", new Object[0]);
            assertEquals(java.util.Arrays.asList(Level.DEBUG, Level.WARN, Level.DEBUG), levels);
        } finally {
            server.set(null, previousServer); pending.set(null, previousPending);
            logger.removeAppender(sink); sink.stop(); logger.setLevel(oldLevel); logger.setAdditive(oldAdditive);
        }
    }
}
