package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.utils.CubeCleaner;

import static io.github.divinerealms.footcube.configs.Lang.CLEARED_CUBES;

/**
 * Task that periodically cleans up cubes in practice areas.
 * Runs at an interval configured by the CubeCleaner.
 */
public class CubeCleanerTask extends BaseTask {
  private final CubeCleaner cubeCleaner;

  public CubeCleanerTask(FCManager fcManager, long interval) {
    super(fcManager, "CubeCleaner", interval, false);
    this.cubeCleaner = fcManager.getCubeCleaner();
  }

  @Override
  protected void kaboom() {
    cubeCleaner.clearCubes();
    if (!cubeCleaner.isEmpty()) {
      logger.broadcast(CLEARED_CUBES,
          String.valueOf(cubeCleaner.getAmount())
      );
    }
  }

  @Override
  public void start() {
    if (cubeCleaner.noPracticeAreasSet()) {
      logger.info("&e! &dCubeCleaner &etask not started - no practice areas configured!");
      return;
    }

    super.start();
  }
}
