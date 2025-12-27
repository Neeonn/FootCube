package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.touch.CubeTouchInfo;
import io.github.divinerealms.footcube.physics.touch.CubeTouchType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.CLEANUP_LAST_TOUCHES_INTERVAL;

/**
 * Maintenance task to be run at a lower frequency (e.g., every 5-10 seconds).
 * <p>
 * Cleans up expired touch data and other non-critical cache structures to
 * keep memory usage lean without interrupting the primary physics calculations.
 * </p>
 */
public class TouchCleanupTask extends BaseTask {
  private final PhysicsData data;

  private final List<UUID> playersToRemove = new ArrayList<>();
  private final List<UUID> cubesToRemove = new ArrayList<>();
  private final List<CubeTouchType> touchesToRemove = new ArrayList<>();

  public TouchCleanupTask(FCManager fcManager) {
    super(fcManager, "TouchCleanup", CLEANUP_LAST_TOUCHES_INTERVAL, true);
    this.data = fcManager.getPhysicsData();
  }

  @Override
  protected void kaboom() {
    long now = System.currentTimeMillis();
    cleanupLastTouches(now);
    cleanupRaised(now);
  }

  private void cleanupLastTouches(long now) {
    Map<UUID, Map<CubeTouchType, CubeTouchInfo>> lastTouches = data.getLastTouches();
    if (lastTouches.isEmpty()) return;

    playersToRemove.clear();

    for (Map.Entry<UUID, Map<CubeTouchType, CubeTouchInfo>> playerEntry : lastTouches.entrySet()) {
      UUID playerId = playerEntry.getKey();
      Map<CubeTouchType, CubeTouchInfo> playerTouches = playerEntry.getValue();
      if (playerTouches == null || playerTouches.isEmpty()) { playersToRemove.add(playerId); continue; }

      touchesToRemove.clear();

      for (Map.Entry<CubeTouchType, CubeTouchInfo> touchEntry : playerTouches.entrySet()) {
        CubeTouchType type = touchEntry.getKey();
        CubeTouchInfo info = touchEntry.getValue();

        try {
          long timestamp = info.getTimestamp();
          long cooldown = type.getCooldown();

          if ((now - timestamp) > cooldown) touchesToRemove.add(type);
        } catch (Exception exception) { touchesToRemove.add(type); }
      }

      for (CubeTouchType type : touchesToRemove) playerTouches.remove(type);
      if (playerTouches.isEmpty()) playersToRemove.add(playerId);
    }

    for (UUID playerId : playersToRemove) lastTouches.remove(playerId);
  }

  private void cleanupRaised(long now) {
    Map<UUID, Long> raised = data.getRaised();
    if (raised.isEmpty()) return;
    cubesToRemove.clear();
    for (Map.Entry<UUID, Long> entry : raised.entrySet()) if ((now - entry.getValue()) > 1000L) cubesToRemove.add(entry.getKey());
    for (UUID cubeId : cubesToRemove) raised.remove(cubeId);
  }
}
