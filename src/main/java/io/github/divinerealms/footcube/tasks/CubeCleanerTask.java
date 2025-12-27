package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.utils.CubeCleaner;

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
  public void start() {
    if (!cubeCleaner.practiceAreasSet()) {
      logger.info("&eCubeCleaner task not started - no practice areas configured!");
      return;
    }

    super.start();
  }

  @Override
  protected void kaboom() {
    cubeCleaner.clearCubes();
    if (!cubeCleaner.isEmpty()) {
      logger.broadcast(Lang.CLEARED_CUBES.replace(
          new String[]{ String.valueOf(cubeCleaner.getAmount()) }
      ));
    }
  }
}
