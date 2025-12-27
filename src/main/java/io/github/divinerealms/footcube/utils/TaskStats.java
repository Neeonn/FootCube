package io.github.divinerealms.footcube.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Data class to hold average execution times for various tasks.
 */
@Getter
@AllArgsConstructor
public class TaskStats {
  private final double physicsAvgMs, touchCleanupAvgMs, playerUpdateAvgMs, particleTrailAvgMs,
      cubeCleanerAvgMs, matchmakingAvgMs, cacheCleanupAvgMs, queueStatusAvgMs, highScoresAvgMs;

  public double getTotalAverageMs() {
    return physicsAvgMs + touchCleanupAvgMs + playerUpdateAvgMs
        + particleTrailAvgMs + cubeCleanerAvgMs + matchmakingAvgMs
        + cacheCleanupAvgMs + queueStatusAvgMs + highScoresAvgMs;
  }

  public double getAveragePerTask() {
    return getTotalAverageMs() / 9;
  }
}
