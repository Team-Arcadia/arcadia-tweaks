package com.teamarcadia.arcadiatweaks.neoforge.sparework;

/**
 * How many times each spare work shortcut was taken. Only read by the GameTests, to prove a shortcut really
 * runs. Plain counters: a lost increment on another thread changes nothing.
 *
 * @author vyrriox
 */
public final class SpareWorkCounters {

    public static long fastSuiteLookupsSkipped;
    public static long structureLookupsOnLoadedChunk;
    public static long tackleBoxSearchesSkipped;

    private SpareWorkCounters() {}
}
