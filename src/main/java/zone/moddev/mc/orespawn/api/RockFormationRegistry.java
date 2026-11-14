package zone.moddev.mc.orespawn.api;

import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;

/** Register compilers during mod initialization, before provider discovery freezes. */
public final class RockFormationRegistry {
    private static final Map<ResourceLocation, Function<JsonObject, CompiledRockFormation>> TYPES = new LinkedHashMap<>();
    private static boolean frozen;
    private RockFormationRegistry() { }
    public static synchronized void register(ResourceLocation type, Function<JsonObject, CompiledRockFormation> compiler) {
        if (frozen || TYPES.containsKey(type)) throw new IllegalStateException("Frozen or duplicate rock formation type: " + type);
        TYPES.put(java.util.Objects.requireNonNull(type), java.util.Objects.requireNonNull(compiler));
    }
    public static synchronized CompiledRockFormation compile(ResourceLocation type, JsonObject definition) {
        frozen = true;
        Function<JsonObject, CompiledRockFormation> compiler = TYPES.get(type);
        if (compiler == null) throw new IllegalArgumentException("Unknown rock formation type: " + type);
        return compiler.apply(definition);
    }
    public static synchronized boolean contains(ResourceLocation type) { return TYPES.containsKey(type); }
}
