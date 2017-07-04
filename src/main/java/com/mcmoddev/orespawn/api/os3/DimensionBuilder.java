package com.mcmoddev.orespawn.api.os3;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

public interface DimensionBuilder {
  SpawnBuilder SpawnBuilder( @Nullable String name );
  DimensionBuilder create( @Nonnull SpawnBuilder... spawns);
  
  ImmutableList<SpawnBuilder> getSpawnByName( @Nonnull String name );
  ImmutableList<SpawnBuilder> getAllSpawns();
}
