package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.MatchManager;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.MATCH_TASK_INTERVAL_TICKS;

/**
 * Task that handles match updates and matchmaking logic.
 * Runs synchronously at configured interval (default: 1 tick).
 */
public class MatchmakingTask extends BaseTask {
  private final MatchManager matchManager;

  public MatchmakingTask(FCManager fcManager) {
    super(fcManager, "Matchmaking", MATCH_TASK_INTERVAL_TICKS, false);
    this.matchManager = fcManager.getMatchManager();
  }

  @Override
  protected void kaboom() {
    matchManager.update();
  }
}
