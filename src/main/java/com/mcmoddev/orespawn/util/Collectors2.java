package com.mcmoddev.orespawn.util;

import java.util.stream.Collector;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public final class Collectors2 {

    private Collectors2() {
    }

    public static <T> Collector<T, ImmutableList.Builder<T>, ImmutableList<T>> toImmutableList() {
        return Collector.of(ImmutableList.Builder::new, ImmutableList.Builder::add,
                (left, right) -> left.addAll(right.build()), ImmutableList.Builder::build);
    }

    public static <T> Collector<T, ImmutableSet.Builder<T>, ImmutableSet<T>> toImmutableSet() {
        return Collector.of(ImmutableSet.Builder::new, ImmutableSet.Builder::add,
                (left, right) -> left.addAll(right.build()), ImmutableSet.Builder::build);
    }
}
