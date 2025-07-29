package com.mcmoddev.orespawn.misc;

import net.minecraft.util.RandomSource;

public class M {

    public static double triangularDistribution(double a, double b, double c, RandomSource random) {
        double base = (c - a) / (b - a);
        double rand = random.nextDouble();

        if (rand < base) {
            return a + Math.sqrt(rand * (b - a) * (c - a));
        } else {
            return b - Math.sqrt((1 - rand) * (b - a) * (b - c));
        }
    }
    public static int getPoint(int lowerBound, int upperBound, int median, RandomSource random) {
        int t = (int)Math.round(triangularDistribution((float)lowerBound, (float)upperBound, (float)median, random));
        return t - median;
    }


}
