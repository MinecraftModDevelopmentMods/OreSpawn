package zone.moddev.mc.orespawn.api;

/** Baked thread-safe formation evaluator. Null means the column misses this body. */
@FunctionalInterface
public interface CompiledRockFormation {
    RockFormationColumn prepare(RockFormationContext context);
}
