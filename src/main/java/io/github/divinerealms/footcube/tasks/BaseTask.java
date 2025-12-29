package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.utils.Logger;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;

import static io.github.divinerealms.footcube.utils.Permissions.PERM_ADMIN;

public abstract class BaseTask implements Runnable {
  protected final FCManager fcManager;
  protected final Plugin plugin;
  protected final Logger logger;
  @Getter
  private final String taskName;
  private final long interval;
  private final boolean async;
  private final Queue<Long> recentExecutionTimes = new ArrayDeque<>(20);
  private final long debugThreshold;
  private BukkitTask task;
  @Getter
  private boolean running = false;
  @Getter
  private long totalExecutions = 0;
  private long totalExecutionTime = 0;

  protected BaseTask(FCManager fcManager, String taskName, long interval, boolean async) {
    this(fcManager, taskName, interval, async, getDefaultThreshold(interval));
  }

  protected BaseTask(FCManager fcManager, String taskName, long interval, boolean async, long customThreshold) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.logger = fcManager.getLogger();
    this.taskName = taskName;
    this.interval = interval;
    this.async = async;
    this.debugThreshold = customThreshold;
  }

  private static long getDefaultThreshold(long interval) {
    if (interval <= 2) {
      return 5;
    }
    if (interval <= 40) {
      return 20;
    }
    return 50;
  }

  public void start() {
    if (running) {
      logger.info("&e! &6" + taskName + " task is already running");
      return;
    }
    if (async) {
      task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this, interval, interval);
    } else {
      task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, interval, interval);
    }
    running = true;
    logger.info("&a✔ &2Started &d" + taskName + "&2 task &7&o(type: " + (async
                                                                         ? "&ba"
                                                                         : "&a") + "sync&7&o, frequency: " + interval +
                " ticks)");
  }

  public void stop() {
    if (!running) {
      return;
    }
    if (task != null) {
      task.cancel();
      task = null;
    }
    running = false;
  }

  @Override
  public final void run() {
    long start = System.nanoTime();
    try {
      kaboom();
    } catch (Exception exception) {
      Bukkit.getLogger().log(Level.SEVERE, "Error in " + taskName + " task: " + exception.getMessage(), exception);
    } finally {
      long durationNanos = System.nanoTime() - start;
      recordExecution(durationNanos);

      long durationMillis = durationNanos / 1_000_000;
      if (durationMillis > debugThreshold) {
        logger.send(PERM_ADMIN,
            "{prefix-admin}&d" + taskName + " &ftook &e" + durationMillis + "ms &f(threshold: " + debugThreshold +
            "ms)");
      }
    }
  }

  protected abstract void kaboom();

  private void recordExecution(long duration) {
    totalExecutions++;
    totalExecutionTime += duration;

    recentExecutionTimes.offer(duration);
    if (recentExecutionTimes.size() > 20) {
      long removed = recentExecutionTimes.poll();
      totalExecutionTime -= removed;
    }
  }

  public double getAverageExecutionTime() {
    if (recentExecutionTimes.isEmpty()) {
      return 0.0;
    }
    return totalExecutionTime / (double) recentExecutionTimes.size() / 1_000_000.0;
  }

  public void resetStats() {
    totalExecutions = 0;
    recentExecutionTimes.clear();
    totalExecutionTime = 0;
  }
}
