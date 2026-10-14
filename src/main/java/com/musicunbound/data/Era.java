package com.musicunbound.data;

import java.util.Map;

/**
 * A Minecraft version era from tracks.json. {@code order} is the index in the eras array (oldest first).
 * {@code timing} maps a context key (game, creative, nether, end, underwater) to {min, max} delays in ticks.
 */
public record Era(String id, String name, boolean menuMusic, Map<String, int[]> timing, int order) {
}
