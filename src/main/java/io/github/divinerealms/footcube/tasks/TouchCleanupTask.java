package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.physics.PhysicsData;

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

  public TouchCleanupTask(FCManager fcManager) {
    super(fcManager, "TouchCleanup", CLEANUP_LAST_TOUCHES_INTERVAL, true);
    this.data = fcManager.getPhysicsData();
  }

  @Override
  protected void execute() {
    long now = System.currentTimeMillis();

    if (!data.getLastTouches().isEmpty()) {
      data.getLastTouches().values().removeIf(playerTouches -> {
        playerTouches.entrySet().removeIf(entry ->
            (now - entry.getValue().getTimestamp()) > entry.getKey().getCooldown());
        return playerTouches.isEmpty();
      });
    }

    if (!data.getRaised().isEmpty()) {
      data.getRaised().entrySet().removeIf(entry ->
          (now - entry.getValue()) > 1000L);
    }
  }
}
