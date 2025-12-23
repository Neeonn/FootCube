package io.github.divinerealms.footcube.managers;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.tasks.*;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.TaskStats;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized manager for all scheduled tasks.
 * Handles initialization, lifecycle, and cleanup of physics tasks.
 */
@Getter
public class TaskManager {
  private final Logger logger;
  private final List<BaseTask> tasks;

  // Physics Tasks
  private final CubeProcessTask cubeProcessTask;
  private final TouchCleanupTask touchCleanupTask;
  private final PlayerUpdateTask playerUpdateTask;
  private final ParticleTrailTask particleTrailTask;

  // General Tasks
  private final CubeCleanerTask cubeCleanerTask;
  private final MatchmakingTask matchmakingTask;

  public TaskManager(FCManager fcManager) {
    this.logger = fcManager.getLogger();

    // Initialize physics tasks.
    this.cubeProcessTask = new CubeProcessTask(fcManager);
    this.touchCleanupTask = new TouchCleanupTask(fcManager);
    this.playerUpdateTask = new PlayerUpdateTask(fcManager);
    this.particleTrailTask = new ParticleTrailTask(fcManager);

    // Initialize general tasks.
    this.cubeCleanerTask = new CubeCleanerTask(fcManager, fcManager.getCubeCleaner().getRemoveInterval());
    this.matchmakingTask = new MatchmakingTask(fcManager);

    this.tasks = new ArrayList<>();
    tasks.add(cubeProcessTask);
    tasks.add(touchCleanupTask);
    tasks.add(playerUpdateTask);
    tasks.add(particleTrailTask);
    tasks.add(cubeCleanerTask);
    tasks.add(matchmakingTask);
  }

  public void startAll() {
    for (BaseTask task : tasks) task.start();
    logger.info("&a✔ &2Started all plugin tasks.");
  }

  public void stopAll() {
    for (BaseTask task : tasks) task.stop();
    logger.info("&c✘ &4Stopped all plugin tasks.");
  }

  public void restart() {
    stopAll();
    startAll();
  }

  public TaskStats getStats() {
    return new TaskStats(
        cubeProcessTask.getAverageExecutionTime(),
        touchCleanupTask.getAverageExecutionTime(),
        playerUpdateTask.getAverageExecutionTime(),
        particleTrailTask.getAverageExecutionTime(),
        cubeCleanerTask.getAverageExecutionTime(),
        matchmakingTask.getAverageExecutionTime()
    );
  }

  public void resetAllStats() {
    for (BaseTask task : tasks) task.resetStats();
    logger.info("&a✔ &2Reset statistics for all tasks.");
  }

  public int getTaskCount() {
    return tasks.size();
  }

  public int getRunningTaskCount() {
    int count = 0;
    for (BaseTask task : tasks) if (task.isRunning()) count++;
    return count;
  }
}
