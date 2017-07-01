package com.mcmoddev.orespawn.api.os3;

import java.util.Optional;

public interface DimensionBuilder {
  SpawnBuilder SpawnBuilder( Optional<String> name );
  DimensionData create( SpawnData... spawns);
}
