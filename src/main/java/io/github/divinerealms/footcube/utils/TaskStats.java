package io.github.divinerealms.footcube.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TaskStats {
  private final double cubeProcessAvgMs, touchCleanupAvgMs, playerUpdateAvgMs,
      particleTrailAvgMs, cubeCleanerAvgMs, matchmakingAvgMs;

  public double getTotalAverageMs() {
    return cubeProcessAvgMs + touchCleanupAvgMs + playerUpdateAvgMs
        + particleTrailAvgMs + cubeCleanerAvgMs + matchmakingAvgMs;
  }

  public double getAveragePerTask() {
    return getTotalAverageMs() / 6.0;
  }

  @Override
  public String toString() {
    return String.format(
        "Task Performance Statistics:\n" +
            "  Physics Tasks:\n" +
            "    CubeProcess: %.2fms\n" +
            "    TouchCleanup: %.2fms\n" +
            "    PlayerUpdate: %.2fms\n" +
            "    ParticleTrail: %.2fms\n" +
            "  General Tasks:\n" +
            "    CubeCleaner: %.2fms\n" +
            "    Matchmaking: %.2fms\n" +
            "  Total: %.2fms (avg per task: %.2fms)",
        cubeProcessAvgMs, touchCleanupAvgMs, playerUpdateAvgMs,
        particleTrailAvgMs, cubeCleanerAvgMs, matchmakingAvgMs,
        getTotalAverageMs(), getAveragePerTask()
    );
  }
}
