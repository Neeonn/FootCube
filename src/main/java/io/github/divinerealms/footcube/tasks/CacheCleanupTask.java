package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import org.bukkit.entity.Player;

public class CacheCleanupTask extends BaseTask {

  private static final long UPDATE_INTERVAL = 20 * 60 * 5; // 5 minutes

  public CacheCleanupTask(FCManager fcManager) {
    super(fcManager, "CacheCleanup", UPDATE_INTERVAL, true);
  }

  @Override
  protected void kaboom() {
    int removed = 0;
    for (Player player : fcManager.getCachedPlayers()) {
      if (player != null && player.isOnline()) {
        continue;
      }
      fcManager.getCachedPlayers().remove(player);
      removed++;
    }

    if (removed > 0) {
      logger.info("&2Cleaned up &e" + removed + " &2stale player references.");
    }
  }
}
