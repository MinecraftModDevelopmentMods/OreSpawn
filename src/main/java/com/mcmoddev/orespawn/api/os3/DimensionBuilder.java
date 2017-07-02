package com.mcmoddev.orespawn.api.os3;

import java.util.Optional;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

public interface DimensionBuilder {
  SpawnBuilder SpawnBuilder( Optional<String> name );
  DimensionBuilder create( SpawnBuilder... spawns);
  
  SpawnBuilder getSpawnByName( @Nonnull String name );
  ImmutableList<SpawnBuilder> getAllSpawns();
}
