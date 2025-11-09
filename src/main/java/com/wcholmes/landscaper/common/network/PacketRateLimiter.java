package com.wcholmes.landscaper.common.network;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Rate limiter for network packets to prevent client-side packet flooding.
 *
 * <p>Based on Create mod's packet validation patterns.
 * Tracks packets per player per packet type in a sliding time window.
 *
 * @since 2.3.0
 */
public class PacketRateLimiter {
    private static final Map<String, Deque<Long>> packetTimes = new ConcurrentHashMap<>();

    /**
     * Checks if a packet should be allowed based on rate limits.
     *
     * @param playerId UUID of the player sending the packet
     * @param packetType identifier for the packet type (e.g., "cycle_mode")
     * @param maxCount maximum number of packets allowed in the time window
     * @param windowMs time window in milliseconds
     * @return true if packet is allowed, false if rate limit exceeded
     */
    public static boolean allowPacket(UUID playerId, String packetType, int maxCount, long windowMs) {
        String key = playerId + ":" + packetType;
        Deque<Long> times = packetTimes.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        long now = System.currentTimeMillis();

        // Remove entries outside time window
        times.removeIf(time -> (now - time) > windowMs);

        // Check if limit exceeded
        if (times.size() >= maxCount) {
            return false;
        }

        // Add current packet time
        times.add(now);
        return true;
    }

    /**
     * Clears rate limit data for a specific player (e.g., on disconnect).
     *
     * @param playerId UUID of the player
     */
    public static void clearPlayer(UUID playerId) {
        packetTimes.keySet().removeIf(key -> key.startsWith(playerId.toString()));
    }

    /**
     * Clears all rate limit data (e.g., on server shutdown).
     */
    public static void clearAll() {
        packetTimes.clear();
    }
}
