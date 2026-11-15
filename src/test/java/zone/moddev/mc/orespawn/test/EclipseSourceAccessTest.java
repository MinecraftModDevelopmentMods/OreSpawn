package zone.moddev.mc.orespawn.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EclipseSourceAccessTest {
    @Test
    void sourcesDoNotDependDirectlyOnRestrictedJreClasses() throws Exception {
        // javac accepts these references, but Eclipse's Java 8 execution
        // environment rejects them. Do not hide that difference in IDE settings.
        Pattern restricted = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?sun\\."
                + "|\\bsun\\.(?:misc|reflect)\\.[\\w$]+\\s*\\.class\\b");
        List<String> offending = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Paths.get("src"))) {
            for (Path path : (Iterable<Path>) sources.filter(file -> file.toString().endsWith(".java"))::iterator) {
                String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                if (restricted.matcher(text).find()) offending.add(path.toString());
            }
        }
        assertEquals(java.util.Collections.emptyList(), offending,
                "Use the test-only allocator rather than importing restricted JRE classes");
    }
}
