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
  private final PhysicsTask physicsTask;
  private final TouchCleanupTask touchCleanupTask;
  private final PlayerUpdateTask playerUpdateTask;
  private final ParticleTrailTask particleTrailTask;

  // General Tasks
  private final CubeCleanerTask cubeCleanerTask;
  private final MatchmakingTask matchmakingTask;
  private final CacheCleanupTask cacheCleanupTask;
  private final QueueStatusTask queueStatusTask;
  private final HighScoresTask highScoresTask;

  public TaskManager(FCManager fcManager) {
    this.logger = fcManager.getLogger();
    this.tasks = new ArrayList<>();

    // Initialize physics tasks.
    this.physicsTask = new PhysicsTask(fcManager);
    this.touchCleanupTask = new TouchCleanupTask(fcManager);
    this.playerUpdateTask = new PlayerUpdateTask(fcManager);
    this.particleTrailTask = new ParticleTrailTask(fcManager);

    // Initialize general tasks.
    this.cubeCleanerTask = new CubeCleanerTask(fcManager, fcManager.getCubeCleaner().getRemoveInterval());
    this.matchmakingTask = new MatchmakingTask(fcManager);
    this.cacheCleanupTask = new CacheCleanupTask(fcManager);
    this.queueStatusTask = new QueueStatusTask(fcManager);
    this.highScoresTask = new HighScoresTask(fcManager);

    tasks.add(physicsTask);
    tasks.add(touchCleanupTask);
    tasks.add(playerUpdateTask);
    tasks.add(particleTrailTask);
    tasks.add(cubeCleanerTask);
    tasks.add(matchmakingTask);
    tasks.add(cacheCleanupTask);
    tasks.add(queueStatusTask);
    tasks.add(highScoresTask);
  }

  public void startAll() {
    int started = 0;
    for (BaseTask task : tasks) {
      try {
        task.start();
        started++;
      } catch (Exception exception) {
        logger.info("&c✘ &4Failed to start " + task.getTaskName() + " task: " + exception.getMessage());
      }
    }
    logger.info("&a✔ &2Started &e" + started + "/" + tasks.size() + " &2plugin tasks successfully!");
  }

  public void stopAll() {
    int stopped = 0;
    for (int i = tasks.size() - 1; i >= 0; i--) {
      BaseTask task = tasks.get(i);
      if (task.isRunning()) {
        try {
          task.stop();
          stopped++;
        } catch (Exception exception) {
          logger.info("&c✘ &4Error stopping " + task.getTaskName() + " task: " + exception.getMessage());
        }
      }
    }
    if (stopped > 0) logger.info("&c✘ &4Stopped &c" + stopped + " &4plugin tasks.");
  }

  public void restart() {
    stopAll();
    startAll();
  }

  public TaskStats getStats() {
    return new TaskStats(
        physicsTask.getAverageExecutionTime(),
        touchCleanupTask.getAverageExecutionTime(),
        playerUpdateTask.getAverageExecutionTime(),
        particleTrailTask.getAverageExecutionTime(),
        cubeCleanerTask.getAverageExecutionTime(),
        matchmakingTask.getAverageExecutionTime(),
        cacheCleanupTask.getAverageExecutionTime(),
        queueStatusTask.getAverageExecutionTime(),
        highScoresTask.getAverageExecutionTime()
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

  public BaseTask getTask(String taskName) {
    for (BaseTask task : tasks) if (task.getTaskName().equalsIgnoreCase(taskName)) return task;
    return null;
  }
}
